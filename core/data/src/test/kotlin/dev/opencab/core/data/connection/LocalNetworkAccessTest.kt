package dev.opencab.core.data.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun targetSdk36GetsTheImplicitInternetGrant() {
        assertFalse(LocalNetworkAccess.requiresPermission(targetSdk = 36, sdkInt = 36))
        assertFalse(LocalNetworkAccess.requiresPermission(targetSdk = 36, sdkInt = 37))
        assertFalse(LocalNetworkAccess.requiresPermission(targetSdk = 35, sdkInt = 36))
    }

    @Test
    fun targetSdk37AndAndroid17RequireTheRuntimePermission() {
        assertTrue(LocalNetworkAccess.requiresPermission(targetSdk = 37, sdkInt = 37))
        assertTrue(LocalNetworkAccess.requiresPermission(targetSdk = 38, sdkInt = 37))
        assertFalse(LocalNetworkAccess.requiresPermission(targetSdk = 37, sdkInt = 36))
    }

    @Test
    fun permissionNameMatchesTheAndroid17Constant() {
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetworkAccess.PERMISSION)
    }
}
