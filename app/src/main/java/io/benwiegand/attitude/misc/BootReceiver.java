package io.benwiegand.attitude.misc;

import static io.benwiegand.attitude.util.WiFiUtil.connectToWifi;
import static io.benwiegand.attitude.util.WiFiUtil.getRuntimeLocationPermission;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Set;

import io.benwiegand.attitude.man.PrefMan;


public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = BootReceiver.class.getSimpleName();

    private static final Set<String> ACCEPTED_INTENT_ACTIONS = Set.of(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON"
    );


    @SuppressLint("MissingPermission")  // it does actually check
    private void wifiAutoConnect(Context context, PrefMan prefMan) {
        Log.v(TAG, "performing wifi auto-connect");

        String targetSSID = prefMan.read(String.class, PrefMan.KEY_WIFI_NAME, null);
        if (targetSSID == null) {
            Log.e(TAG, "no configured wifi network");
            return;
        }

        try {
            if (!getRuntimeLocationPermission(context)) {
                Log.e(TAG, "failed to get runtime location permission");
            } else if (!connectToWifi(context, targetSSID)) {
                Log.e(TAG, "wifi connection result = false");
            } else {
                Log.v(TAG, "wifi connecting!");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Wifi auto-connect failed", t);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        // ensure malicious actors aren't helping us connect to wifi.
        if (!ACCEPTED_INTENT_ACTIONS.contains(intent.getAction())) return;


        Log.d(TAG, "Boot received!!!!");
        PrefMan prefMan = new PrefMan(context);

        if (prefMan.read(Boolean.class, PrefMan.KEY_WIFI_CONNECT_ON_BOOT, false))
            wifiAutoConnect(context, prefMan);

    }
}
