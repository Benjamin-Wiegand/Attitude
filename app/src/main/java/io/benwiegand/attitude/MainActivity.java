package io.benwiegand.attitude;

import static io.benwiegand.attitude.util.WiFiUtil.connectToWifi;
import static io.benwiegand.attitude.util.WiFiUtil.getBackgroundLocationPermission;
import static io.benwiegand.attitude.util.WiFiUtil.getRuntimeLocationPermission;
import static io.benwiegand.attitude.util.UiUtil.showError;
import static io.benwiegand.attitude.util.UiUtil.showToast;
import static io.benwiegand.attitude.util.WiFiUtil.requestBackgroundLocationPermission;
import static io.benwiegand.attitude.util.WiFiUtil.requestRuntimeLocationPermission;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import io.benwiegand.attitude.man.PrefMan;
import io.benwiegand.attitude.service.MuhNotificationService;
import io.benwiegand.attitude.util.PackageUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private PrefMan prefMan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefMan = new PrefMan(this);


        findViewById(R.id.wifi_connect_button)
                .setOnClickListener(v -> wifiAutoConnect());

        findViewById(R.id.settings_button)
                .setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.notification_panel_button)
                .setOnClickListener(v -> startActivity(new Intent(this, NotificationPanelActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));

        findViewById(R.id.notification_panel_button)
                .setOnLongClickListener(v -> {
                    startActivity(new Intent(this, NotificationPanelActivity.class)
                            .putExtra(NotificationPanelActivity.INTENT_EXTRA_SHOW_DEBUG, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return true;
                });

        findViewById(R.id.adb_button)
                .setOnClickListener(v ->
                        PackageUtil.launchActivity(this, AdbActivity.class));


        findViewById(R.id.android_settings_button)
                .setOnClickListener(v -> PackageUtil.launchAndroidSettings(this));

        findViewById(R.id.accessibility_settings_button)
                .setOnClickListener(v -> startActivity(PackageUtil.createAndroidSettingsIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        findViewById(R.id.notification_listener_settings_button)
                .setOnClickListener(v -> startActivity(
                        PackageUtil.createAndroidSettingsIntent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(this, MuhNotificationService.class).flattenToShortString())));

    }


    @SuppressLint("MissingPermission")  // it does actually check
    private void wifiAutoConnect() {
        try {
            String targetSSID = prefMan.read(String.class, PrefMan.KEY_WIFI_NAME, null);

            if (!getRuntimeLocationPermission(this)) {
                Log.e(TAG, "failed to get runtime location permission");
                requestRuntimeLocationPermission(this);
                return;
            } else if (!connectToWifi(this, targetSSID)) {
                showError(this, "wifi auto-connect failure", "connection result = false\n\nis wifi enabled?");
                return;
            } else {
                showToast(this, "wifi auto-connect success!");
            }

            if (!getBackgroundLocationPermission(this))
                requestBackgroundLocationPermission(this);

        } catch (Throwable t) {
            Log.e(TAG, "wifi auto-connect failure", t);
            showError(this, t);
        }
    }

}