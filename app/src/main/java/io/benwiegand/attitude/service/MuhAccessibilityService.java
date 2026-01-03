package io.benwiegand.attitude.service;

import static io.benwiegand.attitude.misc.Constants.*;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;
import java.util.function.Predicate;

import io.benwiegand.attitude.NotificationPanelActivity;
import io.benwiegand.attitude.makeshiftbind.MakeshiftBind;
import io.benwiegand.attitude.makeshiftbind.MakeshiftBindCallback;

@SuppressLint("AccessibilityPolicy")    // cool story bro
public class MuhAccessibilityService extends AccessibilityService implements MakeshiftBindCallback {
    private static final String TAG = MuhAccessibilityService.class.getSimpleName();
    private static final boolean LOG_DEBUG = true;

    private final ServiceBinder binder = new ServiceBinder();
    private MakeshiftBind makeshiftBind = null;


    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "accessibility service connected");
        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, MuhAccessibilityService.class), this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "accessibility service disconnected");
        makeshiftBind.destroy();
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onMakeshiftBind(Intent intent) {
        return binder;
    }

    @Override
    public void onInterrupt() {

    }

    private void traverseDebug(AccessibilityNodeInfo node, int index, int limit) {
        if (node == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < index; i++) {
            sb.append(" -- ");
        }

        sb.append(node.getPackageName())
                .append('/')
                .append(node.getClassName())
                .append(" (")
                .append(node.getViewIdResourceName())
                .append(")");

        Log.d(TAG, sb.toString());
        if (limit != -1 && index >= limit) return;

        for (int i = 0; i < node.getChildCount(); i++) {
            traverseDebug(node.getChild(i), index + 1, limit);
        }
    }

    private void traverseDebug(AccessibilityNodeInfo node, int limit) {
        traverseDebug(node, 0, limit);
    }


    @SuppressLint("SwitchIntDef")
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (LOG_DEBUG) {
            Log.v(TAG, "EVENT! " + AccessibilityEvent.eventTypeToString(event.getEventType()));
            if (event.getPackageName() != null && event.getClassName() != null) {
                Log.d(TAG, "component: " + event.getPackageName() + "/" + event.getClassName());
            } else {
                Log.d(TAG, "package: " + event.getPackageName());
                Log.d(TAG, "class: " + event.getClassName());
            }
            Log.d(TAG, "win id: " + event.getWindowId());

            AccessibilityNodeInfo node = event.getSource();
            if (node != null) {
                Log.d(TAG, "node id: " + node.getViewIdResourceName());
                Log.d(TAG, "node pkg: " + node.getPackageName());
                Log.d(TAG, "node class: " + node.getClassName());
                if (node.getWindow() != null) {
                    Log.d(TAG, "win title: " + node.getWindow().getTitle());
                    Log.d(TAG, "display id: " + node.getWindow().getDisplayId());
                }

                node.recycle();
            }
        }


        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                 AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // nothing for now
            }

            case AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (handleClickRemap(event)) return;
            }

            case AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if ((event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_ADDED) != 0)
                    Log.d(TAG, "window added");

                // TODO: investigate more
            }

            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.v(TAG, "window state changed event");

                if (handleAnnoyingMetaPopups(event)) return;
                if (handleDraggableRemap(event)) return;

                if (LOG_DEBUG) {
                    List<AccessibilityWindowInfo> windowsOnDisplay = getWindowsOnAllDisplays().get(event.getDisplayId());
                    if (windowsOnDisplay != null) {
                        for (AccessibilityWindowInfo window : windowsOnDisplay) {
                            if (event.getWindowId() != window.getId()) continue;
                            Log.d(TAG, "win: " + window.getTitle());
                            if (window.getRoot() != null)
                                traverseDebug(window.getRoot(), 2);
                        }
                    }
                }
            }
            default -> Log.wtf(TAG, "unexpected event type: " + event.getEventType());
        }
    }


    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo root, Predicate<AccessibilityNodeInfo> criteria) {
        if (root == null) return null;
        if (criteria.test(root)) return root;

        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo node = findNode(root.getChild(i), criteria);
            if (node != null) return node;
        }

        return null;
    }

    private AccessibilityNodeInfo findNodeWithId(AccessibilityNodeInfo root, String id) {
        return findNode(root, n -> id.equals(n.getViewIdResourceName()));
    }

    /**
     * if the event is regarding the opening of an Annoying Meta Popup, close it and return true.
     * @return true if an Annoying Meta Popup was identified, false otherwise
     */
    private boolean handleAnnoyingMetaPopups(AccessibilityEvent event) {
        // TODO: use TYPE_WINDOWS_CHANGED? it appears to have less metadata but is more correct
        //       I may be missing something though
        assert event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return false;

        ComponentName component = new ComponentName(pkg.toString(), cls.toString());
        Log.d(TAG, "checking " + component);


        // systemux window title bars
        if (component.equals(VRSHELL_PRESENTATION_COMPONENT)) {

            AccessibilityNodeInfo node = event.getSource();
            if (node == null) return false;
            try {
                AccessibilityNodeInfo titleTextView = findNodeWithId(node, VRSHELL_WINDOW_TITLE_ID);
                if (titleTextView == null) {    // some of them don't have a title bar.
                    Log.d(TAG, "no title bar on systemux window");
                    return false;
                }

                // check title
                String title = String.valueOf(titleTextView.getText());
                if (!VRSHELL_POPUP_WINDOW_TITLES.contains(title)) return false;
                Log.i(TAG, "Annoying Meta Popup identified!!!   title = " + title);

                // literally just hit the close button
                AccessibilityNodeInfo closeButton = findNodeWithId(node, VRSHELL_CLOSE_BUTTON_ID);
                if (closeButton == null) {
                    Log.wtf(TAG, "failed to find close button");
                } else if (!closeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.e(TAG, "close button click action returned false");
                }

                return true;

            } finally {
                node.recycle();
            }

        }

        /*
        for (ComponentName ampComponent : POPUP_COMPONENT_NAMES) {
            if (!component.equals(ampComponent)) continue;

            Log.i(TAG, "Annoying Meta Popup identified!!!   activity = " + ampComponent);

            // TODO: figure out a way to consistently/forcibly close these without also consistently/forcibly crashing the shell
            // maybe though adb?

            return true;
        }
        */

        return false;
    }

    /**
     * if the event regards a clicked ui element that was re-mapped to something, do the something and return true
     * @return true if the clicked element is re-mapped, false otherwise
     */
    private boolean handleClickRemap(AccessibilityEvent event) {
        assert event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED;

        AccessibilityNodeInfo node = event.getSource();
        if (node == null) return false;
        try {

            String id = node.getViewIdResourceName();

            if (SYSTEMUX_PROFILE_BUTTON_ID.equals(id)) {
                startActivity(new Intent(this, NotificationPanelActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            }

        } finally {
            node.recycle();
        }

        return false;
    }

    /**
     * if the event regards a draggable icon that has been mapped to something, do the something and return true
     * @return true if the draggable icon was mapped to something, false otherwise
     */
    private boolean handleDraggableRemap(AccessibilityEvent event) {
        assert event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

        AccessibilityNodeInfo node = event.getSource();
        if (node == null) return false;
        try {

            AccessibilityWindowInfo window = node.getWindow();
            if (node.getWindow() == null) return false;

            String windowTitle = String.valueOf(window.getTitle());
            if (!SYSTEMUX_DRAGGABLE_WINDOW_TITLE.equals(windowTitle)) return false;

            // TODO: optimize draggable identification to be a little more scale-able
            if (findNodeWithId(node, SYSTEMUX_LIBRARY_ICON_ID) != null) {
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .setComponent(LIGHTNING_LAUNCHER_COMPONENT)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            }

        } finally {
            node.recycle();
        }

        return false;
    }


    public class ServiceBinder extends Binder {
        // nothing for now
    }

}
