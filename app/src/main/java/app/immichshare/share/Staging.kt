package app.immichshare.share

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.time.Instant

/**
 * SPEC §3.2: `MediaProvider` redacts GPS from images unless the reading app
 * holds `ACCESS_MEDIA_LOCATION` and asks for the original.
 *
 * Two guards are needed. `setRequireOriginal` only exists from API 29 — which
 * is also when redaction began, so on 26..28 the original is served anyway. And
 * it only accepts MediaStore URIs, throwing for anything else (file managers,
 * the Downloads provider, another app's FileProvider), so it falls back.
 *
 * A redaction failure must never block the upload — degrade and surface it.
 */
fun ContentResolver.openOriginal(uri: Uri): InputStream? {
    val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            MediaStore.setRequireOriginal(uri)
        } catch (_: Exception) {
            uri
        }
    } else {
        uri
    }
    return try {
        openInputStream(target)
    } catch (_: SecurityException) {
        openInputStream(uri)
    }
}

/**
 * SPEC §3.3: EXIF `DateTimeOriginal` is a naive local timestamp. Let
 * androidx's [ExifInterface] resolve it — it applies `OffsetTimeOriginal` when
 * present and falls back to the device zone when it isn't. Never hand-roll the
 * parse, and always read from the *staged copy*, since the URI grant may
 * already be gone.
 *
 * Caveat the spec did not anticipate: as of exifinterface 1.4.2,
 * `getDateTimeOriginal()` is annotated `@RestrictTo(LIBRARY)`. It is public in
 * the bytecode and has behaved stably for years, so it is used here
 * deliberately — but it is not guaranteed API, so pin the exifinterface version
 * and re-check this on every upgrade. The alternative is parsing
 * `TAG_DATETIME_ORIGINAL` against `TAG_OFFSET_TIME_ORIGINAL` by hand, which is
 * exactly the error-prone path §3.3 warns against.
 */
@SuppressLint("RestrictedApi")
fun capturedAt(staged: File, fallbackMillis: Long): Instant {
    val millis = staged.inputStream().use { stream ->
        runCatching { ExifInterface(stream).dateTimeOriginal }.getOrNull()
    } ?: fallbackMillis
    return Instant.ofEpochMilli(millis)
}
