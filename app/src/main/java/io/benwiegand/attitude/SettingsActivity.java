package io.benwiegand.attitude;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Optional;

import io.benwiegand.attitude.makeshiftbind.MakeshiftServiceConnection;
import io.benwiegand.attitude.man.PrefMan;
import io.benwiegand.attitude.service.MuhAccessibilityService;
import io.benwiegand.attitude.util.WiFiUtil;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_view, new SettingsFragment())
                .commit();

    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private final Handler handler = new Handler(Looper.getMainLooper());

        private MuhAccessibilityService.ServiceBinder accessibilityServiceBinder = null;    // use getAccessibilityBinder()
        private final MakeshiftServiceConnection accessibilityServiceConnection = new MakeshiftServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "accessibility service connected");
                accessibilityServiceBinder = (MuhAccessibilityService.ServiceBinder) service;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.w(TAG, "accessibility service disconnected");
                accessibilityServiceBinder = null;
            }
        };

        private Optional<MuhAccessibilityService.ServiceBinder> getAccessibilityBinder() {
            return Optional.ofNullable(accessibilityServiceBinder);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            MakeshiftServiceConnection.bindService(requireContext(), new ComponentName(requireContext(), MuhAccessibilityService.class), accessibilityServiceConnection);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            // reload here for good measure
            getAccessibilityBinder().ifPresent(MuhAccessibilityService.ServiceBinder::reloadSettings);
            accessibilityServiceConnection.destroy();
        }


        private boolean requestLocationPermissions() {
            if (!WiFiUtil.getRuntimeLocationPermission(getContext())) {
                WiFiUtil.requestRuntimeLocationPermission(getActivity());
                return false;
            }

            if (WiFiUtil.getBackgroundLocationPermission(getContext())) return true;
            WiFiUtil.requestBackgroundLocationPermission(getActivity());
            return true;    // allow the user to test in foreground regardless
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference.OnPreferenceChangeListener locationPermissionOnEnable = (p, newValue) -> {
                if (!Boolean.TRUE.equals(newValue)) return true;    // only show on enable
                return requestLocationPermissions();
            };

            Preference.OnPreferenceClickListener locationPermissionOnClick = (p) -> !requestLocationPermissions();

            // always show so the user doesn't think something is broken
            Preference wifiConnectOnBoot = findPreference(PrefMan.KEY_WIFI_CONNECT_ON_BOOT);
            Preference wifiConnectOnWake = findPreference(PrefMan.KEY_WIFI_CONNECT_ON_WAKE);
            Preference wifiName = findPreference(PrefMan.KEY_WIFI_NAME);
            assert wifiConnectOnBoot != null;
            assert wifiConnectOnWake != null;
            assert wifiName != null;
            wifiConnectOnBoot.setOnPreferenceChangeListener(locationPermissionOnEnable);
            wifiConnectOnWake.setOnPreferenceChangeListener(locationPermissionOnEnable);
            wifiName.setOnPreferenceClickListener(locationPermissionOnClick);


            // the accessibility service builds lookup tables which need to be rebuilt when those settings change
            Preference.OnPreferenceChangeListener accessibilityReload = (p, v) -> {
                // delay reload so it doesn't use the old settings
                handler.post(() -> getAccessibilityBinder().ifPresent(MuhAccessibilityService.ServiceBinder::reloadSettings));
                return true;
            };

            PreferenceCategory popupBlockCategory = findPreference("block_popup_category");
            PreferenceCategory remapCategory = findPreference("remap_category");
            assert popupBlockCategory != null;
            assert remapCategory != null;

            for (int j = 0; j < popupBlockCategory.getPreferenceCount(); j++)
                popupBlockCategory.getPreference(j).setOnPreferenceChangeListener(accessibilityReload);
            for (int j = 0; j < remapCategory.getPreferenceCount(); j++)
                remapCategory.getPreference(j).setOnPreferenceChangeListener(accessibilityReload);

        }
    }
}