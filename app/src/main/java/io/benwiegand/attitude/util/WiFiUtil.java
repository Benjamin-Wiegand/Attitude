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
    private static List<WifiConfiguration> getWiFiConfigs(WifiManager fiMan) {
        List<WifiConfiguration> fiFigs = fiMan.getConfiguredNetworks(); // is my linter broken?
        Log.d(TAG, "num wifi configs: " + fiFigs.size());
        if (fiFigs.isEmpty()) {
            Log.e(TAG, "empty network list, location might not be enabled");
            throw new RuntimeException("WifiManager.getConfiguredNetworks() returned 0 elements. Did you enable location in settings? (If I had my way location wouldn't be required, but it literally doesn't work without it)");
        }

        return fiFigs;
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static boolean connectToWifi(Context c, String ssid) {
        Log.d(TAG, "connectToWifi()");

        WifiManager fiMan = getWiFiManager(c);
        List<WifiConfiguration> fiFigs = getWiFiConfigs(fiMan);

        for (WifiConfiguration fiConfig : fiFigs) {
            if (!fiConfig.SSID.equals(ssid)) continue;

            Log.v(TAG, "found target wifi network: " + ssid);
            Log.d(TAG, "bssid: " + fiConfig.BSSID);

            boolean result = fiMan.enableNetwork(fiConfig.networkId, true);
            Log.i(TAG, "enable network result: " + result);

            return result;
        }

        Log.e(TAG, "couldn't find network: " + ssid);
        throw new RuntimeException("couldn't find target wifi network in configured networks");
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public static String[] getConfiguredWifiSSIDs(Context c) {
        Log.d(TAG, "getConfiguredWifiSSIDs()");

        WifiManager fiMan = getWiFiManager(c);
        List<WifiConfiguration> fiFigs = getWiFiConfigs(fiMan);
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
                .setTitle("Location permission")
                .setMessage("The location permission is required to retrieve and connect to saved wifi networks.\n\nYour geographical location is never derived.")
                .setPositiveButton("open settings", (d, i) ->
                        openAppSettings(c))
                .setNegativeButton("cancel", null);

        dialogBuilder.show();
    }

    public static boolean getBackgroundLocationPermission(Context c) {
        return checkSelfPermission(c, ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestBackgroundLocationPermission(Context c) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(c)
                .setTitle("Background location permission")
                .setMessage("In order to automatically connect to wifi from the background (on boot, on wake, etc.) you need to set location access to \"Allow all the time\".\n\nYour geographical location is never derived.")
                .setPositiveButton("open settings", (d, i) ->
                        openAppSettings(c))
                .setNegativeButton("not now", null);

        dialogBuilder.show();
    }

}
