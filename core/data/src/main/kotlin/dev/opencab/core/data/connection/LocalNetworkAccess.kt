package dev.opencab.core.data.connection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Boundary check for Android's local-network access rules.
 *
 * Current guidance (verified against Android's Local network permission documentation):
 * - Apps targeting API 36 or lower receive an implicit local-network grant through `INTERNET`.
 *   They must not declare or request `ACCESS_LOCAL_NETWORK`.
 * - Apps targeting API 37 (Android 17) or higher need the `ACCESS_LOCAL_NETWORK` runtime
 *   permission before NsdManager discovery or local TCP traffic.
 *
 * Android 16's opt-in `NEARBY_WIFI_DEVICES` compatibility arrangement is deprecated. This object
 * never requests a permission; it only reports whether the platform will enforce one. A blocked
 * socket or NsdManager call still maps to `ConnectionError.LocalNetworkPermissionDenied`.
 */
object LocalNetworkAccess {
    const val PERMISSION: String = "android.permission.ACCESS_LOCAL_NETWORK"
    const val TARGET_SDK_WITH_RUNTIME_PERMISSION: Int = 37

    /** True on Android 17+ for an app that targets 37 or higher. */
    fun requiresPermission(
        targetSdk: Int,
        sdkInt: Int,
    ): Boolean = targetSdk >= TARGET_SDK_WITH_RUNTIME_PERMISSION &&
        sdkInt >= TARGET_SDK_WITH_RUNTIME_PERMISSION

    fun requiresPermission(context: Context): Boolean = requiresPermission(
        targetSdk = context.applicationInfo.targetSdkVersion,
        sdkInt = Build.VERSION.SDK_INT,
    )

    /**
     * Whether local-network traffic is currently permitted. Lower target SDKs are implicitly
     * granted through `INTERNET`; this returns true without asking the runtime permission APIs.
     */
    fun isGranted(context: Context): Boolean = !requiresPermission(context) ||
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}
