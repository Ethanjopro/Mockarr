package dev.mockarr.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Permissions the mock session service needs before it can start. */
internal fun Context.missingMockPermissions(): List<String> = buildList {
    if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}
