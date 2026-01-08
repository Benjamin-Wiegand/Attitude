package io.benwiegand.attitude;

import static io.benwiegand.attitude.util.UiUtil.showDebugError;
import static io.benwiegand.attitude.util.UiUtil.showToast;
import static io.benwiegand.attitude.util.UiUtil.showUnexpectedError;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
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

    private final Handler handler = new Handler(Looper.getMainLooper());

    private AdbConnectionManager adbConnectionManager = null;

    private boolean skipDevelopmentSettingsCheck = false;

    private AdbService.ServiceBinder adbBinder = null;  // use getAdbBinder()
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "adb service connected");
            adbBinder = (AdbService.ServiceBinder) service;
            adbBinder.registerStatusCallback(AdbActivity.this::onAdbServiceStatusUpdate);

            runOnUiThread(() -> {
                TextView bindingText = findViewById(R.id.adb_service_binding_text);
                bindingText.setText(R.string.service_connection_bound_status);
                bindingText.setTextColor(Color.GREEN);
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "adb service disconnected");
            getAdbBinder().ifPresent(b -> b.unregisterStatusCallback(AdbActivity.this::onAdbServiceStatusUpdate));
            adbBinder = null;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                TextView bindingText = findViewById(R.id.adb_service_binding_text);
                bindingText.setText(R.string.service_connection_not_bound_status);
                bindingText.setTextColor(Color.RED);
            });
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

        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.console_ban_warning_title)
                .setMessage(R.string.adb_ban_warning_message)
                .setPositiveButton(R.string.got_it_button, null)
                .setCancelable(false)
                .show();

        findViewById(R.id.pair_button).setOnClickListener(v -> startPairing());

        findViewById(R.id.test_command_button).setOnClickListener(v -> runCommand(TEST_COMMAND));

        findViewById(R.id.grant_secure_settings_tool_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.grant_secure_settings_tool_name)
                .setMessage(R.string.grant_secure_settings_tool_message)
                .setPositiveButton(R.string.do_it_button, (d, i) -> {
                    runCommand("pm grant io.benwiegand.attitude android.permission.WRITE_SECURE_SETTINGS");
                    runCommand("settings put global adb_allowed_connection_time 0");
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show()
        );

        findViewById(R.id.remove_bloatware_icons_tool_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.remove_bloatware_icons_tool_name)
                .setMessage(R.string.remove_bloatware_icons_tool_message)
                .setPositiveButton(R.string.disable_apps_button, (d, i) -> {
                    runCommand("pm disable-user --user 0 com.oculus.explore");
                    runCommand("pm uninstall --user 0 com.oculus.socialplatform");
                    runCommand("pm disable-user --user 0 com.oculus.store");
                    runCommand("pm disable-user --user 0 com.oculus.browser");
                })
                .setNeutralButton(R.string.re_enable_apps_button, (d, i) -> {
                    runCommand("pm enable com.oculus.explore");
                    runCommand("pm install-existing com.oculus.socialplatform");
                    runCommand("pm enable com.oculus.store");
                    runCommand("pm enable com.oculus.browser");
                })
                .setNegativeButton("cancel", null)
                .show()
        );

        findViewById(R.id.crash_everything_tool_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.crash_everything_tool_name)
                .setMessage(R.string.crash_everything_tool_message)
                .setPositiveButton(R.string.do_it_button, (d, i) -> {
                    runCommand("am force-stop com.oculus.systemux && am force-stop com.oculus.vrshell");
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show()
        );

        findViewById(R.id.noclip_tool_button).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.noclip_tool_name)
                .setMessage(R.string.noclip_tool_message)
                .setPositiveButton(R.string.do_it_button, (d, i) -> {
                    runCommand("am force-stop com.oculus.guardian");
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show()
        );


        findViewById(R.id.adb_service_error_button).setOnClickListener(v -> {
            getAdbBinder().ifPresentOrElse(b -> {
                Throwable lastError = b.getLastError();
                updateLatestAdbServiceErrorText(lastError);
                if (lastError == null) {
                    showDebugError(this, "No error", "error is null");
                    return;
                }

                showDebugError(this, lastError);
            }, () -> {
                showDebugError(this, "AdbService not bound", "Can't get latest error, the service isn't bound.");
            });
        });


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

    // TODO: this creates more problems for localization than I initially (very naively) thought
    private void logStatus(String text) {
        // this is a very technical process, it's impossible to pretend it's not
        // technical users may appreciate seeing what is happening
        // especially if something goes wrong
        Log.d(TAG, "status: " + text);
        runOnUiThread(() -> {
            TextView statusText = findViewById(R.id.adb_log_text);
            statusText.setText(statusText.getText() + "\n" + text);

            // scroll down on next loop, after text renders
            handler.post(() -> {
                ScrollView adbLogScrollView = findViewById(R.id.adb_log_scrollview);
                adbLogScrollView.fullScroll(View.FOCUS_DOWN);
            });
        });
    }

    private void updateLatestAdbServiceErrorText(Throwable lastError) {
        TextView lastErrorText = findViewById(R.id.adb_service_error_text);
        // debug text
        lastErrorText.setText(lastError != null ? lastError.getClass().getSimpleName() : "null");
    }

    private void onAdbServiceStatusUpdate(AdbService.Status status) {
        TextView serviceStatusText = findViewById(R.id.adb_service_status_text);
        // debug text
        serviceStatusText.setText(status.name());
        int statusColor;
        if (status.isTerminal()) {
            statusColor = Color.RED;
        } else if (status == AdbService.Status.CONNECTED) {
            statusColor = Color.GREEN;
        } else {
            statusColor = Color.YELLOW;
        }
        serviceStatusText.setTextColor(statusColor);

        getAdbBinder().ifPresent(b ->
                updateLatestAdbServiceErrorText(b.getLastError()));
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
            showUnexpectedError(this, R.string.keystore_init_failure_title, t);
            logStatus("key init failure");
            Log.e(TAG, "exception during key initialization");
            //todo: options to retry or delete keystore + localized errors
        }
    }


    // note: you can still test this on a phone using split-screen mode
    private void startPairing() {

        boolean devOptions = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
        if (!devOptions && !skipDevelopmentSettingsCheck) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.enable_developer_options_title)
                    .setMessage(R.string.enable_developer_options_message)
                    .setPositiveButton(R.string.close_button, null)
                    .setNegativeButton(R.string.skip_developer_options_check_button, (d, i) -> {
                        // I believe PrivateQuest lets you go through wireless pairing via BLE
                        skipDevelopmentSettingsCheck = true;
                        startPairing();
                    })
                    .show();
            PackageUtil.launchAndroidSettings(this);
            return;
        }

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
                .setNegativeButton(R.string.cancel_button, (d, i) -> {
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
                        throw new RuntimeException("AdbConnectionManager.pair() returned false");   // not worth translating
                    }

                    logStatus("pairing successful");
                    runOnUiThread(() -> showToast(this, R.string.pairing_successful_title));

                } catch (Throwable t) {
                    // this is where all errors are actually returned by the library (as of v3.1.1)
                    Log.e(TAG, "pairing failed", t);
                    logStatus("pairing failed: " + t.getMessage());
                    // TODO: handle individual errors properly
                    runOnUiThread(() -> showUnexpectedError(this, R.string.pairing_failed_title, t));
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
                        runOnUiThread(() -> showDebugError(this, t));
                    })
                    .callMeWhenDone();
        }, () ->
                logStatus("AdbService not connected!"));
    }


}
