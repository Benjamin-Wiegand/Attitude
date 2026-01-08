package io.benwiegand.attitude.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import io.benwiegand.attitude.AdbActivity;
import io.benwiegand.attitude.R;
import io.benwiegand.attitude.adb.AdbConnectionManager;
import io.benwiegand.attitude.adb.QdAdbShell;
import io.benwiegand.attitude.async.Sec;
import io.benwiegand.attitude.async.SecAdapter;
import io.benwiegand.attitude.callback.CallbackRegistrar;
import io.benwiegand.attitude.man.KeyMan;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;

public class AdbService extends Service {
    private static final String TAG = AdbService.class.getSimpleName();

    private static final long IDLE_CONNECTION_POLL_INTERVAL = 5000;

    private static final long CONNECTION_LOOP_AUTO_RESTART_AFTER_FAILURE_DELAY = 10000;

    private static final long WIRELESS_DEBUGGING_RESTART_DELAY = 1000;

    private static final String FOREGROUND_NOTIFICATION_CHANNEL = "adb_foreground";
    private static final String SETTINGS_GLOBAL_WIRELESS_DEBUGGING_KEY = "adb_wifi_enabled";

    public enum Status {
        NEED_SETUP,
        NEED_WIRELESS_DEBUGGING,
        NEED_PAIRING,
        // TODO: also check for wifi
        PROBLEM,
        BUSY,
        CONNECTED;

        public boolean isTerminal() {
            return switch (this) {
                case NEED_SETUP, PROBLEM, NEED_PAIRING, NEED_WIRELESS_DEBUGGING -> true;
                case BUSY, CONNECTED -> false;
            };
        }

    }

    private record CommandQueueEntry(String command, long queueExpiration, long executionTimeout, SecAdapter<QdAdbShell.Result> adapter) {}

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Binder binder = new ServiceBinder();

    private Status currentStatus = Status.BUSY;
    private final CallbackRegistrar<Consumer<Status>> statusCallbackRegistrar = new CallbackRegistrar<>(List.of(
            cb -> cb.accept(currentStatus)
    ));

    private AdbConnectionManager adbConnectionManager = null;
    private QdAdbShell qdShell = null;
    private final Queue<CommandQueueEntry> commandQueue = new ConcurrentLinkedQueue<>();

    private boolean wirelessDebuggingForceEnabled = false;


    private Thread connectionThread;
    private boolean serviceRunning = true;
    private boolean dead = true;

    private Throwable error;


    private void updateStatus(Status status) {
        Log.i(TAG, "status: " + status);
        currentStatus = status;
        handler.post(() -> statusCallbackRegistrar.callCallbacks(cb -> cb.accept(status)));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "adb service created");

        createForegroundNotification();
        startConnectionThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "adb service destroyed");

        serviceRunning = false;
        synchronized (commandQueue) {
            CommandQueueEntry entry;
            while ((entry = commandQueue.poll()) != null)
                entry.adapter().throwError(new RejectedExecutionException("service died"));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void startConnectionThread() {
        assert Looper.getMainLooper().isCurrentThread();    // this is not thread safe
        if (!dead) {
            Log.d(TAG, "connection loop thread already running");
            return;
        }

        Log.i(TAG, "starting connection loop thread");
        connectionThread = new Thread(this::connectionLoop);
        connectionThread.start();
    }

    private void connectionLoop() {
        dead = false;
        try {

            while (serviceRunning) {

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


                if (qdShell == null || qdShell.isDead()) {
                    try {
                        Log.i(TAG, "popping a new shell");
                        AdbStream stream = adbConnectionManager.openStream("shell:");
                        QdAdbShell newQdShell = new QdAdbShell(stream);
                        if (!newQdShell.init()) {
                            //todo: should probably have a max number of retries
                            Log.e(TAG, "qd shell init failed");
                            continue;
                        }

                        qdShell = newQdShell;
                    } catch (Throwable t) {
                        Log.e(TAG, "failed to open a 'shell:' stream", t);
                        continue;
                    }
                }


                while (!qdShell.isDead()) {
                    CommandQueueEntry entry;
                    synchronized (commandQueue) {
                        entry = commandQueue.poll();
                        if (entry == null) {
                            try {
                                commandQueue.wait(IDLE_CONNECTION_POLL_INTERVAL);
                            } catch (InterruptedException ignored) {}

                            break;
                        }
                    }

                    if (entry.queueExpiration() < SystemClock.elapsedRealtime()) {
                        Log.w(TAG, "polled command queue entry is expired");
                        // caller doesn't want it executed this late, discard the event
                        entry.adapter().throwError(new RejectedExecutionException("queue timeout reached before command could be executed"));
                        continue;
                    }

                    try {
                        Log.d(TAG, "executing queued command");
                        QdAdbShell.Result result = qdShell.execute(entry.command(), entry.executionTimeout());
                        entry.adapter().provideResult(result);
                    } catch (QdAdbShell.ExecutionException e) {
                        Log.w(TAG, "exception during queued command execution", e);
                        entry.adapter().throwError(e);
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "unexpected exception in connection loop", e);
            error = e;
            updateStatus(Status.PROBLEM);
        } finally {
            // ensure coherent status
            if (!currentStatus.isTerminal()) {
                Log.wtf(TAG, "connection loop terminating while status is still " + currentStatus);
                error = new RuntimeException("connection loop terminated unexpectedly. is this a bug?").fillInStackTrace();
                updateStatus(Status.PROBLEM);
            }

            Log.i(TAG, "connection loop death");
            dead = true;

            if (serviceRunning) {
                Log.i(TAG, "scheduling restart of connection loop in " + CONNECTION_LOOP_AUTO_RESTART_AFTER_FAILURE_DELAY + " ms");
                handler.postDelayed(this::startConnectionThread, CONNECTION_LOOP_AUTO_RESTART_AFTER_FAILURE_DELAY);
            }

        }
    }


    private void createForegroundNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL);
        if (channel == null)
            nm.createNotificationChannel(new NotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL, getString(R.string.adb_service_foreground_notification_channel_name), NotificationManager.IMPORTANCE_LOW));

        Intent intent = new Intent(this, AdbActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)    // TODO: better icon for this
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setContentTitle(getString(R.string.adb_service_foreground_notification_title))
                .setSubText(getString(R.string.adb_service_foreground_notification_sub_text))
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE); // I mean it's not wrong
            } else {
                startForeground(1, notification);
            }
        } catch (Throwable t) {
            Log.e(TAG, "failed to start foreground context", t);
        }
    }


    private void tryEnableWirelessDebugging() {
        ContentResolver cr = getContentResolver();

        if (wirelessDebuggingForceEnabled && error instanceof InterruptedException) {
            // sometimes wireless debugging fails to start correctly, toggling it off and on fixes it
            Log.i(TAG, "restarting wireless debugging");
            try {
                // messy hack, but this seems like the simplest way to ensure the system actually notices the change and restarts it
                Settings.Global.putInt(cr, SETTINGS_GLOBAL_WIRELESS_DEBUGGING_KEY, 0);
                handler.postDelayed(() -> {
                    try {
                        Settings.Global.putInt(cr, SETTINGS_GLOBAL_WIRELESS_DEBUGGING_KEY, 1);
                    } catch (SecurityException e) {
                        Log.w(TAG, "got security exception while re-enabling wireless debugging for restart");
                    }
                }, WIRELESS_DEBUGGING_RESTART_DELAY);
                return;
            } catch (SecurityException e) {
                Log.w(TAG, "got security exception while disabling wireless debugging for restart");
            }
        }

        wirelessDebuggingForceEnabled = Settings.Global.getInt(cr, SETTINGS_GLOBAL_WIRELESS_DEBUGGING_KEY, 0) == 1;
        if (wirelessDebuggingForceEnabled) return;
        Log.v(TAG, "wireless debugging is not enabled");

        try {
            // wireless debugging can be kick-started automatically if WRITE_SECURE_SETTINGS is granted
            Settings.Global.putInt(cr, SETTINGS_GLOBAL_WIRELESS_DEBUGGING_KEY, 1);
            wirelessDebuggingForceEnabled = Settings.Global.getInt(cr, SETTINGS_GLOBAL_WIRELESS_DEBUGGING_KEY, 0) == 1;
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
            updateStatus(Status.NEED_PAIRING);
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

        public Sec<QdAdbShell.Result> executeSafeCommand(String command, long queueTimeout, long executionTimeout) {
            synchronized (commandQueue) {
                if (!serviceRunning)
                    return Sec.premeditatedError(new RejectedExecutionException("service died"));

                SecAdapter.SecWithAdapter<QdAdbShell.Result> secWithAdapter = SecAdapter.createThreadless();
                commandQueue.add(new CommandQueueEntry(
                        command,
                        SystemClock.elapsedRealtime() + queueTimeout,
                        executionTimeout,
                        secWithAdapter.secAdapter()));
                commandQueue.notify();
                return secWithAdapter.sec();
            }
        }

        public Status getStatus() {
            return currentStatus;
        }

        public Throwable getLastError() {
            return error;
        }

        public void registerStatusCallback(Consumer<Status> callback) {
            statusCallbackRegistrar.registerCallback(callback);
        }

        public void unregisterStatusCallback(Consumer<Status> callback) {
            statusCallbackRegistrar.unregisterCallback(callback);
        }

    }
}
