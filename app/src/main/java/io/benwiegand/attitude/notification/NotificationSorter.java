package io.benwiegand.attitude.notification;

import static io.benwiegand.attitude.util.UiUtil.dpToPx;

import android.content.Context;
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

    private static final long CASCADE_DOWN_ANIMATION_DURATION = 250;
    private static final long CASCADE_DOWN_ANIMATION_DISTANCE_DP = 76;


    private static final class Element {
        DisplayedNotification displayedNotification = null;
        final NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        boolean rankingValid = false;

        public Optional<NotificationListenerService.Ranking> getRanking() {
            if (!rankingValid) return Optional.empty();
            return Optional.of(ranking);
        }

        @Override
        public String toString() {
            return "Element{" +
                    "displayedNotification=" + displayedNotification +
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

    private Context getContext() {
        return listRootView.getContext();
    }

    public void addNotification(DisplayedNotification dNotif, NotificationListenerService.RankingMap rankingMap) {
        Element e = keyMap.get(dNotif.getKey());
        if (e == null) {
            Log.d(TAG, "notification added: " + dNotif.getKey());
            e = new Element();
        } else {
            Log.d(TAG, "notification updated: " + dNotif.getKey());
        }

        DisplayedNotification oldDNotif = e.displayedNotification;
        e.displayedNotification = dNotif;

        e.rankingValid = false;
        if (rankingMap == null) {
            Log.w(TAG, "addNotification(): ranking map is null");
        } else if (!rankingMap.getRanking(dNotif.getKey(), e.ranking)) {
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

        Log.d(TAG, "index = " + index);

        boolean startExpanded = index == 0 ||
                (oldDNotif != null && oldDNotif.isExpanded());

        keyMap.put(dNotif.getKey(), e);
        dNotif.attach(listRootView, index, startExpanded);


        if (oldDNotif != null) {
            // remove the old one
            listRootView.removeView(oldDNotif.getRootView());


        } else {

            View rootView = dNotif.getRootView();
            rootView.setTranslationY(-dpToPx(getContext(), CASCADE_DOWN_ANIMATION_DISTANCE_DP));
            rootView.setAlpha(0);
            rootView.animate()
                    .setDuration(CASCADE_DOWN_ANIMATION_DURATION)
                    .alpha(1)
                    .translationY(0)
                    .start();
        }


    }

    public void removeNotification(StatusBarNotification sbn) {
        Element e = keyMap.remove(sbn.getKey());
        if (e == null) {
            Log.w(TAG, "removeNotification() invoked for notification that doesn't exist");
            return;
        }

        sorted = false;

        listRootView.removeView(e.displayedNotification.getRootView());
    }

    public void resetAll() {
        Log.i(TAG, "resetAll()");
        listRootView.removeAllViews();
        keyMap.clear();
        sorted = true;
    }

}
