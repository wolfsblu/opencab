package dev.opencab.feature.connection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The runtime permission OpenCab uses for local-network discovery.
 *
 * With the app still targeting SDK 36, `NEARBY_WIFI_DEVICES` is the permission that gates nearby
 * Wi-Fi discovery on Android 13 and later. The rationale and the Settings deep link are tied to
 * this permission.
 *
 * Android 17 introduces `ACCESS_LOCAL_NETWORK` for apps targeting SDK 37. When this app moves to
 * target 37, change the permission name here and declare it in the manifest; the rest of the screen
 * flow stays the same.
 */
internal object LocalNetworkPermission {
    val name: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            null
        }

    fun isGranted(context: Context): Boolean {
        val permission = name ?: return true
        return ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Opens this app's system settings page so a blocked permission can be re-allowed. */
    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        runCatching { context.startActivity(intent) }
    }
}
