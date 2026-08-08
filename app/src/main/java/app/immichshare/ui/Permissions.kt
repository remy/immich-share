package app.immichshare.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * The permission set that keeps metadata intact.
 *
 * `ACCESS_MEDIA_LOCATION` is the one that matters: without it MediaProvider
 * strips GPS from every image handed to this app, and photos land in Immich
 * with no location. It is a dangerous permission, so declaring it in the
 * manifest is not enough — it must be granted at runtime. On API 33+ it only
 * grants alongside media read access, so they are requested together.
 */
fun requiredMediaPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            add(Manifest.permission.READ_MEDIA_IMAGES)

        else -> add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

fun notificationPermission(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * True when location metadata will survive. This is the grant users most often
 * miss, and the one whose absence is otherwise invisible.
 */
fun Context.hasMediaLocationAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION)

fun Context.hasNotificationAccess(): Boolean =
    notificationPermission().all { isGranted(it) }

/** Route here when a permission was permanently denied and cannot be re-asked. */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:$packageName".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
