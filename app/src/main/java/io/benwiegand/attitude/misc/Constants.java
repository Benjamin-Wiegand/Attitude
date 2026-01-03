package io.benwiegand.attitude.misc;

import android.content.ComponentName;

import java.util.Set;

public class Constants {

    // seems to do the annoying popups. ironic for "ux"
    public static final String SYSTEMUX_PKG = "com.oculus.systemux";

    // seems to do title bars and overlay components
    public static final String VRSHELL_PKG = "com.oculus.vrshell";

    // the "horizon feed"
    public static final String HORIZON_FEED_PKG = "com.oculus.explore";


    // for remapping things
    public static final String SYSTEMUX_DRAGGABLE_WINDOW_TITLE = "com.oculus.android_panel_app.AndroidPanelLayer-com.oculus.systemux-draggable";
    public static final String SYSTEMUX_LIBRARY_ICON_ID = "com.oculus.systemux:id/library_background";
    public static final String SYSTEMUX_PROFILE_BUTTON_ID = "com.oculus.systemux:id/profile_button_hit_target";


    // title-bar component for systemux windows
    public static final ComponentName VRSHELL_PRESENTATION_COMPONENT = new ComponentName(VRSHELL_PKG, "android.app.Presentation");

    // title-bar popup identification
    // I'm aware that this isn't perfect (namely, doesn't work for different languages, and might close similarly-titled windows)
    // TODO: it might be possible to fix the language problem by leveraging resource IDs
    public static final Set<String> VRSHELL_POPUP_WINDOW_TITLES = Set.of(
            "Passthrough",          // annoying "add an avatar mirror" popup
            "Horizon Feed"          // pops up on boot
    );

    // elements of title bar
    public static final String VRSHELL_CLOSE_BUTTON_ID = "com.oculus.vrshell:id/close_button";
    public static final String VRSHELL_WINDOW_TITLE_ID = "com.oculus.vrshell:id/app_display_name";


    // third-party integrations
    public static final ComponentName LIGHTNING_LAUNCHER_COMPONENT = new ComponentName("com.threethan.launcher", "com.threethan.LightningLauncher");



    // better popup identification
    // TODO: better for identifying, but need to find a way to reliably close these
    public static final Set<ComponentName> POPUP_COMPONENT_NAMES = Set.of(
            // for quick testing
            //new ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock"),
            //new ComponentName("com.limelight.debug", "com.limelight.PcView"),

            new ComponentName(HORIZON_FEED_PKG, "com.oculus.explore.ExploreActivity"),                                                      // "horizon feed"
            new ComponentName(SYSTEMUX_PKG, "com.oculus.panelapp.androiddialog.dialogs.common.mas.MasLaunchRestrictedDialogActivity"),     // "your org has blocked" which happens when you disable the above
            new ComponentName(SYSTEMUX_PKG, "com.oculus.homeinworldmenu.HomeInWorldMenu")                                                  // "add an avatar mirror" popup

    );


}
