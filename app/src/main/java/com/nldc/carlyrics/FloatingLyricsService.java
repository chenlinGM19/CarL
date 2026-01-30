package com.nldc.carlyrics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

public class FloatingLyricsService extends Service {

    public static final String ACTION_UPDATE_TEXT = "action_update_text";
    public static final String ACTION_SHOW = "action_show";
    public static final String ACTION_HIDE = "action_hide";
    
    public static final String EXTRA_TEXT = "extra_text";

    private static final String PREFS_NAME = "OverlayPrefs";
    private static final String NOTIFICATION_CHANNEL_ID = "lyrics_overlay_service";

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvLyrics;
    private WindowManager.LayoutParams params;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceNotification();
        initializeWindow();
    }

    private void startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Lyrics Overlay",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }

            Notification.Builder builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Car Lyrics Overlay")
                    .setContentText("Service is running")
                    .setSmallIcon(R.drawable.ic_app_icon);

            try {
                startForeground(1, builder.build());
            } catch (Exception e) {
                Log.e("CarLyrics", "Error starting foreground service", e);
            }
        }
    }

    private void initializeWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            floatingView = LayoutInflater.from(this).inflate(R.layout.window_floating_lyrics, null);
            tvLyrics = floatingView.findViewById(R.id.tvLyrics);

            int layoutFlag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
            }

            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            
            // Load position
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            params.x = prefs.getInt("x", 100);
            params.y = prefs.getInt("y", 100);

            try {
                windowManager.addView(floatingView, params);
            } catch (Exception e) {
                Log.e("CarLyrics", "Error adding view to window manager", e);
            }
            
            setupTouchListener();
            
        } catch (Exception e) {
            Log.e("CarLyrics", "Failed to initialize window layout", e);
        }
    }

    private void setupTouchListener() {
        if (floatingView == null) return;
        
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        // Save position
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit().putInt("x", params.x).putInt("y", params.y).apply();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            windowManager.updateViewLayout(floatingView, params);
                        } catch (Exception e) {
                            // View might have been removed
                        }
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            
            if (floatingView == null) {
                initializeWindow();
            }

            if (ACTION_UPDATE_TEXT.equals(action)) {
                String text = intent.getStringExtra(EXTRA_TEXT);
                if (text != null && tvLyrics != null) {
                    tvLyrics.setText(text);
                }
            } else if (ACTION_SHOW.equals(action)) {
                if (floatingView != null) floatingView.setVisibility(View.VISIBLE);
            } else if (ACTION_HIDE.equals(action)) {
                if (floatingView != null) floatingView.setVisibility(View.GONE);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                // View might not be attached
            }
        }
    }
}