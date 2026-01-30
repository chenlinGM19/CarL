package com.example.caroverlay;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;

public class MediaListenerService extends NotificationListenerService {

    private MusicBroadcastReceiver musicReceiver;
    private MediaSessionManager mediaSessionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        registerMusicReceiver();
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, null);
        updateFromActiveSessions();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (musicReceiver != null) {
            unregisterReceiver(musicReceiver);
        }
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        }
    }

    // --- Broadcast Receiver for Car Units (Kuwo, System) ---
    private void registerMusicReceiver() {
        musicReceiver = new MusicBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        // Standard Android Music
        filter.addAction("com.android.music.metachanged");
        filter.addAction("com.android.music.playstatechanged");
        
        // Kuwo Music Car (From Reference Manifest)
        filter.addAction("cn.kuwo.kwmusiccar.action.PLAY_STATUS_CHANGED");
        filter.addAction("cn.kuwo.kwmusiccar.action.META_CHANGED");
        
        // Generic/Other
        filter.addAction("com.kugou.android.music.metachanged");
        
        registerReceiver(musicReceiver, filter);
    }

    private class MusicBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String textToDisplay = null;

            if (action != null) {
                if (action.contains("cn.kuwo")) {
                    // Kuwo specific extras
                    String lyric = intent.getStringExtra("valid_lyric");
                    if (lyric != null && !lyric.isEmpty()) {
                        textToDisplay = lyric;
                    } else {
                        String song = intent.getStringExtra("song_name");
                        String artist = intent.getStringExtra("artist_name");
                        if (song != null) textToDisplay = song + (artist != null ? " - " + artist : "");
                    }
                } else {
                    // Standard Android
                    String artist = intent.getStringExtra("artist");
                    String track = intent.getStringExtra("track");
                    if (track != null) {
                        textToDisplay = track + (artist != null ? " - " + artist : "");
                    }
                }
            }

            if (textToDisplay != null) {
                sendTextToOverlay(textToDisplay);
            }
        }
    }

    // --- Notification / MediaSession Listener ---

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener = 
        new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            registerCallbacks(controllers);
        }
    };

    private void updateFromActiveSessions() {
        try {
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(new ComponentName(this, MediaListenerService.class));
            registerCallbacks(controllers);
        } catch (SecurityException e) {
            // Permission not granted yet
        }
    }

    private void registerCallbacks(List<MediaController> controllers) {
        if (controllers == null) return;
        for (MediaController controller : controllers) {
            controller.registerCallback(new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    if (metadata == null) return;
                    String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    
                    if (title != null) {
                        String text = title + (artist != null ? "\n" + artist : "");
                        sendTextToOverlay(text);
                    }
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    super.onPlaybackStateChanged(state);
                }
            });
        }
    }

    private void sendTextToOverlay(String text) {
        Intent intent = new Intent(this, FloatingLyricsService.class);
        intent.setAction(FloatingLyricsService.ACTION_UPDATE_TEXT);
        intent.putExtra(FloatingLyricsService.EXTRA_TEXT, text);
        startService(intent);
    }
}
