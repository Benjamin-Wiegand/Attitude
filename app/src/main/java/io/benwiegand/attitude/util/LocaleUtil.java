package io.benwiegand.attitude.util;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleUtil {

    /**
     * Gets an english context that can be used to resolve english string resources regardless of device language.
     * This should only be used for log messages, where they will be read by a developer (probably me) and not the user.
     */
    public static Context getDeveloperContext(Context appContext) {
        Configuration enConfig = new Configuration(appContext.getResources().getConfiguration());
        enConfig.setLocale(Locale.ENGLISH);
        return appContext
                .createConfigurationContext(enConfig);
    }

}
