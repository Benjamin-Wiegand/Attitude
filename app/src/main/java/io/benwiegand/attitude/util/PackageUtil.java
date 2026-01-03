package io.benwiegand.attitude.util;

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
}
