package com.nldc.carlyrics;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.List;

public class MediaListenerService extends NotificationListenerService {

    private MusicBroadcastReceiver musicReceiver;
    private MediaSessionManager mediaSessionManager;
    private ComponentName componentName;

    @Override
    public void onCreate() {
        super.onCreate();
        componentName = new ComponentName(this, MediaListenerService.class);
        registerMusicReceiver();
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        
        try {
            // Fix: Must pass componentName to avoid SecurityException on some devices
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName);
            updateFromActiveSessions();
        } catch (SecurityException e) {
            Log.e("CarLyrics", "Permission missing for MediaSession access", e);
        } catch (Exception e) {
            Log.e("CarLyrics", "Error setting up session listener", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (musicReceiver != null) {
                unregisterReceiver(musicReceiver);
            }
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
        
        if (mediaSessionManager != null) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    // --- Broadcast Receiver for Car Units (Kuwo, System) ---
    private void registerMusicReceiver() {
        musicReceiver = new MusicBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        // Standard Android Music
        filter.addAction("com.android.music.metachanged");
        filter.addAction("com.android.music.playstatechanged");
        
        // Kuwo Music Car
        filter.addAction("cn.kuwo.kwmusiccar.action.PLAY_STATUS_CHANGED");
        filter.addAction("cn.kuwo.kwmusiccar.action.META_CHANGED");
        
        // Generic/Other
        filter.addAction("com.kugou.android.music.metachanged");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(musicReceiver, filter);
        }
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
            if (mediaSessionManager != null) {
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(componentName);
                registerCallbacks(controllers);
            }
        } catch (SecurityException e) {
            // Permission not granted yet
            Log.e("CarLyrics", "SecurityException retrieving active sessions", e);
        } catch (Exception e) {
            Log.e("CarLyrics", "Error retrieving active sessions", e);
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
        
        try {
            // Fix: Use startForegroundService for Android O+ to avoid IllegalStateException in background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e("CarLyrics", "Failed to start overlay service", e);
        }
    }
}