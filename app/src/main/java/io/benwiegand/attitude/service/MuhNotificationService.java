package io.benwiegand.attitude.service;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;
import java.util.function.Consumer;

import io.benwiegand.attitude.callback.CallbackRegistrar;
import io.benwiegand.attitude.callback.NotificationListenerCallback;
import io.benwiegand.attitude.makeshiftbind.MakeshiftBind;
import io.benwiegand.attitude.makeshiftbind.MakeshiftBindCallback;

public class MuhNotificationService extends NotificationListenerService implements MakeshiftBindCallback {
    private static final String TAG = MuhNotificationService.class.getSimpleName();

    private final ServiceBinder binder = new ServiceBinder();
    private MakeshiftBind makeshiftBind = null;

    private CallbackRegistrar<NotificationListenerCallback> listenerCallbackRegistrar;

    private boolean connected = false;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "notification listener created");

        listenerCallbackRegistrar = new CallbackRegistrar<>(List.of(
                cb -> {
                    if (connected) cb.onNotificationsReady();
                }
        ));

        // Note: DO NOT, I REPEAT, DO NOT EVER OVERRIDE onBind() AND EXPECT THIS SHIT TO WORK!!!!!
        // YOU WILL WASTE HALF A DAY SMASHING YOUR HEAD AGAINST A BRICK WALL
        // hence: MakeshiftBind here instead of a real binding
        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, MuhNotificationService.class), this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "notification listener destroyed");
        makeshiftBind.destroy();
    }

    @Override
    public IBinder onMakeshiftBind(Intent intent) {
        return binder;
    }

    @Override
    public void onListenerConnected() {
        connected = true;
        Log.v(TAG, "notification listener connected");
        listenerCallbackRegistrar.callCallbacks(NotificationListenerCallback::onNotificationsReady);
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        Log.v(TAG, "notification listener disconnected");
        listenerCallbackRegistrar.callCallbacks(NotificationListenerCallback::onNotificationsUnready);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.d(TAG, "notification posted");
        listenerCallbackRegistrar.callCallbacks(c -> c.onNotificationPost(sbn, rankingMap));
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.d(TAG, "notification removed");
        listenerCallbackRegistrar.callCallbacks(c -> c.onNotificationRemove(sbn, rankingMap));
    }


    public class ServiceBinder extends Binder {

        public StatusBarNotification[] getNotifications() {
            return getActiveNotifications();
        }

        public RankingMap getRankings() {
            return getCurrentRanking();
        }

        public void registerCallback(NotificationListenerCallback callback) {
            listenerCallbackRegistrar.registerCallback(callback);
        }

        public void unregisterCallback(NotificationListenerCallback callback) {
            listenerCallbackRegistrar.unregisterCallback(callback);
        }

    }

}
