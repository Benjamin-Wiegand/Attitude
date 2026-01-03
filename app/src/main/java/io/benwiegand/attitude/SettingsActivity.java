package io.benwiegand.attitude;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import io.benwiegand.attitude.man.PrefMan;
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
            if (wifiConnectOnBoot != null) wifiConnectOnBoot.setOnPreferenceChangeListener(locationPermissionOnEnable);
            if (wifiConnectOnWake != null) wifiConnectOnWake.setOnPreferenceChangeListener(locationPermissionOnEnable);
            if (wifiName != null) wifiName.setOnPreferenceClickListener(locationPermissionOnClick);

        }
    }
}