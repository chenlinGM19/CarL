package com.nldc.carlyrics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingLyricsService extends Service {

    // Actions
    public static final String ACTION_UPDATE_TEXT = "action_update_text";
    public static final String ACTION_SHOW = "action_show";
    public static final String ACTION_HIDE = "action_hide";
    public static final String ACTION_LOCK = "action_lock";
    public static final String ACTION_UNLOCK = "action_unlock";
    public static final String ACTION_RESET_POS = "action_reset_pos";
    
    // UI Customization Actions
    public static final String ACTION_UPDATE_SIZE = "action_update_size";
    public static final String ACTION_UPDATE_ALPHA_TEXT = "action_update_alpha_text";
    public static final String ACTION_UPDATE_ALPHA_BG = "action_update_alpha_bg";
    public static final String ACTION_UPDATE_STYLE_PRESET = "action_update_style_preset";
    public static final String ACTION_UPDATE_ALIGN = "action_update_align";
    public static final String ACTION_UPDATE_FONT_FAMILY = "action_update_font_family";
    public static final String ACTION_UPDATE_FONT_STYLE = "action_update_font_style";
    public static final String ACTION_UPDATE_COLOR = "action_update_color";
    public static final String ACTION_UPDATE_PADDING = "action_update_padding";
    public static final String ACTION_MOVE_POS = "action_move_pos";

    // Extras
    public static final String EXTRA_VALUE = "extra_value"; 
    public static final String EXTRA_TEXT = "extra_text";
    public static final String EXTRA_STRING_VAL = "extra_string_val";
    public static final String EXTRA_INT_VAL = "extra_int_val"; // For color or alignment int
    public static final String EXTRA_BOOL_BOLD = "extra_bool_bold";
    public static final String EXTRA_BOOL_ITALIC = "extra_bool_italic";
    public static final String EXTRA_DX = "extra_dx";
    public static final String EXTRA_DY = "extra_dy";

    private static final String PREFS_NAME = "OverlayPrefs";
    private static final String NOTIFICATION_CHANNEL_ID = "lyrics_overlay_service";

    private WindowManager windowManager;
    private View floatingView;
    private LinearLayout rootLayout;
    private TextView tvLyrics;
    private ImageView ivDragHandle;
    private WindowManager.LayoutParams params;

    private boolean isLocked = false;
    private boolean isVisible = true;
    
    // State
    private float currentTextSize = 20f;
    private float currentTextAlpha = 1.0f;
    private float currentBgAlpha = 0.5f;
    private int currentPadding = 12;
    private int currentTextColor = Color.WHITE;
    
    // Typography State
    private int currentGravity = Gravity.CENTER;
    private String currentFontFamily = "default";
    private boolean isBold = false;
    private boolean isItalic = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceNotification();
        initializeWindow();
        loadState();
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

            Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Car Lyrics Overlay")
                    .setContentText("Service is running")
                    .setSmallIcon(R.drawable.ic_app_icon)
                    .build();

            startForeground(1, notification);
        }
    }

    private void initializeWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.window_floating_lyrics, null);
        rootLayout = floatingView.findViewById(R.id.root_layout);
        tvLyrics = floatingView.findViewById(R.id.tvLyrics);
        ivDragHandle = floatingView.findViewById(R.id.ivDragHandle);

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
        params.x = 100;
        params.y = 100;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
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
                        savePosition();
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
        if (intent != null && intent.getAction() != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            String action = intent.getAction();

            switch (action) {
                case ACTION_UPDATE_TEXT:
                    String text = intent.getStringExtra(EXTRA_TEXT);
                    if (tvLyrics != null && text != null) tvLyrics.setText(text);
                    break;
                    
                case ACTION_SHOW:
                    setVisibility(true);
                    break;
                case ACTION_HIDE:
                    setVisibility(false);
                    break;
                    
                case ACTION_LOCK:
                    setLocked(true);
                    break;
                case ACTION_UNLOCK:
                    setLocked(false);
                    break;
                    
                case ACTION_RESET_POS:
                    params.x = 100;
                    params.y = 100;
                    windowManager.updateViewLayout(floatingView, params);
                    savePosition();
                    break;
                    
                case ACTION_MOVE_POS:
                    int dx = intent.getIntExtra(EXTRA_DX, 0);
                    int dy = intent.getIntExtra(EXTRA_DY, 0);
                    params.x += dx;
                    params.y += dy;
                    windowManager.updateViewLayout(floatingView, params);
                    savePosition();
                    break;

                // --- Visual Customizations ---
                
                case ACTION_UPDATE_SIZE:
                    float size = intent.getFloatExtra(EXTRA_VALUE, 20f);
                    currentTextSize = size;
                    tvLyrics.setTextSize(size);
                    editor.putFloat("text_size", size).apply();
                    break;
                    
                case ACTION_UPDATE_ALPHA_TEXT:
                    float tAlpha = intent.getFloatExtra(EXTRA_VALUE, 1.0f);
                    currentTextAlpha = tAlpha;
                    tvLyrics.setAlpha(tAlpha);
                    editor.putFloat("text_alpha", tAlpha).apply();
                    break;
                    
                case ACTION_UPDATE_ALPHA_BG:
                    float bAlpha = intent.getFloatExtra(EXTRA_VALUE, 0.5f);
                    currentBgAlpha = bAlpha;
                    updateBackgroundAlpha();
                    editor.putFloat("bg_alpha", bAlpha).apply();
                    break;

                case ACTION_UPDATE_PADDING:
                    int pad = intent.getIntExtra(EXTRA_INT_VAL, 12);
                    currentPadding = pad;
                    updatePadding();
                    editor.putInt("padding", pad).apply();
                    break;

                case ACTION_UPDATE_COLOR:
                    int color = intent.getIntExtra(EXTRA_INT_VAL, Color.WHITE);
                    currentTextColor = color;
                    tvLyrics.setTextColor(color);
                    editor.putInt("text_color", color).apply();
                    break;
                    
                case ACTION_UPDATE_ALIGN:
                    int align = intent.getIntExtra(EXTRA_INT_VAL, Gravity.CENTER);
                    currentGravity = align;
                    tvLyrics.setGravity(align);
                    editor.putInt("gravity", align).apply();
                    break;
                    
                case ACTION_UPDATE_FONT_FAMILY:
                    String fam = intent.getStringExtra(EXTRA_STRING_VAL);
                    if (fam != null) {
                        currentFontFamily = fam;
                        updateTypeface();
                        editor.putString("font_family", fam).apply();
                    }
                    break;
                    
                case ACTION_UPDATE_FONT_STYLE:
                    isBold = intent.getBooleanExtra(EXTRA_BOOL_BOLD, false);
                    isItalic = intent.getBooleanExtra(EXTRA_BOOL_ITALIC, false);
                    updateTypeface();
                    editor.putBoolean("is_bold", isBold).putBoolean("is_italic", isItalic).apply();
                    break;

                case ACTION_UPDATE_STYLE_PRESET:
                    String styleName = intent.getStringExtra(EXTRA_STRING_VAL);
                    applyPresetStyle(styleName);
                    // Save the last preset used, but individual overrides are saved separately
                    editor.putString("last_preset", styleName).apply();
                    break;
            }
        }
        return START_STICKY;
    }

    private void updateBackgroundAlpha() {
        if (rootLayout != null && rootLayout.getBackground() != null) {
            rootLayout.getBackground().setAlpha((int)(currentBgAlpha * 255));
        }
    }
    
    private void updatePadding() {
        int px = (int) (currentPadding * getResources().getDisplayMetrics().density);
        rootLayout.setPadding(px, px, px, px);
    }

    private void updateTypeface() {
        Typeface base = Typeface.DEFAULT;
        switch (currentFontFamily) {
            case "mono": base = Typeface.MONOSPACE; break;
            case "serif": base = Typeface.SERIF; break;
            case "sans": base = Typeface.SANS_SERIF; break;
            default: base = Typeface.DEFAULT; break;
        }
        
        int style = Typeface.NORMAL;
        if (isBold && isItalic) style = Typeface.BOLD_ITALIC;
        else if (isBold) style = Typeface.BOLD;
        else if (isItalic) style = Typeface.ITALIC;
        
        tvLyrics.setTypeface(base, style);
    }

    private void applyPresetStyle(String styleName) {
        // Apply preset but don't save to prefs yet (user might tweak)
        tvLyrics.setShadowLayer(0, 0, 0, 0);
        
        switch (styleName) {
            case "Classic":
                updateColor(Color.WHITE);
                tvLyrics.setShadowLayer(4, 2, 2, Color.BLACK);
                break;
            case "Minimal":
                updateColor(Color.WHITE);
                tvLyrics.setShadowLayer(8, 0, 0, Color.BLACK);
                // Minimal implies 0 background usually, but we respect slider for consistency
                break;
            case "Neon":
                updateColor(Color.parseColor("#00FFFF"));
                tvLyrics.setShadowLayer(15, 0, 0, Color.parseColor("#FF00FF"));
                break;
            case "Retro":
                updateColor(Color.GREEN);
                currentFontFamily = "mono";
                updateTypeface();
                break;
            case "Day":
                updateColor(Color.BLACK);
                break;
            case "KTV":
                updateColor(Color.parseColor("#FFD700"));
                tvLyrics.setShadowLayer(10, 0, 0, Color.RED);
                isBold = true;
                updateTypeface();
                break;
        }
    }
    
    private void updateColor(int color) {
        currentTextColor = color;
        tvLyrics.setTextColor(color);
        // Persist immediately for consistency if preset is clicked
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt("text_color", color).apply();
    }

    private void setVisibility(boolean visible) {
        this.isVisible = visible;
        if (floatingView != null) {
            floatingView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void setLocked(boolean locked) {
        this.isLocked = locked;
        if (locked) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                           WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                           WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            ivDragHandle.setVisibility(View.GONE);
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
                           WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            ivDragHandle.setVisibility(View.VISIBLE);
        }
        windowManager.updateViewLayout(floatingView, params);
    }

    private void savePosition() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt("x", params.x).putInt("y", params.y).apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Position
        params.x = prefs.getInt("x", 100);
        params.y = prefs.getInt("y", 100);
        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception e) {}
        
        // Appearance
        currentTextSize = prefs.getFloat("text_size", 20f);
        currentTextAlpha = prefs.getFloat("text_alpha", 1.0f);
        currentBgAlpha = prefs.getFloat("bg_alpha", 0.5f);
        currentTextColor = prefs.getInt("text_color", Color.WHITE);
        currentPadding = prefs.getInt("padding", 12);
        
        tvLyrics.setTextSize(currentTextSize);
        tvLyrics.setAlpha(currentTextAlpha);
        tvLyrics.setTextColor(currentTextColor);
        updateBackgroundAlpha();
        updatePadding();
        
        // Typography
        currentGravity = prefs.getInt("gravity", Gravity.CENTER);
        currentFontFamily = prefs.getString("font_family", "default");
        isBold = prefs.getBoolean("is_bold", false);
        isItalic = prefs.getBoolean("is_italic", false);
        
        tvLyrics.setGravity(currentGravity);
        updateTypeface();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }
}