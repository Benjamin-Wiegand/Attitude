package io.benwiegand.attitude.util;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

public class PackageUtil {
    private static final String TAG = PackageUtil.class.getSimpleName();

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

    public static Intent createAndroidSettingsIntent(String action, Uri uri) {
        return new Intent(action, uri)
                .setPackage("com.android.settings")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static void openAppSettings(Context c) {
        c.startActivity(
                createAndroidSettingsIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName())));
    }

    public static void launchAndroidSettings(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN, Uri.parse("package:com.android.settings"))
                    .setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings"))
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Log.e(TAG, "failed to directly launch settings, using workaround");
            c.startActivity(
                    createAndroidSettingsIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.android.settings")));
        }
    }
}
