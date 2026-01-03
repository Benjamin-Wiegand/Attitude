package io.benwiegand.attitude.callback;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public interface NotificationListenerCallback {

    void onNotificationsReady();
    void onNotificationsUnready();

    void onNotificationPost(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap);
    void onNotificationRemove(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap);

}
