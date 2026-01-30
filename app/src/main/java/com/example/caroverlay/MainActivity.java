package com.example.caroverlay;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private SwitchMaterial switchOverlay;
    private SwitchMaterial switchLock;
    private Button btnPermissionNotification;
    private Slider sliderSize;
    private TextView tvStatus;
    
    private Button btnCyan, btnGreen, btnYellow, btnWhite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
    }

    private void initViews() {
        switchOverlay = findViewById(R.id.switchOverlay);
        switchLock = findViewById(R.id.switchLock);
        btnPermissionNotification = findViewById(R.id.btnPermissionNotification);
        sliderSize = findViewById(R.id.sliderSize);
        tvStatus = findViewById(R.id.tvStatus);
        
        btnCyan = findViewById(R.id.btnColorCyan);
        btnGreen = findViewById(R.id.btnColorGreen);
        btnYellow = findViewById(R.id.btnColorYellow);
        btnWhite = findViewById(R.id.btnColorWhite);

        updatePermissionButtonState();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionButtonState();
    }

    private void updatePermissionButtonState() {
        if (isNotificationServiceEnabled()) {
            btnPermissionNotification.setText(R.string.permission_granted);
            btnPermissionNotification.setEnabled(false);
            btnPermissionNotification.setBackgroundColor(Color.DKGRAY);
        } else {
            btnPermissionNotification.setText(R.string.grant_notification_permission);
            btnPermissionNotification.setEnabled(true);
        }
    }

    private boolean isNotificationServiceEnabled() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabledListeners.contains(getPackageName());
    }

    private void setupListeners() {
        // Overlay Toggle
        switchOverlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkOverlayPermission()) {
                    startOverlayService();
                    switchLock.setEnabled(true);
                    
                    // Also make sure media listener is active?
                    if (!isNotificationServiceEnabled()) {
                        Toast.makeText(this, "Enable Notification Access for Lyrics", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    requestOverlayPermission();
                    switchOverlay.setChecked(false); // Reset until granted
                }
            } else {
                stopOverlayService();
                switchLock.setEnabled(false);
                switchLock.setChecked(false);
            }
        });
        
        // Notification Permission Button
        btnPermissionNotification.setOnClickListener(v -> {
             Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
             try {
                 startActivity(intent);
             } catch (Exception e) {
                 Toast.makeText(this, "Cannot open settings manually", Toast.LENGTH_SHORT).show();
             }
        });

        // Lock Toggle
        switchLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Intent intent = new Intent(MainActivity.this, FloatingLyricsService.class);
            if (isChecked) {
                intent.setAction(FloatingLyricsService.ACTION_LOCK);
                tvStatus.setText(R.string.status_locked);
            } else {
                intent.setAction(FloatingLyricsService.ACTION_UNLOCK);
                tvStatus.setText(R.string.status_unlocked);
            }
            startService(intent);
        });

        // Size Slider
        sliderSize.addOnChangeListener((slider, value, fromUser) -> {
            if (switchOverlay.isChecked()) {
                Intent intent = new Intent(MainActivity.this, FloatingLyricsService.class);
                intent.setAction(FloatingLyricsService.ACTION_UPDATE_SIZE);
                intent.putExtra(FloatingLyricsService.EXTRA_SIZE, value);
                startService(intent);
            }
        });

        // Color Buttons
        btnCyan.setOnClickListener(v -> sendColorUpdate(Color.parseColor("#00BCD4")));
        btnGreen.setOnClickListener(v -> sendColorUpdate(Color.parseColor("#00E676")));
        btnYellow.setOnClickListener(v -> sendColorUpdate(Color.parseColor("#FFEB3B")));
        btnWhite.setOnClickListener(v -> sendColorUpdate(Color.WHITE));
    }

    private void sendColorUpdate(int color) {
        if (switchOverlay.isChecked()) {
            Intent intent = new Intent(MainActivity.this, FloatingLyricsService.class);
            intent.setAction(FloatingLyricsService.ACTION_UPDATE_COLOR);
            intent.putExtra(FloatingLyricsService.EXTRA_COLOR, color);
            startService(intent);
        }
    }

    private void startOverlayService() {
        Intent serviceIntent = new Intent(this, FloatingLyricsService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopOverlayService() {
        Intent serviceIntent = new Intent(this, FloatingLyricsService.class);
        stopService(serviceIntent);
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
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkOverlayPermission()) {
                switchOverlay.setChecked(true); // Retry enabling
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
