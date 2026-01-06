package io.benwiegand.attitude;

import static io.benwiegand.attitude.util.UiUtil.showError;
import static io.benwiegand.attitude.util.UiUtil.showToast;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.concurrent.atomic.AtomicInteger;

import io.benwiegand.attitude.adb.AdbConnectionManager;
import io.benwiegand.attitude.man.KeyMan;
import io.benwiegand.attitude.service.AdbService;
import io.benwiegand.attitude.util.KeyUtil;
import io.benwiegand.attitude.util.PackageUtil;
import io.github.muntashirakon.adb.android.AdbMdns;

public class AdbActivity extends AppCompatActivity {
    private static final String TAG = AdbActivity.class.getSimpleName();

    private AdbConnectionManager adbConnectionManager = null;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_adb);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // todo: check INTERNET permission, previous versions of the app didn't require it

        findViewById(R.id.pair_button).setOnClickListener(v -> startPairing());

        findViewById(R.id.connect_button).setOnClickListener(v -> {
            startService(new Intent(this, AdbService.class));
        });

        // this will take quite a while the first time
        // the adb operations row will be unhidden after it completes
        new Thread(this::initKeys)
                .start();
    }

    private void logStatus(String text) {
        // this is a very technical process, it's impossible to pretend it's not
        // technical users may appreciate seeing what is happening
        // especially if something goes wrong
        Log.d(TAG, "status: " + text);
        runOnUiThread(() -> {
            TextView statusText = findViewById(R.id.adb_status_text);
            statusText.setText(statusText.getText() + "\n" + text);
        });
    }

    private void initKeys() {
        if (adbConnectionManager != null) {
            Log.i(TAG, "keys already initialized");
            return;
        }

        try {

            logStatus("init keystore");
            KeyMan keyMan = new KeyMan(this);
            keyMan.loadKeystore();

            if (keyMan.hasKeypair(KeyMan.ADB_KEY_ALIAS)) {
                logStatus("found existing adb keypair");
            } else {
                logStatus("no existing adb keypair, generating one (this may take a while)");
                keyMan.initKeypair(KeyMan.ADB_KEY_ALIAS);
            }

            PrivateKey privateKey = keyMan.getPrivateKey(KeyMan.ADB_KEY_ALIAS);
            if (privateKey == null) {
                logStatus("failed to load private key");
                throw new RuntimeException("failed to load private key");
            }

            String keyAlgo = privateKey.getAlgorithm();
            if (privateKey instanceof RSAPrivateKey rsaKey) {
                keyAlgo = rsaKey.getModulus().bitLength() + "-bit " + keyAlgo;
            }
            logStatus(" - " + privateKey.getFormat() + " private key: " + keyAlgo);

            Certificate certificate = keyMan.getCertificate(KeyMan.ADB_KEY_ALIAS);
            if (certificate == null) {
                logStatus("failed to load certificate");
                throw new RuntimeException("failed to load certificate");
            }

            logStatus(" - " + certificate.getPublicKey().getFormat() + " public key: " + certificate.getPublicKey().getAlgorithm());
            logStatus(" - certificate: " + certificate.getType());

            byte[] fingerprint = KeyUtil.calculateCertificateFingerprint(certificate);
            logStatus("certificate fingerprint: " + KeyUtil.hexOf(fingerprint, ":", true));

            // abort if the user has closed the activity at this point
            // avoids race conditions with saving
            if (isFinishing() || isDestroyed()) {
                Log.e(TAG, "activity has been closed");
                return;
            }

            if (keyMan.isModified()) {
                logStatus("keystore has been modified, saving");
                keyMan.saveKeystore();
            }

            logStatus("key init complete");
            adbConnectionManager = new AdbConnectionManager(this, privateKey, certificate);

            runOnUiThread(() ->
                    findViewById(R.id.adb_operations).setVisibility(View.VISIBLE));
        } catch (Throwable t) {
            showError(this, t);
            logStatus("key init failure");
            Log.e(TAG, "exception during key initialization");
            //todo: options to retry or delete keystore
        }
    }


    // note: you can still test this on a phone using split-screen mode
    private void startPairing() {

        // TODO: check if enabled, tell user how to enable
        startActivity(PackageUtil.createAndroidSettingsIntent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));


        View dialogRoot = getLayoutInflater().inflate(R.layout.layout_adb_pairing_dialog, null, false);
        View waitForPairingIndicator = dialogRoot.findViewById(R.id.waiting_for_pairing_indicator);
        View pairingCodeEntryArea = dialogRoot.findViewById(R.id.pairing_code_entry_area);
        EditText pairingCodeInput = dialogRoot.findViewById(R.id.pairing_code_input);
        ImageButton pairingCodeSubmitButton = dialogRoot.findViewById(R.id.pairing_code_submit_button);


        AtomicInteger adbPairingPort = new AtomicInteger(-1);

        // TODO: this can still leak probably but I don't feel like fixing it right now
        AdbMdns pairingDiscovery = new AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING, (host, port) -> {
            logStatus("pairing service discovered at: " + host + " port " + port);
            adbPairingPort.set(port);
            runOnUiThread(() -> {
                waitForPairingIndicator.setVisibility(View.GONE);
                pairingCodeEntryArea.setVisibility(View.VISIBLE);
            });
        });
        pairingDiscovery.start();
        logStatus("pairing service discovery started");


        AlertDialog pairingCodeEntryDialog = new AlertDialog.Builder(this)
                .setView(dialogRoot)
                .setCancelable(false)
                .setNegativeButton("cancel", (d, i) -> {
                    logStatus("cancelling pairing");
                    pairingDiscovery.stop();
                })
                .show();


        pairingCodeSubmitButton.setOnClickListener(v -> {
            assert adbPairingPort.get() != -1;  // should be gated by view visibility
            if (adbPairingPort.get() == -1) return;

            logStatus("attempting pairing...");
            pairingCodeEntryDialog.dismiss();
            pairingDiscovery.stop();

            // not doing futures just for this one thing
            new Thread(() -> {
                String pairingCode = String.valueOf(pairingCodeInput.getText());

                try {
                    if (!adbConnectionManager.pair(adbPairingPort.get(), pairingCode)) {
                        // this is not returned by the library, but there's a note that says it should be added (as of v3.1.1).
                        // not sure what that means, but transfer this confusion to the user with a '?'.
                        logStatus("AdbConnectionManager.pair() returned false. bad pairing code or something went wrong?");
                        runOnUiThread(() ->
                                showError(this, "pairing failed", "bad pairing code or something went wrong?"));
                        return;
                    }

                    logStatus("pairing successful");
                    runOnUiThread(() -> showToast(this, "paring successful"));

                } catch (Throwable t) {
                    // this is where all errors are actually returned by the library (as of v3.1.1)
                    Log.e(TAG, "pairing failed", t);
                    logStatus("pairing failed: " + t.getMessage());
                    runOnUiThread(() -> showError(this, t));
                }
            }).start();
        });

    }

}
