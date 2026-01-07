package io.benwiegand.attitude;

import static io.benwiegand.attitude.util.UiUtil.showError;
import static io.benwiegand.attitude.util.UiUtil.showToast;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.benwiegand.attitude.adb.AdbConnectionManager;
import io.benwiegand.attitude.adb.QdAdbShell;
import io.benwiegand.attitude.man.KeyMan;
import io.benwiegand.attitude.service.AdbService;
import io.benwiegand.attitude.util.KeyUtil;
import io.benwiegand.attitude.util.PackageUtil;
import io.github.muntashirakon.adb.android.AdbMdns;

public class AdbActivity extends AppCompatActivity {
    private static final String TAG = AdbActivity.class.getSimpleName();

    private static final long COMMAND_QUEUE_TIMEOUT = 10000;
    private static final long COMMAND_EXECUTION_TIMEOUT = 5000;

    private static final String TEST_COMMAND = "id -u";

    private AdbConnectionManager adbConnectionManager = null;

    private AdbService.ServiceBinder adbBinder = null;  // use getAdbBinder()
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "adb service connected");
            logStatus("AdbService connected");
            adbBinder = (AdbService.ServiceBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "adb service disconnected");
            logStatus("AdbService disconnected");
            adbBinder = null;
        }
    };

    private Optional<AdbService.ServiceBinder> getAdbBinder() {
        return Optional.ofNullable(adbBinder);
    }


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

        //todo: CONSOLE BAN WARNING

        // todo: check INTERNET permission, previous versions of the app didn't require it

        findViewById(R.id.pair_button).setOnClickListener(v -> startPairing());

        findViewById(R.id.test_command_button).setOnClickListener(v -> runCommand(TEST_COMMAND));

        findViewById(R.id.grant_secure_settings_tool_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("allow wireless adb auto-start")
                .setMessage("grants Attitude the WRITE_SECURE_SETTINGS permission, allowing it to enable wireless debugging as needed.\n\nalso sets adb pairing connections to never expire, so you don't have to pair wireless debugging again.")
                .setPositiveButton("do it", (d, i) -> {
                    runCommand("pm grant io.benwiegand.attitude android.permission.WRITE_SECURE_SETTINGS");
                    runCommand("settings put global adb_allowed_connection_time 0");
                })
                .setNegativeButton("cancel", null)
                .show()
        );

        findViewById(R.id.remove_bloatware_icons_tool_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("bloatware icon removal")
                .setMessage("To remove the icons:\n - use \"disable apps\"\n - reboot or use the \"crash everything\" tool\n - add apps of your choosing into the now empty space\n - optionally, use \"enable apps\" if you still want to launch those apps elsewhere (you may have to reboot again after this)\n\nNote: after re-enabling the apps and rebooting again, any unused space on the taskbar will be re-populated with the bloatware app icons.")
                .setPositiveButton("disable apps", (d, i) -> {
                    runCommand("pm disable-user --user 0 com.oculus.explore");
                    runCommand("pm uninstall --user 0 com.oculus.socialplatform");
                    runCommand("pm disable-user --user 0 com.oculus.store");
                    runCommand("pm disable-user --user 0 com.oculus.browser");
                })
                .setNeutralButton("re-enable apps", (d, i) -> {
                    runCommand("pm enable com.oculus.explore");
                    runCommand("pm install-existing com.oculus.socialplatform");
                    runCommand("pm enable com.oculus.store");
                    runCommand("pm enable com.oculus.browser");
                })
                .setNegativeButton("cancel", null)
                .show()
        );

        findViewById(R.id.crash_everything_tool_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("crash everything")
                .setMessage("crashes vrshell and systemux to be restarted. this takes the entire ui and all open apps with it.\n\nuseful as a very fast soft reboot for the UI, but not as useful as an actual reboot.")
                .setPositiveButton("do it", (d, i) -> {
                    runCommand("am force-stop com.oculus.systemux && am force-stop com.oculus.vrshell");
                })
                .setNegativeButton("cancel", null)
                .show()
        );

        findViewById(R.id.noclip_tool_button); //todo

        bindService(new Intent(this, AdbService.class), serviceConnection, BIND_AUTO_CREATE);

        // this will take quite a while the first time
        // the adb operations row will be unhidden after it completes
        new Thread(this::initKeys)
                .start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
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

    private void runCommand(String command) {
        getAdbBinder().ifPresentOrElse(b -> {
            logStatus("executing: " + command);
            b.executeSafeCommand(command, COMMAND_QUEUE_TIMEOUT, COMMAND_EXECUTION_TIMEOUT)
                    .doOnResult(r -> {
                        logStatus("exit code: " + r.returnCode());
                        if (r.out().length == 0) return;
                        String out = new String(r.out()).stripTrailing();
                        logStatus("output: \n" + out + "\n===========");
                    })
                    .doOnError(t -> {
                        if (t instanceof QdAdbShell.ExecutionException e) {
                            if (e.getCause() != null)
                                logStatus("problem during execution: " + e.getCause().getMessage());
                            if (e.isCmdSentOff())
                                logStatus("command may still have been executed");
                        } else {
                            logStatus("execution failed: " + t.getMessage());
                        }
                        runOnUiThread(() -> showError(this, t));
                    })
                    .callMeWhenDone();
        }, () ->
                logStatus("AdbService not connected!"));
    }


}
