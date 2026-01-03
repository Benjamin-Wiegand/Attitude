package io.benwiegand.attitude.util;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static androidx.core.app.ActivityCompat.requestPermissions;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static androidx.core.content.ContextCompat.getSystemService;

import android.Manifest;
import android.app.Activity;
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

    public static boolean ensureBackgroundLocationPermission(Context c) {
        if (checkSelfPermission(c, ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) return true;

        if (c instanceof Activity a) {
            if (shouldShowRequestPermissionRationale(a, ACCESS_BACKGROUND_LOCATION)) {
                new AlertDialog.Builder(a)
                        .setTitle("need background location for wifi auto-connect")
                        .setMessage("Location is never actually derived, it's just a WifiManager API requirement.\n\nYou may need to go to settings to explicitly grant background location access. Without this, wifi auto-connect won't work on boot.")
                        .setPositiveButton("K", (d, i) -> {
                            requestPermissions(a, new String[]{ACCESS_BACKGROUND_LOCATION}, 0);

                        })
                        .setNegativeButton("no", null)
                        .show();
            } else {
                requestPermissions(a, new String[]{ACCESS_BACKGROUND_LOCATION}, 0);
            }
        }
        return false;
    }

    public static boolean getRuntimeLocationPermission(Context c) {
        if (checkSelfPermission(c, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) return true;

        if (c instanceof Activity a) {
            if (shouldShowRequestPermissionRationale(a, ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(a)
                        .setTitle("need location for wifi connect")
                        .setMessage("Location is never actually derived, it's just a WifiManager API requirement")
                        .setPositiveButton("K", (d, i) -> {
                            requestPermissions(a, new String[]{ACCESS_FINE_LOCATION}, 0);

                        })
                        .setNegativeButton("no", null)
                        .show();
            } else {
                requestPermissions(a, new String[]{ACCESS_FINE_LOCATION}, 0);
            }
        }
        return false;
    }

}
