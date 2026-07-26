# Release hardening — R8 full mode + aggressive shrinking (see gradle.properties)

# Native / JNI
-keep class ca.psiphon.Tun2SocksJniLoader {
    native <methods>;
}
-keep class com.psiphon3.VpnManager {
    public static void logTun2Socks(java.lang.String, java.lang.String, java.lang.String);
}

# Psiphon tunnel library (AAR)
-keep class ca.psiphon.** { *; }
-dontwarn ca.psiphon.**

# Tunnel service entry points
-keep class com.psiphon3.psiphonlibrary.TunnelManager { *; }
-keep class com.psiphon3.psiphonlibrary.TunnelProviderService { *; }
-keep class com.psiphon3.psiphonlibrary.EmbeddedValues {
    public static <fields>;
    public static <methods>;
}

# Secret decoder — keep names so EmbeddedValues <clinit> always resolves
-keep class com.psiphon3.psiphonlibrary.internal.Sd {
    public static java.lang.String d(int, byte[]);
    public static java.lang.String[] da(int, byte[][]);
}
-keepclassmembers class com.psiphon3.psiphonlibrary.EmbeddedValues {
    static <clinit>();
}
-keep class com.psiphon3.azadi.BundledServerEntries { *; }
-keep class com.psiphon3.azadi.IranBypassListService { *; }
-keep class com.psiphon3.azadi.BypassDomainResolver { *; }
-keep class com.psiphon3.azadi.BundledIranCIDR { *; }
-keep class com.psiphon3.azadi.LanProxyRuntimeStore { *; }
-keep class com.psiphon3.azadi.LocalNetworkAddress { *; }

# Compose / AndroidX (defaults from dependencies)
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# Strip debug logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Repackage app code (not psiphonlibrary — referenced by name from tunnel)
-repackageclasses 'sk'
-allowaccessmodification
-overloadaggressively

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }
-dontwarn kotlin.coroutines.jvm.internal.SpillingKt

# Enums - R8 full mode can strip values() or valueOf() if used via reflection or IPC
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# AutoValue - keep generated implementation classes
-keep class com.psiphon3.AutoValue_* { *; }

# Tray preferences library
-keep class net.grandcentrix.tray.** { *; }
-dontwarn net.grandcentrix.tray.**
