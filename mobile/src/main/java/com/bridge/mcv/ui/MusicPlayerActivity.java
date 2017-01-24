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

import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
import com.facebook.ads.*;
//import com.bridge.mcv.R;
import com.bridge.mcv.R;
import com.bridge.mcv.model.MusicProvider;
import com.bridge.mcv.utils.LogHelper;
import com.google.gson.Gson;

import android.widget.Toast;
import org.json.JSONArray;

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class MusicPlayerActivity extends BaseActivity
        implements MediaBrowserFragment.MediaFragmentListener {

    private InterstitialAd interstitialAd;
    private static final String TAG = LogHelper.makeLogTag(MusicPlayerActivity.class);
    private static final String SAVED_MEDIA_ID="com.bridge.mcv.MEDIA_ID";
    private static final String FRAGMENT_TAG = "mcv_list_container";

    public static final String EXTRA_START_FULLSCREEN =
            "com.bridge.mcv.EXTRA_START_FULLSCREEN";


    /**
     * Optionally used with {@link #EXTRA_START_FULLSCREEN} to carry a MediaDescription to
     * the {@link FullScreenPlayerActivity}, speeding up the screen rendering
     * while the {@link android.support.v4.media.session.MediaControllerCompat} is connecting.
     */
    public static final String EXTRA_CURRENT_MEDIA_DESCRIPTION =
        "com.bridge.mcv.CURRENT_MEDIA_DESCRIPTION";

    private Bundle mVoiceSearchParams;
	private MusicProvider mMusicProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");
        interstitialAd = new InterstitialAd(this, "786906088117796_809298215878583");
		//AdSettings.addTestDevice("bd218f5d1c515d3269697b617e6c1918");
		AdSettings.addTestDevice("d169afd6764e2d50fd22ae6ca3d67614");
		interstitialAd.setAdListener(new InterstitialAdListener() {
			@Override
			public void onInterstitialDisplayed(Ad ad) {
				// Interstitial displayed callback
			}

			@Override
			public void onInterstitialDismissed(Ad ad) {
				// Interstitial dismissed callback
			}

			@Override
			public void onError(Ad ad, AdError adError) {
				// Ad error callback
				Toast.makeText(MusicPlayerActivity.this, "Error: " + adError.getErrorMessage(),
						Toast.LENGTH_LONG).show();
			}

			@Override
			public void onAdLoaded(Ad ad) {
				//AdSettings.addTestDevice("bd218f5d1c515d3269697b617e6c1918");
				// Show the ad when it's done loading.
				interstitialAd.show();
			}

			@Override
			public void onAdClicked(Ad ad) {
				// Ad clicked callback
			}
		});

		// Load the interstitial ad
		//AdSettings.addTestDevice("bd218f5d1c515d3269697b617e6c1918");
		interstitialAd.loadAd();

        setContentView(R.layout.activity_player);

        initializeToolbar();
        initializeFromParams(savedInstanceState, getIntent());
		if (FullScreenPlayerActivity.offlinejsonTracks ==null) FullScreenPlayerActivity.offlinejsonTracks= new JSONArray();
        Gson gson = new Gson();
       TinyDB tinyDB = new TinyDB(getApplicationContext());
		String offMusic = tinyDB.getString("offMusic");

		try {

			JSONArray obj = new JSONArray(offMusic);
			FullScreenPlayerActivity.offlinejsonTracks = obj;

		} catch (Throwable tx) {
			LogHelper.w("My App", "Could not parse malformed JSON: \"" + offMusic + "\"");
		}
		OfflineStore.yam = tinyDB.getString("yam");
        if (savedInstanceState == null) {
            LogHelper.d(TAG, "Activity onCreate");
            startFullScreenActivityIfNeeded(getIntent());
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
		String FILENAME = "myfile";
        String mediaId = getMediaId();
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId);
        }
        super.onSaveInstanceState(outState);
    }
	@Override
	protected void onDestroy() {
		if (interstitialAd != null) {
			interstitialAd.destroy();
		}
		super.onDestroy();
	}

    @Override
    public void onMediaItemSelected(MediaBrowserCompat.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.getMediaId());
        if (item.isPlayable()) {
            getSupportMediaController().getTransportControls()
                    .playFromMediaId(item.getMediaId(), null);
        } else if (item.isBrowsable()) {
				LogHelper.d(TAG, "seleeeeeeeee");
            navigateToBrowser(item.getMediaId());
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.getMediaId());
        }
    }

    @Override
    public void setToolbarTitle(CharSequence title) {
        LogHelper.d(TAG, "Setting toolbar title to ", title);
        if (title == null) {
            title = getString(R.string.app_name);
        }
        setTitle(title);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogHelper.d(TAG, "onNewIntent, intent=" + intent);
        initializeFromParams(null, intent);
        startFullScreenActivityIfNeeded(intent);
    }

    private void startFullScreenActivityIfNeeded(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            Intent fullScreenIntent = new Intent(this, FullScreenPlayerActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION,
                    intent.getParcelableExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION));
            startActivity(fullScreenIntent);
        }
    }

    protected void initializeFromParams(Bundle savedInstanceState, Intent intent) {
        String mediaId = null;
        // check if we were started from a "Play XYZ" voice search. If so, we save the extras
        // (which contain the query details) in a parameter, so we can reuse it later, when the
        // MediaSession is connected.
        if (intent.getAction() != null
            && intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
            mVoiceSearchParams = intent.getExtras();
            LogHelper.d(TAG, "Starting from voice search query=",
                mVoiceSearchParams.getString(SearchManager.QUERY));
        } else {
            if (savedInstanceState != null) {
                // If there is a saved media ID, use it
                mediaId = savedInstanceState.getString(SAVED_MEDIA_ID);
            }
        }
        navigateToBrowser(mediaId);
    }

    private void navigateToBrowser(String mediaId) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId);
        MediaBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new MediaBrowserFragment();
            fragment.setMediaId(mediaId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                R.animator.slide_in_from_left, R.animator.slide_out_to_right);
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null) {
                transaction.addToBackStack(null);
            }
            transaction.commit();
        }
    }

    public String getMediaId() {
        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment == null) {
            return null;
        }
		LogHelper.w(TAG, "Loggin off",FullScreenPlayerActivity.offlinejsonTracks);
        return fragment.getMediaId();
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    protected void onMediaControllerConnected() {

        if (mVoiceSearchParams != null) {
            // If there is a bootstrap parameter to start from a search query, we
            // send it to the media session and set it to null, so it won't play again
            // when the activity is stopped/started or recreated:
            String query = mVoiceSearchParams.getString(SearchManager.QUERY);
            getSupportMediaController().getTransportControls()
                    .playFromSearch(query, mVoiceSearchParams);
            mVoiceSearchParams = null;
        }
        getBrowseFragment().onConnected();
    }
}


