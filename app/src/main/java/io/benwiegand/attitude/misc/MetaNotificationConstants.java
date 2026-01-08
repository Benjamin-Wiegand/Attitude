package io.benwiegand.attitude.misc;

import java.util.Set;

public class MetaNotificationConstants {

    // packages of which to hide foreground notifications from
    // these don't show up in the normal notification drawer and are usually just for foreground context
    public static final Set<String> META_HIDDEN_NOTIFICATION_PACKAGES = Set.of(
            "com.oculus.systemdriver",
            "com.facebook.spatial_persistence_service",
            "com.oculus.presence",
            "com.oculus.vrshell",
            "com.oculus.guardian",
            "com.oculus.captionservice",
            "com.oculus.metacam",
            "com.oculus.assistant",
            "com.oculus.bodyapiservice"
    );

}
