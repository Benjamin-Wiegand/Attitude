package io.benwiegand.attitude.service;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;

import io.benwiegand.attitude.adb.AdbConnectionManager;
import io.benwiegand.attitude.man.KeyMan;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

public class AdbService extends Service {
    private static final String TAG = AdbService.class.getSimpleName();

    public enum Status {
        NEED_SETUP,
        NEED_WIRELESS_DEBUGGING,
        NEED_PAIRING_AGAIN,
        PROBLEM,
        BUSY,
        CONNECTED;

        public boolean isTerminal() {
            return switch (this) {
                case NEED_SETUP, PROBLEM, NEED_PAIRING_AGAIN, NEED_WIRELESS_DEBUGGING -> true;
                case BUSY, CONNECTED -> false;
            };
        }

    }

    private final Binder binder = new ServiceBinder();

    private Status currentStatus = Status.BUSY;

    private AdbConnectionManager adbConnectionManager = null;


    private boolean wirelessDebuggingForceEnabled = false;


    private Thread connectionThread;
    private boolean stayAlive = true;
    private boolean dead = false;

    private Throwable error;


    private void updateStatus(Status status) {
        Log.i(TAG, "status: " + status);
        currentStatus = status;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "adb service created");

        // many long running operations in init
        connectionThread = new Thread(this::connectionLoop);
        connectionThread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "adb service destroyed");
        stayAlive = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void connectionLoop() {
        dead = false;
        try {

            while (stayAlive) {

                if (adbConnectionManager == null || !adbConnectionManager.isConnected()) {
                    adbConnectionManager = null;
                    updateStatus(Status.BUSY);
                    //todo: cooldown

                    if (!initKeys()) return;
                    tryEnableWirelessDebugging();
                    connect();

                    if (currentStatus.isTerminal()) {
                        Log.e(TAG, "connection init failed, exiting loop");
                        return;
                    }
                    continue;
                }


                //TODO
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "unexpected exception in connection loop", e);
            error = e;
            updateStatus(Status.PROBLEM);
        } finally {
            // ensure coherent status
            if (!currentStatus.isTerminal()) {
                Log.wtf(TAG, "connection loop terminating while status is still " + currentStatus);
                error = new RuntimeException("connection loop terminated unexpectedly. is this a bug?");
                updateStatus(Status.PROBLEM);
            }

            Log.i(TAG, "connection loop death");
            dead = true;

            //TODO: remove
            stopSelf();

            //todo: restart after a little while
        }
    }

    private void tryEnableWirelessDebugging() {
        ContentResolver cr = getContentResolver();
        wirelessDebuggingForceEnabled = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1;
        if (wirelessDebuggingForceEnabled) return;
        Log.v(TAG, "wireless debugging is not enabled");

        try {
            // wireless debugging can be kick-started automatically if WRITE_SECURE_SETTINGS is granted
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1);
            wirelessDebuggingForceEnabled = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1;
        } catch (SecurityException e) {
            // does not necessarily mean wireless debugging wasn't manually enabled
            Log.w(TAG, "failed to set wireless debugging", e);
        }
    }

    private boolean initKeys() {
        assert adbConnectionManager == null;
        Log.i(TAG, "init keys");
        try {

            KeyMan keyMan = new KeyMan(this);
            keyMan.loadKeystore();

            if (!keyMan.hasKeypair(KeyMan.ADB_KEY_ALIAS))
                throw new RuntimeException("no keypair yet, setup must be completed");

            PrivateKey privateKey = keyMan.getPrivateKey(KeyMan.ADB_KEY_ALIAS);
            if (privateKey == null)
                throw new RuntimeException("failed to load private key");

            String keyAlgo = privateKey.getAlgorithm();
            if (privateKey instanceof RSAPrivateKey rsaKey)
                keyAlgo = rsaKey.getModulus().bitLength() + "-bit " + keyAlgo;

            Certificate certificate = keyMan.getCertificate(KeyMan.ADB_KEY_ALIAS);
            if (certificate == null)
                throw new RuntimeException("failed to load certificate");

            Log.d(TAG, "using " + privateKey.getFormat() + " encoded private key: " + keyAlgo);
            Log.d(TAG, " - " + certificate.getPublicKey().getFormat() + " encoded public key: " + certificate.getPublicKey().getAlgorithm());
            Log.d(TAG, " - certificate: " + certificate.getType());

            // should not be modified at this point
            assert !keyMan.isModified();

            Log.i(TAG, "key init complete");
            adbConnectionManager = new AdbConnectionManager(this, privateKey, certificate);
            return true;

        } catch (Throwable t) {
            Log.e(TAG, "problem during key init: ", t);
            updateStatus(Status.NEED_SETUP);
            error = t;
            return false;
        }
    }

    private void connect() {
        assert adbConnectionManager != null;
        assert !adbConnectionManager.isConnected();


        Log.i(TAG, "connecting");
        try {
            if (!adbConnectionManager.connectTls(this, 10000)) {
                // documentation is unclear as to what problems actually cause this
                throw new RuntimeException("AdbConnectionManager.connectTls() returned false");
            }

            if (adbConnectionManager.isConnected()) {
                Log.i(TAG, "connected to adb");
                updateStatus(Status.CONNECTED);
            } else {
                Log.e(TAG, "adb immediately disconnected");
                // soft error
            }
        } catch (AdbPairingRequiredException e) {
            error = e;
            updateStatus(Status.NEED_PAIRING_AGAIN);
        } catch (IOException e) {
            Log.e(TAG, "failed to connect", e);
            error = e;
            // soft error
        } catch (InterruptedException e) {
            Log.e(TAG, "connect timed out", e);
            error = e;
            if (!wirelessDebuggingForceEnabled) {
                // assuming wifi is connected, this is likely the problem
                updateStatus(Status.NEED_WIRELESS_DEBUGGING);
            }
            // soft error if wirelessDebuggingForceEnabled
        } catch (Throwable t) {
            Log.wtf(TAG, "unexpected exception during connection");
            error = t;
            updateStatus(Status.PROBLEM);
        }
    }


    public class ServiceBinder extends Binder {



    }
}
