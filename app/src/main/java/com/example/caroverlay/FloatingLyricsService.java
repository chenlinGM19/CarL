package com.example.caroverlay;

import android.app.Service;
import android.content.Intent;
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

public class FloatingLyricsService extends Service {

    public static final String ACTION_UPDATE_SIZE = "action_update_size";
    public static final String ACTION_UPDATE_COLOR = "action_update_color";
    public static final String ACTION_UPDATE_TEXT = "action_update_text";
    public static final String ACTION_LOCK = "action_lock";
    public static final String ACTION_UNLOCK = "action_unlock";
    public static final String ACTION_HIDE = "action_hide";
    
    public static final String EXTRA_SIZE = "extra_size";
    public static final String EXTRA_COLOR = "extra_color";
    public static final String EXTRA_TEXT = "extra_text";

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvLyrics;
    private ImageView ivDragHandle;
    private WindowManager.LayoutParams params;

    private boolean isLocked = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeWindow();
    }

    private void initializeWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inflate the layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.window_floating_lyrics, null);
        tvLyrics = floatingView.findViewById(R.id.tvLyrics);
        ivDragHandle = floatingView.findViewById(R.id.ivDragHandle);

        // Layout Parameters
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

        // Initial Position
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        // Add View
        windowManager.addView(floatingView, params);

        // Setup Drag Logic
        setupTouchListener();
    }

    private void setupTouchListener() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLocked) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_UPDATE_SIZE:
                        float size = intent.getFloatExtra(EXTRA_SIZE, 20f);
                        if (tvLyrics != null) tvLyrics.setTextSize(size);
                        break;
                    case ACTION_UPDATE_COLOR:
                        int color = intent.getIntExtra(EXTRA_COLOR, 0xFFFFFFFF);
                        if (tvLyrics != null) tvLyrics.setTextColor(color);
                        break;
                    case ACTION_UPDATE_TEXT:
                        String text = intent.getStringExtra(EXTRA_TEXT);
                        if (tvLyrics != null && text != null) tvLyrics.setText(text);
                        break;
                    case ACTION_LOCK:
                        setLocked(true);
                        break;
                    case ACTION_UNLOCK:
                        setLocked(false);
                        break;
                    case ACTION_HIDE:
                        stopSelf();
                        break;
                }
            }
        }
        return START_STICKY;
    }

    private void setLocked(boolean locked) {
        this.isLocked = locked;
        if (locked) {
            // When locked: allow clicks to pass through
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                           WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                           WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            ivDragHandle.setVisibility(View.GONE);
            floatingView.setBackgroundResource(0); // Make transparent
        } else {
            // When unlocked: allow dragging
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                           WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            ivDragHandle.setVisibility(View.VISIBLE);
            floatingView.setBackgroundResource(R.drawable.bg_rounded_overlay);
        }
        windowManager.updateViewLayout(floatingView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }
}
