package io.benwiegand.attitude;

import static io.benwiegand.attitude.util.UiUtil.showError;
import static io.benwiegand.attitude.util.UiUtil.showToast;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import io.benwiegand.attitude.man.PrefMan;
import io.benwiegand.attitude.util.WiFiUtil;

// TODO: replace this whole activity with something more polished
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private PrefMan prefMan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefMan = new PrefMan(this);

        // show wifi name
        Spinner networkSpinner = findViewById(R.id.network_spinner);
        networkSpinner.setPrompt(prefMan.read(String.class, PrefMan.KEY_WIFI_NAME, "null"));

        // wifi list refresh
        findViewById(R.id.wifi_list_refresh_button).setOnClickListener(v -> refreshWifiNetworkList());

    }

    private void refreshWifiNetworkList() {
        String[] wifiSSIDs;
        try {
            if (!WiFiUtil.getRuntimeLocationPermission(this)) {
                Log.e(TAG, "failed to get runtime location permission");
                showError(this, "wifi refresh failure", "failed to get runtime location permission");
                return;
            }

            wifiSSIDs = WiFiUtil.getConfiguredWifiSSIDs(this);
        } catch (Throwable t) {
            Log.e(TAG, "failed to get wifi list", t);
            showError(this, t);
            return;
        }

        Spinner netSpinner = findViewById(R.id.network_spinner);
        ArrayAdapter<String> wifiSSIDListAdapter = new ArrayAdapter<>(this, R.layout.layout_spinner_item);
        wifiSSIDListAdapter.addAll(wifiSSIDs);
        netSpinner.setAdapter(wifiSSIDListAdapter);
        netSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "selected network: " + wifiSSIDs[position]);
                if (prefMan.writeNow(PrefMan.KEY_WIFI_NAME, wifiSSIDs[position])) {
                    showToast(SettingsActivity.this, "wifi saved");
                } else {
                    showError(SettingsActivity.this, "failed to save", "the shared preferences api returned false. are you out of storage or something?");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "nothing selected");
            }
        });
    }
}