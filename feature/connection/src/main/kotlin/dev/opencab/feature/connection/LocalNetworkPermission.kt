package dev.opencab.feature.connection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.opencab.core.data.connection.LocalNetworkAccess

/**
 * The local-network permission boundary for the connection screen.
 *
 * With `targetSdk = 36`, Android grants local-network access implicitly through `INTERNET`; no
 * runtime permission is requested and there is no rationale to show. When the app moves to
 * `targetSdk = 37`, [requiredPermission] returns `ACCESS_LOCAL_NETWORK` and the manifest must
 * declare it. Discovery and socket failures still surface as a typed
 * `ConnectionError.LocalNetworkPermissionDenied` from `:core:data`.
 */
internal object LocalNetworkPermission {
    fun requiredPermission(context: Context): String? = if (LocalNetworkAccess.requiresPermission(context)) {
        LocalNetworkAccess.PERMISSION
    } else {
        null
    }

    fun isGranted(context: Context): Boolean = LocalNetworkAccess.isGranted(context)

    /** Opens this app's system settings page so a blocked permission can be re-allowed. */
    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        runCatching { context.startActivity(intent) }
    }
}
