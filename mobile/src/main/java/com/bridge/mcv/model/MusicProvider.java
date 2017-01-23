/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bridge.mcv.model;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.bridge.mcv.R;
import com.bridge.mcv.MusicService;
//import com.android.mcv.R;
import com.bridge.mcv.ui.FullScreenPlayerActivity;
import com.bridge.mcv.utils.LogHelper;
import com.bridge.mcv.utils.MediaIDHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.bridge.mcv.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static com.bridge.mcv.utils.MediaIDHelper.MEDIA_ID_OFFLINE;
import static com.bridge.mcv.utils.MediaIDHelper.MEDIA_ID_ROOT;
import static com.bridge.mcv.utils.MediaIDHelper.createMediaID;

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
public class MusicProvider extends ArrayList<MediaMetadataCompat>
{

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    private MusicProviderSource mSource;

    // Categorized caches for music track data:
    private ConcurrentMap<String, List<MediaMetadataCompat>> mMusicListByGenre;
    private final ConcurrentMap<String, MutableMediaMetadata> mMusicListById;

    private final Set<String> mFavoriteTracks;
	private static final String JSON_TITLE = "title";
	private static final String JSON_ALBUM = "album";
	private static final String JSON_ARTIST = "artist";
	private static final String JSON_GENRE = "genre";
	private static final String JSON_SOURCE = "source";
	private static final String JSON_IMAGE = "image";
	private static final String JSON_TRACK_NUMBER = "trackNumber";
	private static final String JSON_TOTAL_TRACK_COUNT = "totalTrackCount";
	private static final String JSON_DURATION = "duration";
	public  static ArrayList<MediaMetadataCompat> tracks;

	enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    private volatile State mCurrentState = State.NON_INITIALIZED;

    public interface Callback {
        //void onMusicCatalogReady(boolean success);
		void children(List<MediaBrowserCompat.MediaItem> mediaItems);
    }

    public MusicProvider() {
        this(new RemoteJSONSource());
    }
    public MusicProvider(MusicProviderSource source) {
        mSource = source;
        mMusicListByGenre = new ConcurrentHashMap<>();
        mMusicListById = new ConcurrentHashMap<>();
        mFavoriteTracks = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    /**
     * Get an iterator over the list of genres
     *
     * @return genres
     */
    public Iterable<String> getGenres() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        return mMusicListByGenre.keySet();
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    public Iterable<MediaMetadataCompat> getShuffledMusic() {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        List<MediaMetadataCompat> shuffled = new ArrayList<>(mMusicListById.size());
        for (MutableMediaMetadata mutableMetadata: mMusicListById.values()) {
            shuffled.add(mutableMetadata.metadata);
        }
        Collections.shuffle(shuffled);
        return shuffled;
    }

    /**
     * Get music tracks of the given genre
     *
     */
    public Iterable<MediaMetadataCompat> getMusicsByGenre(String genre) {
        if (mCurrentState != State.INITIALIZED || !mMusicListByGenre.containsKey(genre)) {
            return Collections.emptyList();
        }
        return mMusicListByGenre.get(genre);
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *
     */
    public Iterable<MediaMetadataCompat> searchMusicBySongTitle(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_TITLE, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.
     *
     */
    public Iterable<MediaMetadataCompat> searchMusicByAlbum(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ALBUM, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.
     *
     */
    public Iterable<MediaMetadataCompat> searchMusicByArtist(String query) {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ARTIST, query);
    }

    Iterable<MediaMetadataCompat> searchMusic(String metadataField, String query) {
        if (mCurrentState != State.INITIALIZED) {
            return Collections.emptyList();
        }
        ArrayList<MediaMetadataCompat> result = new ArrayList<>();
        query = query.toLowerCase(Locale.US);
        for (MutableMediaMetadata track : mMusicListById.values()) {
            if (track.metadata.getString(metadataField).toLowerCase(Locale.US)
                .contains(query)) {
                result.add(track.metadata);
            }
        }
        return result;
    }


    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    public MediaMetadataCompat getMusic(String musicId) {
        return mMusicListById.containsKey(musicId) ? mMusicListById.get(musicId).metadata : null;
    }

    public synchronized void updateMusicArt(String musicId, Bitmap albumArt, Bitmap icon) {
        MediaMetadataCompat metadata = getMusic(musicId);
        metadata = new MediaMetadataCompat.Builder(metadata)

                // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                // example, on the lockscreen background when the media session is active.
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                // set small version of the album art in the DISPLAY_ICON. This is used on
                // the MediaDescription and thus it should be small to be serialized if
                // necessary
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                .build();

        MutableMediaMetadata mutableMetadata = mMusicListById.get(musicId);
        if (mutableMetadata == null) {
            throw new IllegalStateException("Unexpected error: Inconsistent data structures in " +
                    "MusicProvider");
        }

        mutableMetadata.metadata = metadata;
    }

    public void setFavorite(String musicId, boolean favorite) {
        if (favorite) {
            mFavoriteTracks.add(musicId);
        } else {
            mFavoriteTracks.remove(musicId);
        }
    }

    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    public boolean isFavorite(String musicId) {
        return mFavoriteTracks.contains(musicId);
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    private synchronized void buildListsByGenre() {
        ConcurrentMap<String, List<MediaMetadataCompat>> newMusicListByGenre = new ConcurrentHashMap<>();

        for (MutableMediaMetadata m : mMusicListById.values()) {
            String genre = m.metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
            List<MediaMetadataCompat> list = newMusicListByGenre.get(genre);
            if (list == null) {
                list = new ArrayList<>();
                newMusicListByGenre.put(genre, list);
            }
            list.add(m.metadata);
        }
        mMusicListByGenre = newMusicListByGenre;
    }

    private synchronized void retrieveMedia() {
		mCurrentState = State.NON_INITIALIZED;
        try {
            if (mCurrentState == State.NON_INITIALIZED) {

                mCurrentState = State.INITIALIZING;

                Iterator<MediaMetadataCompat> tracks = mSource.iterator();
                while (tracks.hasNext()) {
                    MediaMetadataCompat item = tracks.next();
                    String musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID);
                    mMusicListById.put(musicId, new MutableMediaMetadata(musicId, item));
                }
                buildListsByGenre();
                mCurrentState = State.INITIALIZED;
            }
        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                mCurrentState = State.NON_INITIALIZED;
            }
        }
    }

    public List<MediaBrowserCompat.MediaItem> getChildren(final String mediaId, final Resources resources, final Callback  callback) {
        final List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        if (!MediaIDHelper.isBrowseable(mediaId)) {
            LogHelper.w(TAG, "bla: ", mediaId);
            return mediaItems;
        }

		if (MEDIA_ID_ROOT.equals(mediaId)) {
			new AsyncTask<Void, Void, State>() {
				@Override
				protected State doInBackground(Void... params) {
					mMusicListByGenre = new ConcurrentHashMap<>();
					retrieveMedia();

					for (String genre : getGenres()) {
						mediaItems.add(createBrowsableMediaItemForGenre(genre, resources));
					}
					return mCurrentState;
				}

				@Override
				protected void onPostExecute(State current) {
					if (callback != null) {
						callback.children(mediaItems);
					}
				}
			}.execute();
		}  else if (MEDIA_ID_MUSICS_BY_GENRE.equals(mediaId)) {
            for (String genre : getGenres()) {
                mediaItems.add(createBrowsableMediaItemForGenre(genre, resources));
            }

        } else if (mediaId.startsWith(MEDIA_ID_MUSICS_BY_GENRE)) {
			new MusicService();
			new AsyncTask<Void, Void, State>() {
				@Override
				protected State doInBackground(Void... params) {
					retrieveMedia();
					String genre = MediaIDHelper.getHierarchy(mediaId)[1];

					for (MediaMetadataCompat metadata : getMusicsByGenre(genre)) {
						mediaItems.add(createMediaItem(metadata));
					}
					return mCurrentState;
				}

				@Override
				protected void onPostExecute(State current) {
					if (callback != null) {
						LogHelper.w(TAG, "call back working: ", mediaId);
						callback.children(mediaItems);
					}
				}
			}.execute();

        }
		else if (MEDIA_ID_OFFLINE.equals(mediaId)) {
			LogHelper.w(TAG, "offfff: ", mediaId);
			String genre = MediaIDHelper.getHierarchy("__BY_GENRE__/Offline")[1];
			for (MediaMetadataCompat metadata : getMusicsByGenre(genre)) {
				mediaItems.add(createMediaItem(metadata));
			}
		}
		else {
            LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId);
        }
        return mediaItems;
    }

    private MediaBrowserCompat.MediaItem createBrowsableMediaItemForGenre(String genre,
                                                                    Resources resources) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(createMediaID(null, MEDIA_ID_MUSICS_BY_GENRE, genre))
                .setTitle(genre)
                .setSubtitle(resources.getString(
                        R.string.browse_musics_by_genre_subtitle, genre))
                .build();
        return new MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaMetadataCompat metadata) {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        String genre = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE);
        String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                metadata.getDescription().getMediaId(), MEDIA_ID_MUSICS_BY_GENRE, genre);
        MediaMetadataCompat copy = new MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build();
        return new MediaBrowserCompat.MediaItem(copy.getDescription(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);

    }

	public Iterator<MediaMetadataCompat> iterator() {
		tracks = new ArrayList<>();
		try {
			if (FullScreenPlayerActivity.offlinejsonTracks != null) {
				for (int j = 0; j < FullScreenPlayerActivity.offlinejsonTracks.length(); j++) {
					tracks.add(buildFromJSON(FullScreenPlayerActivity.offlinejsonTracks.getJSONObject(j)));
				}
			}
			return tracks.iterator();
		} catch (JSONException e) {
			LogHelper.e(TAG, e, "Could not retrieve music list");
			throw new RuntimeException("Could not retrieve music list", e);
		}
	}

	private MediaMetadataCompat buildFromJSON(JSONObject json) throws JSONException {
		String title = json.getString(JSON_TITLE);
		String album = json.getString(JSON_ALBUM);
		String artist = json.getString(JSON_ARTIST);
		String genre = json.getString(JSON_GENRE);
		String source = json.getString(JSON_SOURCE);
		String iconUrl = json.getString(JSON_IMAGE);
		int trackNumber = json.getInt(JSON_TRACK_NUMBER);
		int totalTrackCount = json.getInt(JSON_TOTAL_TRACK_COUNT);
		int duration = json.getInt(JSON_DURATION) * 1000; // ms

		LogHelper.d(TAG, "Found music track: ", json);


		// Since we don't have a unique ID in the server, we fake one using the hashcode of
		// the music source. In a real world app, this could come from the server.
		String id = String.valueOf(source.hashCode());

		// Adding the music source to the MediaMetadata (and consequently using it in the
		// mediaSession.setMetadata) is not a good idea for a real world music app, because
		// the session metadata can be accessed by notification listeners. This is done in this
		// sample for convenience only.
		//noinspection ResourceType
		return new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
				.putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, source)
				.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
				.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
				.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
				.putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
				.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, iconUrl)
				.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
				.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
				.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount)
				.build();
	}

}
