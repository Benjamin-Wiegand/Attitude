package io.benwiegand.attitude.util;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

public class PackageUtil {

    public static void openAppSettings(Context c) {
        c.startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()))
                        .setPackage("com.android.settings")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        );
    }

    public static void launchActivity(Context context, Class<?> localClass) {
        startActivity(context, new Intent(context, localClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null);
    }

    public static void launchActivity(Context context, ComponentName component) {
        startActivity(context, new Intent(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(component), null);
    }

    public static Intent createAndroidSettingsIntent(String action) {
        return new Intent(action)
                .setPackage("com.android.settings")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    }
}
