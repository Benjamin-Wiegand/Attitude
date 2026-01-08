package io.benwiegand.attitude.preference;

import static io.benwiegand.attitude.util.UiUtil.showError;
import static io.benwiegand.attitude.util.UiUtil.showUnexpectedError;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import io.benwiegand.attitude.R;
import io.benwiegand.attitude.exception.UserFriendlyException;
import io.benwiegand.attitude.util.WiFiUtil;

public class WifiNetworkPreference extends ListPreference {
    private static final String TAG = WifiNetworkPreference.class.getSimpleName();

    private boolean init = false;


    private void init() {
        if (init) return;
        init = true;

        setSummaryProvider(p -> {
            // get value directly instead of finding index
            if (getValue() == null) return getContext().getString(R.string.preference_not_set_summary);
            return getValue();
        });
    }


    public WifiNetworkPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public WifiNetworkPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public WifiNetworkPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WifiNetworkPreference(@NonNull Context context) {
        super(context);
        init();
    }


    @SuppressLint("MissingPermission")  // it does check
    @Override
    protected void onClick() {
        Log.i(TAG, "getting network list");
        try {
            if (!WiFiUtil.getRuntimeLocationPermission(getContext())) return;   // should be handled by preference fragment

            String[] wifiSSIDs = WiFiUtil.getConfiguredWifiSSIDs(getContext());
            setEntryValues(wifiSSIDs);
            setEntries(wifiSSIDs);

        } catch (UserFriendlyException e) {
            Log.e(TAG, "failed to get wifi list", e);
            showError(getContext(), R.string.wifi_list_error_title, e);
        } catch (RuntimeException e) {
            Log.e(TAG, "failed to get wifi list", e);
            showUnexpectedError(getContext(), R.string.wifi_list_error_title, e);
        }

        super.onClick();
    }

}
