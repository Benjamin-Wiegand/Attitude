package io.benwiegand.attitude.service;

import static io.benwiegand.attitude.misc.Constants.*;
import static io.benwiegand.attitude.util.UiUtil.showError;
import static io.benwiegand.attitude.util.WiFiUtil.connectToWifi;
import static io.benwiegand.attitude.util.WiFiUtil.getRuntimeLocationPermission;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.benwiegand.attitude.MainActivity;
import io.benwiegand.attitude.NotificationPanelActivity;
import io.benwiegand.attitude.makeshiftbind.MakeshiftBind;
import io.benwiegand.attitude.makeshiftbind.MakeshiftBindCallback;
import io.benwiegand.attitude.man.PrefMan;
import io.benwiegand.attitude.util.PackageUtil;

@SuppressLint("AccessibilityPolicy")    // cool story bro
public class MuhAccessibilityService extends AccessibilityService implements MakeshiftBindCallback {
    private static final String TAG = MuhAccessibilityService.class.getSimpleName();
    private static final boolean LOG_DEBUG = true;

    private final ServiceBinder binder = new ServiceBinder();
    private MakeshiftBind makeshiftBind = null;

    // cache these in lookup tables to reduce load on the system

    private final Set<String> popupWindowTitles = new HashSet<>();

    // for remapping actions that open windows
    private final Map<String, Runnable> windowTitleRemaps = new HashMap<>();
    private static final List<Pair<String, String>> windowRemapPrefsWithWindowTitles = List.of(
            Pair.create(PrefMan.KEY_REMAP_APP_DRAWER_WINDOW, APP_DRAWER_WINDOW_TITLE),
            Pair.create(PrefMan.KEY_REMAP_NOTIFICATION_WINDOW, NOTIFICATION_WINDOW_TITLE)
    );

    // for remapping draggable buttons on drag
    private final Map<String, Runnable> draggableIdRemaps = new HashMap<>();
    private static final List<Pair<String, String>> draggableRemapPrefsWithIds = List.of(
            Pair.create(PrefMan.KEY_REMAP_APP_DRAWER_DRAG, SYSTEMUX_LIBRARY_BACKGROUND_ID)
    );

    // for buttons that can be directly remapped (normally non-functional)
    private final Map<String, Runnable> systemuxButtonIdRemaps = new HashMap<>();
    private static final List<Pair<String, String>> systemuxButtonRemapPrefsWithIds = List.of(
            Pair.create(PrefMan.KEY_REMAP_PROFILE_BUTTON, SYSTEMUX_PROFILE_BUTTON_ID)
    );


    private final Map<String, Runnable> remapTargetsToActions = Map.of(
            PrefMan.REMAP_TARGET_NOTIFICATION_DRAWER, () -> {
                startActivity(new Intent(this, NotificationPanelActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            },
            PrefMan.REMAP_TARGET_WIFI_CONNECT, () -> {
                // TODO: error messages

                PrefMan prefMan = new PrefMan(this);
                String targetSSID = prefMan.read(String.class, PrefMan.KEY_WIFI_NAME, null);
                if (targetSSID == null) {
                    Log.e(TAG, "no configured wifi network");
                    return;
                }

                try {
                    if (!getRuntimeLocationPermission(this)) {
                        Log.e(TAG, "failed to get runtime location permission");
                    } else if (!connectToWifi(this, targetSSID)) {
                        Log.e(TAG, "wifi connection result = false");
                    } else {
                        Log.v(TAG, "wifi connecting!");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Wifi auto-connect failed", t);
                    showError(this, t);
                }

            },
            PrefMan.REMAP_TARGET_ATTITUDE, () -> {
                PackageUtil.launchActivity(this, MainActivity.class);
            },
            PrefMan.REMAP_TARGET_LIGHTNING_LAUNCHER, () -> {
                //TODO: check if installed
                PackageUtil.launchActivity(this, LIGHTNING_LAUNCHER_COMPONENT);
            }
    );



    private void initLookups() {
        popupWindowTitles.clear();
        windowTitleRemaps.clear();
        draggableIdRemaps.clear();
        systemuxButtonIdRemaps.clear();

        PrefMan prefMan = new PrefMan(this);

        if (prefMan.read(Boolean.class, PrefMan.KEY_BLOCK_POPUP_HORIZON_FEED, true))
            popupWindowTitles.add(HORIZON_FEED_WINDOW_TITLE);
        if (prefMan.read(Boolean.class, PrefMan.KEY_BLOCK_POPUP_PASSTHROUGH, true))
            popupWindowTitles.add(PASSTHROUGH_WINDOW_TITLE);

        if (prefMan.read(Boolean.class, PrefMan.KEY_REMAP_GLOBAL_ENABLE, true)) {
            for (Pair<String, String> e : windowRemapPrefsWithWindowTitles) {
                String prefKey = e.first, winTitle = e.second;
                String target = prefMan.read(String.class, prefKey, null);
                if (target == null) continue;
                windowTitleRemaps.put(winTitle, remapTargetsToActions.get(target));
            }

            for (Pair<String, String> e : draggableRemapPrefsWithIds) {
                String prefKey = e.first, id = e.second;
                String target = prefMan.read(String.class, prefKey, null);
                if (target == null) continue;
                draggableIdRemaps.put(id, remapTargetsToActions.get(target));
            }

            for (Pair<String, String> e : systemuxButtonRemapPrefsWithIds) {
                String prefKey = e.first, id = e.second;
                String target = prefMan.read(String.class, prefKey, null);
                if (target == null) continue;
                systemuxButtonIdRemaps.put(id, remapTargetsToActions.get(target));
            }
        }

        Log.i(TAG, "regenerated lookup tables");
        Log.v(TAG, "popup window titles: " + popupWindowTitles);
        Log.v(TAG, "window title remaps: " + windowTitleRemaps.keySet());
        Log.v(TAG, "draggable id remaps: " + draggableIdRemaps.keySet());
        Log.v(TAG, "systemux button id remaps: " + systemuxButtonIdRemaps.keySet());
    }


    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "accessibility service connected");
        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, MuhAccessibilityService.class), this);
        initLookups();
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
                if (handleWindowRemap(event)) return;
                if (handleDraggableRemap(event)) return;

                if (LOG_DEBUG) {
                    List<AccessibilityWindowInfo> windowsOnDisplay = getWindowsOnAllDisplays().get(event.getDisplayId());
                    if (windowsOnDisplay != null) {
                        for (AccessibilityWindowInfo window : windowsOnDisplay) {
                            if (event.getWindowId() != window.getId()) continue;
                            Log.d(TAG, "win: " + window.getTitle());
                            if (window.getRoot() != null)
                                traverseDebug(window.getRoot(), 1);
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
                if (!popupWindowTitles.contains(title)) return false;
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
            Runnable target = systemuxButtonIdRemaps.getOrDefault(node.getViewIdResourceName(), null);
            if (target == null) return false;

            target.run();
            return true;
        } finally {
            node.recycle();
        }
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

            AccessibilityNodeInfo iconView = findNodeWithId(node, SYSTEMUX_DRAGGABLE_BUTTON_ICON_ID);
            if (iconView == null) {
                Log.w(TAG, "no icon view found in draggable window");
                return false;
            }

            // the parent view to the icon is the only one that has a unique id
            AccessibilityNodeInfo bgNode = iconView.getParent();
            if (bgNode == null) {
                Log.wtf(TAG, "view hierarchy changed while processing it");
                return false;
            }

            String id = bgNode.getViewIdResourceName();
            Log.d(TAG, "draggable id: " + id);

            Runnable target = draggableIdRemaps.getOrDefault(id, null);
            if (target == null) return false;
            Log.i(TAG, "remapping draggable: " + id);
            target.run();

        } finally {
            node.recycle();
        }

        return false;
    }

    /**
     * if the event regards a window opening which has been remapped to something else:
     * close the window, do the something, and return true
     * @return true if the window was mapped to something, false otherwise
     */
    private boolean handleWindowRemap(AccessibilityEvent event) {
        assert event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return false;
        ComponentName component = new ComponentName(pkg.toString(), cls.toString());

        // systemux window title bars
        if (component.equals(VRSHELL_PRESENTATION_COMPONENT)) {

            AccessibilityNodeInfo node = event.getSource();
            if (node == null) return false;
            try {

                AccessibilityNodeInfo titleTextView = findNodeWithId(node, VRSHELL_WINDOW_TITLE_ID);
                if (titleTextView == null) return false;

                Runnable target = windowTitleRemaps.getOrDefault(String.valueOf(titleTextView.getText()), null);
                if (target == null) return false;
                Log.i(TAG, "remapping window: " + titleTextView.getText());

                AccessibilityNodeInfo closeButton = findNodeWithId(node, VRSHELL_CLOSE_BUTTON_ID);
                if (closeButton == null) {
                    Log.wtf(TAG, "failed to find close button");
                    return true;
                } else if (!closeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.e(TAG, "close button click action returned false");
                    return true;
                }

                target.run();

                return true;

            } finally {
                node.recycle();
            }

        }

        return false;
    }


    public class ServiceBinder extends Binder {

        // TODO: call this in settings activity
        public void reloadSettings() {
            Log.i(TAG, "re-loading due to binder call");
            initLookups();
        }

    }

}
