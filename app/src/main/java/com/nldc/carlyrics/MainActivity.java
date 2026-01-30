package com.nldc.carlyrics;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_OVERLAY = 1001;
    private static final int REQUEST_CODE_NOTIFICATION = 1002;

    private TextView statusText;
    private Button btnOverlay;
    private Button btnNotification;
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        btnOverlay = findViewById(R.id.btn_grant_overlay);
        btnNotification = findViewById(R.id.btn_grant_notification);
        btnStart = findViewById(R.id.btn_start_service);

        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    private void setupButtons() {
        btnOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_OVERLAY);
                }
            }
        });

        btnNotification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivityForResult(intent, REQUEST_CODE_NOTIFICATION);
                }
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLyricsService();
            }
        });
    }

    private void checkPermissions() {
        boolean overlayGranted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayGranted = Settings.canDrawOverlays(this);
        } else {
            overlayGranted = true;
        }

        boolean notificationGranted = isNotificationServiceEnabled();

        updateUI(overlayGranted, notificationGranted);

        if (overlayGranted && notificationGranted) {
            startLyricsService();
        }
    }

    private boolean isNotificationServiceEnabled() {
        ComponentName cn = new ComponentName(this, MediaListenerService.class);
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    private void updateUI(boolean overlay, boolean notification) {
        btnOverlay.setEnabled(!overlay);
        btnOverlay.setText(overlay ? "Overlay Permission Granted" : "Grant Overlay Permission");

        btnNotification.setEnabled(!notification);
        btnNotification.setText(notification ? "Notification Permission Granted" : "Grant Notification Access");
        
        btnStart.setEnabled(overlay);
        
        StringBuilder status = new StringBuilder("Status:\n");
        status.append("Overlay: ").append(overlay ? "OK" : "Missing").append("\n");
        status.append("Notification Listener: ").append(notification ? "OK" : "Missing");
        statusText.setText(status.toString());
    }

    private void startLyricsService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, FloatingLyricsService.class);
        serviceIntent.setAction(FloatingLyricsService.ACTION_SHOW);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // Also ensure MediaListenerService is "started" (though the system binds it automatically)
        // This helps keeps the process alive
        try {
            Intent listenerIntent = new Intent(this, MediaListenerService.class);
            startService(listenerIntent);
        } catch (Exception e) {
            // Usually ignored for listener services
        }
    }
}