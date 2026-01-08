package io.benwiegand.attitude.util;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static androidx.core.content.ContextCompat.checkSelfPermission;
import static androidx.core.content.ContextCompat.getSystemService;

import static io.benwiegand.attitude.util.PackageUtil.openAppSettings;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;

import java.util.List;

import io.benwiegand.attitude.R;
import io.benwiegand.attitude.exception.UserFriendlyException;

public class WiFiUtil {
    private static final String TAG = WiFiUtil.class.getSimpleName();

    private static WifiManager getWiFiManager(Context c) {
        WifiManager fiMan = getSystemService(c, WifiManager.class);
        if (fiMan == null) {
            Log.wtf(TAG, "WifiManager not supported");
            throw new UnsupportedOperationException("WifiManager service not supported/allowed by your OS");
        }
        return fiMan;
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private static List<WifiConfiguration> getWiFiConfigs(Context c, WifiManager fiMan) throws UserFriendlyException {
        List<WifiConfiguration> fiFigs = fiMan.getConfiguredNetworks(); // is my linter broken?
        Log.d(TAG, "num wifi configs: " + fiFigs.size());
        if (fiFigs.isEmpty()) {
            Log.e(TAG, "empty network list, location might not be enabled");
            throw new UserFriendlyException(c, R.string.zero_configured_wifi_networks_enable_location_error_message);
        }

        return fiFigs;
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static boolean connectToWifi(Context c, String ssid) throws UserFriendlyException {
        Log.d(TAG, "connectToWifi()");

        WifiManager fiMan = getWiFiManager(c);
        List<WifiConfiguration> fiFigs = getWiFiConfigs(c, fiMan);

        for (WifiConfiguration fiConfig : fiFigs) {
            if (!fiConfig.SSID.equals(ssid)) continue;

            Log.v(TAG, "found target wifi network: " + ssid);
            Log.d(TAG, "bssid: " + fiConfig.BSSID);

            boolean result = fiMan.enableNetwork(fiConfig.networkId, true);
            Log.i(TAG, "enable network result: " + result);

            return result;
        }

        Log.e(TAG, "couldn't find network: " + ssid);
        throw new UserFriendlyException(c, R.string.failed_to_find_target_wifi_error_message);
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static String[] getConfiguredWifiSSIDs(Context c) throws UserFriendlyException {
        Log.d(TAG, "getConfiguredWifiSSIDs()");

        WifiManager fiMan = getWiFiManager(c);
        List<WifiConfiguration> fiFigs = getWiFiConfigs(c, fiMan);
        String[] ssids = new String[fiFigs.size()];

        int i = 0;
        for (WifiConfiguration fiConfig : fiFigs) {
            ssids[i++] = fiConfig.SSID;
        }

        return ssids;
    }


    public static boolean getRuntimeLocationPermission(Context c) {
        return checkSelfPermission(c, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestRuntimeLocationPermission(Context c) {
        // always show rationale since it's a potentially confusing requirement
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(c)
                .setTitle(R.string.location_permission_rationale_title)
                .setMessage(R.string.location_permission_rationale_message)
                .setPositiveButton(R.string.open_settings_button, (d, i) ->
                        openAppSettings(c))
                .setNegativeButton(R.string.cancel_button, null);

        dialogBuilder.show();
    }

    public static boolean getBackgroundLocationPermission(Context c) {
        return checkSelfPermission(c, ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestBackgroundLocationPermission(Context c) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(c)
                .setTitle(R.string.background_location_permission_rationale_title)
                .setMessage(R.string.background_location_permission_rationale_message)
                .setPositiveButton(R.string.open_settings_button, (d, i) ->
                        openAppSettings(c))
                .setNegativeButton(R.string.not_now_button, null);

        dialogBuilder.show();
    }

}
