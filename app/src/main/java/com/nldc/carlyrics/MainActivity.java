package com.nldc.carlyrics;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    // Core
    private SwitchMaterial switchOverlay, switchLock;
    private Button btnPermissionNotification;
    private TextView tvStatus;
    
    // Typography
    private MaterialButtonToggleGroup toggleGroupFont, toggleGroupAlign, toggleGroupStyle;
    private Slider sliderSize;
    
    // Colors
    private Button btnColorWhite, btnColorCyan, btnColorGreen, btnColorYellow, btnColorOrange, btnColorPink, btnColorRed;
    
    // Appearance & Opacity
    private Slider sliderTextAlpha, sliderBgAlpha;
    private Button btnClassic, btnMinimal, btnNeon, btnRetro, btnDay, btnKaraoke;
    
    // Layout
    private Button btnResetPos, btnMoveLeft, btnMoveRight, btnMoveUp, btnMoveDown;
    private Slider sliderPadding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        syncUIWithPrefs();
    }

    private void initViews() {
        // Core
        switchOverlay = findViewById(R.id.switchOverlay);
        switchLock = findViewById(R.id.switchLock);
        btnPermissionNotification = findViewById(R.id.btnPermissionNotification);
        tvStatus = findViewById(R.id.tvStatus);

        // Typography
        toggleGroupFont = findViewById(R.id.toggleGroupFont);
        toggleGroupAlign = findViewById(R.id.toggleGroupAlign);
        toggleGroupStyle = findViewById(R.id.toggleGroupStyle);
        sliderSize = findViewById(R.id.sliderSize);
        
        // Colors
        btnColorWhite = findViewById(R.id.btnColorWhite);
        btnColorCyan = findViewById(R.id.btnColorCyan);
        btnColorGreen = findViewById(R.id.btnColorGreen);
        btnColorYellow = findViewById(R.id.btnColorYellow);
        btnColorOrange = findViewById(R.id.btnColorOrange);
        btnColorPink = findViewById(R.id.btnColorPink);
        btnColorRed = findViewById(R.id.btnColorRed);

        // Appearance
        sliderTextAlpha = findViewById(R.id.sliderTextAlpha);
        sliderBgAlpha = findViewById(R.id.sliderBgAlpha);
        
        btnClassic = findViewById(R.id.btnStyleClassic);
        btnMinimal = findViewById(R.id.btnStyleMinimal);
        btnNeon = findViewById(R.id.btnStyleNeon);
        btnRetro = findViewById(R.id.btnStyleRetro);
        btnDay = findViewById(R.id.btnStyleDay);
        btnKaraoke = findViewById(R.id.btnStyleKaraoke);
        
        // Layout
        btnResetPos = findViewById(R.id.btnResetPos);
        sliderPadding = findViewById(R.id.sliderPadding);
        btnMoveLeft = findViewById(R.id.btnMoveLeft);
        btnMoveRight = findViewById(R.id.btnMoveRight);
        btnMoveUp = findViewById(R.id.btnMoveUp);
        btnMoveDown = findViewById(R.id.btnMoveDown);

        updatePermissionButtonState();
    }
    
    private void syncUIWithPrefs() {
        SharedPreferences prefs = getSharedPreferences("OverlayPrefs", MODE_PRIVATE);
        
        // Values
        sliderSize.setValue(prefs.getFloat("text_size", 20f));
        sliderTextAlpha.setValue(prefs.getFloat("text_alpha", 1.0f));
        sliderBgAlpha.setValue(prefs.getFloat("bg_alpha", 0.5f));
        sliderPadding.setValue(prefs.getInt("padding", 12));
        
        // Toggles sync (Visual only, logic is in Service mostly)
        String fam = prefs.getString("font_family", "default");
        switch(fam) {
            case "mono": toggleGroupFont.check(R.id.btnFontMono); break;
            case "serif": toggleGroupFont.check(R.id.btnFontSerif); break;
            default: toggleGroupFont.check(R.id.btnFontDef); break;
        }
        
        int align = prefs.getInt("gravity", Gravity.CENTER);
        if (align == Gravity.START || align == (Gravity.START | Gravity.TOP)) toggleGroupAlign.check(R.id.btnAlignLeft);
        else if (align == Gravity.END || align == (Gravity.END | Gravity.TOP)) toggleGroupAlign.check(R.id.btnAlignRight);
        else toggleGroupAlign.check(R.id.btnAlignCenter);
        
        if (prefs.getBoolean("is_bold", false)) toggleGroupStyle.check(R.id.btnStyleBold);
        if (prefs.getBoolean("is_italic", false)) toggleGroupStyle.check(R.id.btnStyleItalic);
    }
    
    private void updatePermissionButtonState() {
        if (isNotificationServiceEnabled()) {
            btnPermissionNotification.setText("Media Access: ON");
            btnPermissionNotification.setEnabled(false);
            btnPermissionNotification.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.teal_700));
        } else {
            btnPermissionNotification.setText("Enable Media Access");
            btnPermissionNotification.setEnabled(true);
            btnPermissionNotification.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.accent_red));
        }
    }

    private boolean isNotificationServiceEnabled() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabledListeners.contains(getPackageName());
    }

    private void setupListeners() {
        // --- Core ---
        switchOverlay.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                if (checkOverlayPermission()) {
                    startServiceAction(FloatingLyricsService.ACTION_SHOW);
                    switchLock.setEnabled(true);
                    if (!isNotificationServiceEnabled()) Toast.makeText(this, "Enable Media Access for lyrics", Toast.LENGTH_SHORT).show();
                } else {
                    requestOverlayPermission();
                    switchOverlay.setChecked(false);
                }
            } else {
                startServiceAction(FloatingLyricsService.ACTION_HIDE);
                switchLock.setEnabled(false);
            }
        });
        
        switchLock.setOnCheckedChangeListener((v, isChecked) -> {
            startServiceAction(isChecked ? FloatingLyricsService.ACTION_LOCK : FloatingLyricsService.ACTION_UNLOCK);
            tvStatus.setText(isChecked ? R.string.status_locked : R.string.status_unlocked);
            tvStatus.setTextColor(isChecked ? Color.RED : Color.parseColor("#888888"));
        });
        
        btnPermissionNotification.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Exception e) { Toast.makeText(this, "Settings not found", Toast.LENGTH_SHORT).show(); }
        });

        // --- Typography ---
        toggleGroupFont.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                String fam = "default";
                if (checkedId == R.id.btnFontMono) fam = "mono";
                else if (checkedId == R.id.btnFontSerif) fam = "serif";
                else if (checkedId == R.id.btnFontDef) fam = "default"; // Explicitly default (sans)
                
                Intent i = new Intent(this, FloatingLyricsService.class);
                i.setAction(FloatingLyricsService.ACTION_UPDATE_FONT_FAMILY);
                i.putExtra(FloatingLyricsService.EXTRA_STRING_VAL, fam);
                startService(i);
            }
        });

        toggleGroupAlign.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int gravity = Gravity.CENTER;
                if (checkedId == R.id.btnAlignLeft) gravity = Gravity.START;
                else if (checkedId == R.id.btnAlignRight) gravity = Gravity.END;
                
                Intent i = new Intent(this, FloatingLyricsService.class);
                i.setAction(FloatingLyricsService.ACTION_UPDATE_ALIGN);
                i.putExtra(FloatingLyricsService.EXTRA_INT_VAL, gravity);
                startService(i);
            }
        });

        toggleGroupStyle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            // This triggers on uncheck too, which is what we want
            boolean bold = group.getCheckedButtonIds().contains(R.id.btnStyleBold);
            boolean italic = group.getCheckedButtonIds().contains(R.id.btnStyleItalic);
            
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_UPDATE_FONT_STYLE);
            i.putExtra(FloatingLyricsService.EXTRA_BOOL_BOLD, bold);
            i.putExtra(FloatingLyricsService.EXTRA_BOOL_ITALIC, italic);
            startService(i);
        });

        sliderSize.addOnChangeListener((slider, value, fromUser) -> {
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_UPDATE_SIZE);
            i.putExtra(FloatingLyricsService.EXTRA_VALUE, value);
            startService(i);
        });

        // --- Colors ---
        View.OnClickListener colorListener = v -> {
            int color = Color.WHITE;
            if (v.getId() == R.id.btnColorCyan) color = ContextCompat.getColor(this, R.color.accent_cyan);
            else if (v.getId() == R.id.btnColorGreen) color = ContextCompat.getColor(this, R.color.accent_green);
            else if (v.getId() == R.id.btnColorYellow) color = ContextCompat.getColor(this, R.color.accent_yellow);
            else if (v.getId() == R.id.btnColorOrange) color = ContextCompat.getColor(this, R.color.accent_orange);
            else if (v.getId() == R.id.btnColorPink) color = ContextCompat.getColor(this, R.color.accent_pink);
            else if (v.getId() == R.id.btnColorRed) color = ContextCompat.getColor(this, R.color.accent_red);
            
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_UPDATE_COLOR);
            i.putExtra(FloatingLyricsService.EXTRA_INT_VAL, color);
            startService(i);
        };
        
        btnColorWhite.setOnClickListener(colorListener);
        btnColorCyan.setOnClickListener(colorListener);
        btnColorGreen.setOnClickListener(colorListener);
        btnColorYellow.setOnClickListener(colorListener);
        btnColorOrange.setOnClickListener(colorListener);
        btnColorPink.setOnClickListener(colorListener);
        btnColorRed.setOnClickListener(colorListener);

        // --- Appearance ---
        sliderTextAlpha.addOnChangeListener((slider, value, fromUser) -> {
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_UPDATE_ALPHA_TEXT);
            i.putExtra(FloatingLyricsService.EXTRA_VALUE, value);
            startService(i);
        });
        
        sliderBgAlpha.addOnChangeListener((slider, value, fromUser) -> {
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_UPDATE_ALPHA_BG);
            i.putExtra(FloatingLyricsService.EXTRA_VALUE, value);
            startService(i);
        });

        View.OnClickListener styleListener = v -> {
            String style = "Classic";
            if (v.getId() == R.id.btnStyleMinimal) style = "Minimal";
            else if (v.getId() == R.id.btnStyleNeon) style = "Neon";
            else if (v.getId() == R.id.btnStyleRetro) style = "Retro";
            else if (v.getId() == R.id.btnStyleDay) style = "Day";
            else if (v.getId() == R.id.btnStyleKaraoke) style = "KTV";
            
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_UPDATE_STYLE_PRESET);
            i.putExtra(FloatingLyricsService.EXTRA_STRING_VAL, style);
            startService(i);
        };
        
        btnClassic.setOnClickListener(styleListener);
        btnMinimal.setOnClickListener(styleListener);
        btnNeon.setOnClickListener(styleListener);
        btnRetro.setOnClickListener(styleListener);
        btnDay.setOnClickListener(styleListener);
        btnKaraoke.setOnClickListener(styleListener);

        // --- Layout ---
        btnResetPos.setOnClickListener(v -> startServiceAction(FloatingLyricsService.ACTION_RESET_POS));
        
        sliderPadding.addOnChangeListener((slider, value, fromUser) -> {
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_UPDATE_PADDING);
            i.putExtra(FloatingLyricsService.EXTRA_INT_VAL, (int)value);
            startService(i);
        });
        
        View.OnClickListener moveListener = v -> {
            int dx = 0, dy = 0;
            int step = 10;
            if (v.getId() == R.id.btnMoveLeft) dx = -step;
            else if (v.getId() == R.id.btnMoveRight) dx = step;
            else if (v.getId() == R.id.btnMoveUp) dy = -step;
            else if (v.getId() == R.id.btnMoveDown) dy = step;
            
            Intent i = new Intent(this, FloatingLyricsService.class);
            i.setAction(FloatingLyricsService.ACTION_MOVE_POS);
            i.putExtra(FloatingLyricsService.EXTRA_DX, dx);
            i.putExtra(FloatingLyricsService.EXTRA_DY, dy);
            startService(i);
        };
        
        btnMoveLeft.setOnClickListener(moveListener);
        btnMoveRight.setOnClickListener(moveListener);
        btnMoveUp.setOnClickListener(moveListener);
        btnMoveDown.setOnClickListener(moveListener);
    }

    private void startServiceAction(String action) {
        if (switchOverlay.isChecked()) {
            Intent intent = new Intent(this, FloatingLyricsService.class);
            intent.setAction(action);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            Toast.makeText(this, R.string.permission_required_desc, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionButtonState();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkOverlayPermission()) {
                switchOverlay.setChecked(true);
            }
        }
    }
}