package io.benwiegand.attitude;

import static io.benwiegand.attitude.misc.MetaNotificationConstants.META_HIDDEN_NOTIFICATION_PACKAGES;
import static io.benwiegand.attitude.util.UiUtil.dpToPx;
import static io.benwiegand.attitude.util.UiUtil.tintView;

import android.content.ComponentName;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Optional;

import io.benwiegand.attitude.callback.NotificationListenerCallback;
import io.benwiegand.attitude.makeshiftbind.MakeshiftServiceConnection;
import io.benwiegand.attitude.man.PrefMan;
import io.benwiegand.attitude.notification.NotificationInflater;
import io.benwiegand.attitude.notification.NotificationSorter;
import io.benwiegand.attitude.service.MuhNotificationService;

public class NotificationPanelActivity extends AppCompatActivity implements NotificationListenerCallback {
    private static final String TAG = NotificationPanelActivity.class.getSimpleName();
    private static final boolean DEFAULT_SHOW_DEBUG = false;
    public static final String INTENT_EXTRA_SHOW_DEBUG = "io.benwiegand.attitude.NotificationPanelActivity.showDebug";

    private static final long CASCADE_DOWN_ANIMATION_DURATION = 250;
    private static final long CASCADE_DOWN_ANIMATION_DISTANCE_DP = 76;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private MuhNotificationService.ServiceBinder notificationServiceBinder = null;   // use getNotifBinder()
    private NotificationInflater notificationInflater;
    private NotificationSorter notificationSorter;

    private boolean showDebug;

    private boolean hideForegroundMetaNotifications = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notification_panel);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        PrefMan prefMan = new PrefMan(this);
        hideForegroundMetaNotifications = prefMan.read(Boolean.class, PrefMan.KEY_HIDE_FOREGROUND_META_NOTIFICATIONS, true);

        showDebug = getIntent().getBooleanExtra(INTENT_EXTRA_SHOW_DEBUG, DEFAULT_SHOW_DEBUG);

        LinearLayout notificationListLayout = findViewById(R.id.notification_list_layout);
        notificationInflater = new NotificationInflater(this, getLayoutInflater(), notificationListLayout, showDebug);
        notificationSorter = new NotificationSorter(notificationListLayout);

        if (showDebug) {
            findViewById(R.id.notification_service_connection_debug_icon)
                    .setVisibility(View.VISIBLE);
        }

        tintDebugIcon(Color.BLUE);
        MakeshiftServiceConnection.bindService(this, new ComponentName(this, MuhNotificationService.class), notificationServiceConnection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        notificationServiceConnection.destroy();
    }


    private Optional<MuhNotificationService.ServiceBinder> getNotifBinder() {
        return Optional.ofNullable(notificationServiceBinder);
    }

    private void tintDebugIcon(int color) {
        if (!showDebug) return;
        tintView(findViewById(R.id.notification_service_connection_debug_icon), color);
    }


    public void initNotifList() {
        Log.d(TAG, "initializing notification views");
        notificationSorter.resetAll();

        StatusBarNotification[] notifications = getNotifBinder()
                .map(MuhNotificationService.ServiceBinder::getNotifications)
                .orElse(new StatusBarNotification[0]);

        NotificationListenerService.RankingMap rankings = getNotifBinder()
                .map(MuhNotificationService.ServiceBinder::getRankings)
                .orElse(null);

        long delay = 0;
        for (StatusBarNotification sbn : notifications) {
            // render each in a separate event to soften initial load time
            // TODO: fix this properly
            handler.postDelayed(() -> onNotificationPost(sbn, rankings), delay);
            delay += 30;
        }

    }

    @Override
    public void onNotificationsReady() {
        Log.i(TAG, "READY");
        tintDebugIcon(Color.GREEN);

        initNotifList();
    }

    @Override
    public void onNotificationsUnready() {
        Log.i(TAG, "NOT READY");
        tintDebugIcon(Color.RED);
    }

    @Override
    public void onNotificationPost(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        Log.v(TAG, "notif posted!");

        boolean hide = false;

        if (META_HIDDEN_NOTIFICATION_PACKAGES.contains(sbn.getPackageName()) && !sbn.isClearable()) {
            // hide normally hidden meta notifs
            hide = hideForegroundMetaNotifications;
        }

        View notificationView;
        if (!hide) {
            notificationView = notificationInflater.inflate(sbn, rankingMap);
        } else {
            notificationView = new View(this);
        }

        // TODO: properly handle groups
        if (sbn.isAppGroup()) {
            // TODO: find the actual way to hide group headers, this ain't it
//            notificationView.setVisibility(View.GONE);
//            Log.w(TAG, "not showing app group notification");
        }

        notificationView.setTranslationY(-dpToPx(this, CASCADE_DOWN_ANIMATION_DISTANCE_DP));
        notificationView.setAlpha(0);
        notificationView.animate()
                .setDuration(CASCADE_DOWN_ANIMATION_DURATION)
                .alpha(1)
                .translationY(0)
                .start();
        notificationSorter.addNotification(sbn, notificationView, rankingMap);
    }

    @Override
    public void onNotificationRemove(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        Log.i(TAG, "notif removed!");
        notificationSorter.removeNotification(sbn);
        // probably best to not re-rank everything while the user is trying to read their notifications
    }


    private final MakeshiftServiceConnection notificationServiceConnection = new MakeshiftServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "notification service connected");
            tintDebugIcon(Color.YELLOW);
            notificationServiceBinder = (MuhNotificationService.ServiceBinder) service;
            notificationServiceBinder.registerCallback(NotificationPanelActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "notification service disconnected");
            tintDebugIcon(Color.LTGRAY);
            getNotifBinder().ifPresent(b -> b.unregisterCallback(NotificationPanelActivity.this));
            notificationServiceBinder = null;
        }
    };

}
