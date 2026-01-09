package io.benwiegand.attitude.notification;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class DisplayedNotification {

    private final Handler handler;
    private final StatusBarNotification statusBarNotification;
    private final ViewGroup notificationContainerView;
    private final View bigContentView;
    private final View contentView;
    private final Runnable expandTransition;
    private final Runnable collapseTransition;
    private final boolean hidden;

    private boolean attached = false;
    private boolean expanded = false;

    /**
     * creates a DisplayedNotification that wraps a StatusBarNotification and its inflated views
     * @param handler a handler for the main looper thread
     * @param statusBarNotification the StatusBarNotification object
     * @param notificationContainerView the container view
     * @param bigContentView the expanded view
     * @param contentView the collapsed view
     * @param expandTransition callback to animate the expansion of the notification
     * @param collapseTransition callback to animate the collapsing of the notification
     */
    public DisplayedNotification(Handler handler, StatusBarNotification statusBarNotification, ViewGroup notificationContainerView, View bigContentView, View contentView, Runnable expandTransition, Runnable collapseTransition) {
        assert handler.getLooper() == Looper.getMainLooper();
        this.handler = handler;
        this.statusBarNotification = statusBarNotification;
        this.notificationContainerView = notificationContainerView;
        this.bigContentView = bigContentView;
        this.contentView = contentView;
        this.expandTransition = expandTransition;
        this.collapseTransition = collapseTransition;
        hidden = false;
    }

    /**
     * creates a hidden DisplayedNotification with no content
     * @param context a context
     * @param handler a handler for main looper thread
     * @param statusBarNotification status bar notification
     */
    public DisplayedNotification(Context context, Handler handler, StatusBarNotification statusBarNotification) {
        this.handler = handler;
        this.statusBarNotification = statusBarNotification;
        notificationContainerView = new FrameLayout(context);
        bigContentView = null;
        contentView = null;
        expandTransition = null;
        collapseTransition = null;
        hidden = true;
    }

    private void runOnUiThread(Runnable runnable) {
        if (handler.getLooper().isCurrentThread()) {
            runnable.run();
            return;
        }

        handler.post(runnable);
    }

    public void attach(ViewGroup parent, int index, boolean expanded) {
        if (attached) throw new IllegalStateException("DisplayedNotification already attached");
        attached = true;

        parent.addView(getRootView(), index);

        if (hidden) return;

        this.expanded = expanded;

        // ensures both content views get rendered for their height so their animations work
        View hiddenContentView = expanded ? contentView : bigContentView;
        hiddenContentView.setVisibility(View.INVISIBLE);
        handler.post(() -> hiddenContentView.setVisibility(View.GONE));
    }

    public View getRootView() {
        if (!attached) throw new IllegalStateException("DisplayedNotification hasn't been attached yet");   // use attach() instead of adding the view directly
        return notificationContainerView;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public String getKey() {
        return statusBarNotification.getKey();
    }

    public void expand(boolean animate) {
        if (!attached) throw new IllegalStateException("DisplayedNotification hasn't been attached yet");
        if (hidden) return;

        runOnUiThread(() -> {
            if (expanded) return;
            expanded = true;

            if (animate) {
                expandTransition.run();
            } else {
                bigContentView.setVisibility(View.VISIBLE);
                contentView.setVisibility(View.GONE);
            }
        });
    }

    public void collapse(boolean animate) {
        if (!attached) throw new IllegalStateException("DisplayedNotification hasn't been attached yet");
        if (hidden) return;

        runOnUiThread(() -> {
            if (!expanded) return;
            expanded = false;

            if (animate) {
                collapseTransition.run();
            } else {
                contentView.setVisibility(View.VISIBLE);
                bigContentView.setVisibility(View.GONE);
            }
        });
    }


}
