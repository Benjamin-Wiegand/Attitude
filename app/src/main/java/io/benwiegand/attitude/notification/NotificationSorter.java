package io.benwiegand.attitude.notification;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class NotificationSorter {
    private static final String TAG = NotificationSorter.class.getSimpleName();

    private static final class Element {
        View view = null;
        final NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        boolean rankingValid = false;

        public Optional<NotificationListenerService.Ranking> getRanking() {
            if (!rankingValid) return Optional.empty();
            return Optional.of(ranking);
        }

        @Override
        public String toString() {
            return "Element{" +
                    "view=" + view +
                    ", ranking=" + ranking +
                    ", rankingValid=" + rankingValid +
                    '}';
        }
    }


    private final Map<String, Element> keyMap = new HashMap<>();
    private final LinearLayout listRootView;
    private boolean sorted = true;  // TODO: do something about it

    public NotificationSorter(LinearLayout listRootView) {
        this.listRootView = listRootView;
    }

    public void addNotification(StatusBarNotification sbn, View view, NotificationListenerService.RankingMap rankingMap) {
        Element e = keyMap.get(sbn.getKey());
        if (e == null) {
            Log.d(TAG, "notification added: " + sbn.getKey());
            e = new Element();
        } else {
            Log.d(TAG, "notification updated: " + sbn.getKey());
        }

        View oldView = e.view;
        e.view = view;

        e.rankingValid = false;
        if (rankingMap == null) {
            Log.w(TAG, "addNotification(): ranking map is null");
        } else if (!rankingMap.getRanking(sbn.getKey(), e.ranking)) {
            Log.wtf(TAG, "addNotificatio(): provided ranking map rejected key of provided notification");
        } else {
            e.rankingValid = true;
        }

        // TODO: sections and groups
//        sbn.getKey();
//        sbn.getPackageName();
//        sbn.getGroupKey();
//        sbn.getId();
//        sbn.getOpPkg();
//        sbn.getOverrideGroupKey();
//        sbn.getTag();
//        sbn.isAppGroup();
//        sbn.isGroup();

        int index = 0;  // fall back to top
        if (e.rankingValid) {
            index = e.ranking.getRank();
        }

        if (index > listRootView.getChildCount()) {
            Log.w(TAG, "index too large, limiting");
            index = listRootView.getChildCount();
            sorted = false;
        }

        keyMap.put(sbn.getKey(), e);
        listRootView.addView(view, index);

        if (oldView == null) return;
        listRootView.removeView(oldView);
    }

    public void removeNotification(StatusBarNotification sbn) {
        Element e = keyMap.remove(sbn.getKey());
        if (e == null) {
            Log.w(TAG, "removeNotification() invoked for notification that doesn't exist");
            return;
        }

        sorted = false;

        listRootView.removeView(e.view);
    }

    public void resetAll() {
        Log.i(TAG, "resetAll()");
        listRootView.removeAllViews();
        keyMap.clear();
        sorted = true;
    }

}
