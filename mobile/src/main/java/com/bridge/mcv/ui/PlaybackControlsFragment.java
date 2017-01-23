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
package com.bridge.mcv.ui;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bridge.mcv.AlbumArtCache;
import com.bridge.mcv.MusicService;
//import com.android.mcv.R;
import com.bridge.mcv.R;
import com.bridge.mcv.model.RemoteJSONSource;
import com.bridge.mcv.utils.LogHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that shows the Media Queue to the user.
 */
public class PlaybackControlsFragment extends Fragment {

    private static final String TAG = LogHelper.makeLogTag(PlaybackControlsFragment.class);

    private ImageButton mPlayPause;
	private ImageButton mloveButton;
	private ImageButton mdownloadtrack;
    private TextView mTitle;
    private TextView mSubtitle;
    private TextView mExtraInfo;
    private ImageView mAlbumArt;
    private String mArtUrl;
	private MediaBrowserCompat mMediaBrowser;
	private final String PATH = "/data/data/com.bridge.mcv/";
	private String musicfile;
	private String mCurrentArtUrl;
	private String source;
	private String mofflineStatus;
	public static List<String[]> downloadqueue = new ArrayList<String[]>();
	public static boolean downloadInProgress = false;
	// Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            LogHelper.d(TAG, "Received playback state change to state ", state.getState());
            PlaybackControlsFragment.this.onPlaybackStateChanged(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (metadata == null) {
                return;
            }
            LogHelper.d(TAG, "Received metadata state change to mediaId=",
                    metadata.getDescription().getMediaId(),
                    " song=", metadata.getDescription().getTitle());
            PlaybackControlsFragment.this.onMetadataChanged(metadata);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_playback_controls, container, false);

        mPlayPause = (ImageButton) rootView.findViewById(R.id.play_pause);
        mPlayPause.setEnabled(true);
        mPlayPause.setOnClickListener(mButtonListener);

		mloveButton = (ImageButton) rootView.findViewById(R.id.heartMusic);
		mloveButton.setEnabled(true);
		mloveButton.setOnClickListener(mloveitDialog);

        mTitle = (TextView) rootView.findViewById(R.id.title);
        mSubtitle = (TextView) rootView.findViewById(R.id.artist);
        mExtraInfo = (TextView) rootView.findViewById(R.id.extra_info);
        mAlbumArt = (ImageView) rootView.findViewById(R.id.album_art);



        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), FullScreenPlayerActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                MediaControllerCompat controller = ((FragmentActivity) getActivity())
                        .getSupportMediaController();
                MediaMetadataCompat metadata = controller.getMetadata();
                if (metadata != null) {
                    intent.putExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION,
                        metadata.getDescription());
                }
                startActivity(intent);
            }
        });
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        LogHelper.d(TAG, "fragment.onStart");
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            onConnected();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        LogHelper.d(TAG, "fragment.onStop");
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            controller.unregisterCallback(mCallback);
        }
    }

    public void onConnected() {
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        LogHelper.d(TAG, "onConnected, mediaController==null? ", controller == null);
        if (controller != null) {
            onMetadataChanged(controller.getMetadata());
            onPlaybackStateChanged(controller.getPlaybackState());
            controller.registerCallback(mCallback);
        }
    }

    private void onMetadataChanged(MediaMetadataCompat metadata) {
        LogHelper.d(TAG, "onMetadataChanged ", metadata);
        if (getActivity() == null) {
            LogHelper.w(TAG, "onMetadataChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.");
            return;
        }
        if (metadata == null) {
            return;
        }

        mTitle.setText(metadata.getDescription().getTitle());
        mSubtitle.setText(metadata.getDescription().getSubtitle());
        String artUrl = null;
        if (metadata.getDescription().getIconUri() != null) {
            artUrl = metadata.getDescription().getIconUri().toString();
        }
        if (!TextUtils.equals(artUrl, mArtUrl)) {
            mArtUrl = artUrl;
            Bitmap art = metadata.getDescription().getIconBitmap();
            AlbumArtCache cache = AlbumArtCache.getInstance();
            if (art == null) {
                art = cache.getIconImage(mArtUrl);
            }
            if (art != null) {
                mAlbumArt.setImageBitmap(art);
            } else {
                cache.fetch(artUrl, new AlbumArtCache.FetchListener() {
                            @Override
                            public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                                if (icon != null) {
                                    LogHelper.d(TAG, "album art icon of w=", icon.getWidth(),
                                            " h=", icon.getHeight());
                                    if (isAdded()) {
                                        mAlbumArt.setImageBitmap(icon);
                                    }
                                }
                            }
                        }
                );
            }
        }
    }

    public void setExtraInfo(String extraInfo) {
        if (extraInfo == null) {
            mExtraInfo.setVisibility(View.GONE);
        } else {
            mExtraInfo.setText(extraInfo);
            mExtraInfo.setVisibility(View.VISIBLE);
        }
    }

    private void onPlaybackStateChanged(PlaybackStateCompat state) {
        LogHelper.d(TAG, "onPlaybackStateChanged ", state);
        if (getActivity() == null) {
            LogHelper.w(TAG, "onPlaybackStateChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.");
            return;
        }
        if (state == null) {
            return;
        }
        boolean enablePlay = false;
        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_STOPPED:
                enablePlay = true;
                break;
            case PlaybackStateCompat.STATE_ERROR:
                LogHelper.e(TAG, "error playbackstate: ", state.getErrorMessage());
                Toast.makeText(getActivity(), state.getErrorMessage(), Toast.LENGTH_LONG).show();
                break;
        }

        if (enablePlay) {
            mPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_play_arrow_black_36dp));
        } else {
            mPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(getActivity(), R.drawable.ic_pause_black_36dp));
        }

        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        String extraInfo = null;
        if (controller != null && controller.getExtras() != null) {
            String castName = controller.getExtras().getString(MusicService.EXTRA_CONNECTED_CAST);
            if (castName != null) {
                extraInfo = getResources().getString(R.string.casting_to_device, castName);
            }
        }
        setExtraInfo(extraInfo);
    }

    private final View.OnClickListener mButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MediaControllerCompat controller = ((FragmentActivity) getActivity())
                    .getSupportMediaController();
            PlaybackStateCompat stateObj = controller.getPlaybackState();
            final int state = stateObj == null ?
                    PlaybackStateCompat.STATE_NONE : stateObj.getState();
            LogHelper.d(TAG, "Button pressed, in state " + state);
            switch (v.getId()) {
                case R.id.play_pause:
                    LogHelper.d(TAG, "Play button pressed, in state " + state);
                    if (state == PlaybackStateCompat.STATE_PAUSED ||
                            state == PlaybackStateCompat.STATE_STOPPED ||
                            state == PlaybackStateCompat.STATE_NONE) {
                        playMedia();
                    } else if (state == PlaybackStateCompat.STATE_PLAYING ||
                            state == PlaybackStateCompat.STATE_BUFFERING ||
                            state == PlaybackStateCompat.STATE_CONNECTING) {
                        pauseMedia();
                    }
                    break;
            }
        }
    };

	private final View.OnClickListener mloveitDialog = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			final Dialog dialog = new Dialog(getContext());
			dialog.setContentView(R.layout.downlod_dialog);
			dialog.setTitle("Title...");
			// set the custom dialog components - text, image and button
			TextView text = (TextView) dialog.findViewById(R.id.text);
			text.setText("So you love this track? You can save track for offline use or share with your friends on facebook");
			ImageView image = (ImageView) dialog.findViewById(R.id.image);
			image.setImageResource(R.drawable.ic_launcher);

			ImageButton downloadBut = (ImageButton) dialog.findViewById(R.id.download_button);
			TextView downloadText = (TextView) dialog.findViewById(R.id.download_text);
			mofflineStatus = (String) downloadText.getText();
			//downloadBut.setImageIcon(R.drawable.delete_icon);


			mdownloadtrack = (ImageButton) dialog.findViewById(R.id.download_button);
			mdownloadtrack.setEnabled(true);
			mdownloadtrack.setOnClickListener(mloveitkeepit);
			if (FullScreenPlayerActivity.offlinejsonTracks != null) {
				try{
					for (int j = 0; j < FullScreenPlayerActivity.offlinejsonTracks.length(); j++) {
						JSONObject json = FullScreenPlayerActivity.offlinejsonTracks.getJSONObject(j);
						String title = json.getString("title");
						String artistTemp = json.getString("artist");

						String artist = " "+ artistTemp;
						String nTitle = (String) mTitle.getText();
						String npartist = (String) mSubtitle.getText();
						String nartist =  " "+ npartist;
						if(nTitle.equals(title) && nartist.equals(artist)){
							text.setText("You have already downloaded this track");
							mofflineStatus = "delete track";
							downloadText.setText("delete track");
							downloadBut.setImageResource(R.drawable.delete_icon);
						}
					}
				}
				catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}

			dialog.show();
		}
	};

	private final View.OnClickListener mloveitkeepit = new View.OnClickListener() {
		@TargetApi(Build.VERSION_CODES.KITKAT)
		@Override
		public void onClick(View v) {
			mdownloadtrack.setEnabled(false);
			if (mofflineStatus == "delete track"){
				if (FullScreenPlayerActivity.offlinejsonTracks != null) {
					try{
						for (int j = 0; j < FullScreenPlayerActivity.offlinejsonTracks.length(); j++) {
							JSONObject json = FullScreenPlayerActivity.offlinejsonTracks.getJSONObject(j);
							String title = json.getString("title");
							String artistTemp = json.getString("artist");
							String musicfile = json.getString("musicfile");
							String artist = " "+ artistTemp;
							String nTitle = (String) mTitle.getText();
							String npartist = (String) mSubtitle.getText();
							String nartist =  " "+ npartist;
							if(nTitle.equals(title) && nartist.equals(artist)){
								FullScreenPlayerActivity.offlinejsonTracks.remove(j);
								String offstr = FullScreenPlayerActivity.offlinejsonTracks.toString();

								TinyDB tinyDB = new TinyDB(getContext());
							    tinyDB.putString("offMusic", offstr);

								File fileD = new File(PATH, musicfile);
								boolean deleted = fileD.delete();
							}
						}
					}
					catch (JSONException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
				return;
			}

			LogHelper.d(TAG, "yeah yeah yeah yeah");
			if (RemoteJSONSource.jsonTracks != null) {
				JSONObject list1 = new JSONObject();
				if (FullScreenPlayerActivity.offlinejsonTracks ==null) FullScreenPlayerActivity.offlinejsonTracks= new JSONArray();
				try{
					for (int j = 0; j < RemoteJSONSource.jsonTracks.length(); j++) {
						JSONObject json = RemoteJSONSource.jsonTracks.getJSONObject(j);
						String title = json.getString("title");
						String artistTemp = json.getString("artist");
						String albumTemp = json.getString("genre");
						String artist = " "+ artistTemp;
						String album = " "+ albumTemp;
						String nTitle = (String) mTitle.getText();
						String npartist = (String) mSubtitle.getText();
						String nartist =  " "+ npartist;
						if(nTitle.equals(title) && nartist.equals(artist)){
							Toast.makeText(getContext(), title + " will be added to offline",
									Toast.LENGTH_LONG).show();
							source = json.getString("source");
							musicfile = json.getString("musicfile");
							json.put("genre", "Offline");
							json.put("source", PATH + musicfile);
							FullScreenPlayerActivity.offlinejsonTracks.put(json);


							String[] trackToDownload = {source, PATH +musicfile};
							if (downloadqueue == null) downloadqueue = new ArrayList<String[]>();
                            downloadqueue.add(trackToDownload);
							downloadManager();
							String offstr = FullScreenPlayerActivity.offlinejsonTracks.toString();
							TinyDB tinyDB = new TinyDB(getContext());
							tinyDB.putString("offMusic", offstr);
							LogHelper.w(TAG,"jsonCars = " + FullScreenPlayerActivity.offlinejsonTracks);
						}
					}
				}
				catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
	};

    private void playMedia() {
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            controller.getTransportControls().play();
        }
    }

    private void pauseMedia() {
        MediaControllerCompat controller = ((FragmentActivity) getActivity())
                .getSupportMediaController();
        if (controller != null) {
            controller.getTransportControls().pause();
        }
    }

	public static void downloadManager(){
		if (downloadInProgress || downloadqueue == null || downloadqueue.isEmpty()){
			Log.d(TAG, "download in progress or list empty: ");
			return;
		}
		else{
			Log.d(TAG, "downloadManager: ");
			LogHelper.d(TAG, "item waiting for D ",downloadqueue);
			String[] downloadParams = downloadqueue.get(0);
			String source = downloadParams[0];
			String path = downloadParams[1];
			new MusicDownloader().execute(source, path);
		}

	}
}
class MusicDownloader extends AsyncTask<String, Void, Void>
{

	@Override
	protected Void doInBackground(String... params) {
		PlaybackControlsFragment.downloadInProgress = true;
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
				.permitAll().build();
		StrictMode.setThreadPolicy(policy);
		try {
			URL url = new URL(params[0]);
			File file = new File(params[1]);

			long startTime = System.currentTimeMillis();

			URLConnection ucon = url.openConnection();

			InputStream is = ucon.getInputStream();
			BufferedInputStream bis = new BufferedInputStream(is);

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] data = new byte[50];
			int current = 0;

			while((current = bis.read(data,0,data.length)) != -1){
				buffer.write(data,0,current);
			}

			FileOutputStream fos = new FileOutputStream(file);
			fos.write(buffer.toByteArray());
			fos.close();
			LogHelper.d("Music", "download ready in"
					+ ((System.currentTimeMillis() - startTime) / 1000)
					+ " sec");
			removeFromQueue(params[0]);
		} catch (IOException e) {
			LogHelper.d("MusicDownloader", "Error: " + e);
		}
		return null;
	}

	private void removeFromQueue(String source){
		List<String[]> mdownloadqueue  = PlaybackControlsFragment.downloadqueue;
		for (int i = 0; i < mdownloadqueue.size(); i++) {
			String[] element = mdownloadqueue.get(i);
			String itemtoDelete = element[0];
			if (itemtoDelete == source){
				mdownloadqueue.remove(i);
			}
		}
		PlaybackControlsFragment.downloadInProgress = false;
		PlaybackControlsFragment.downloadManager();
	}
}

