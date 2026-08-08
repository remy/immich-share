package app.immichshare.share

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }

/** Root for staged batches: `cacheDir/pending/<batchId>/`. */
fun Context.pendingRoot(): File = File(cacheDir, "pending")

fun Context.batchDir(batchId: String): File = File(pendingRoot(), batchId)

private fun File.manifest(): File = File(this, "manifest.json")

/**
 * SPEC §3.2: `MediaProvider` redacts GPS from images unless the reading app
 * holds `ACCESS_MEDIA_LOCATION` and explicitly asks for the original.
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
 * SPEC §3.1: the `content://` grant on an `ACTION_SEND` URI is scoped to the
 * receiving activity and cannot be made persistable. Copy the bytes — and read
 * the display name, MIME type and size, which also come from the resolver —
 * while the grant is still alive. Everything after this point works off disk.
 *
 * Runs on IO. Returns only the assets that could actually be read; a single
 * unreadable image should not sink the whole share.
 */
suspend fun Context.stageAll(batchId: String, uris: List<Uri>): List<StagedAsset> =
    withContext(Dispatchers.IO) {
        val dir = batchDir(batchId).apply { mkdirs() }
        uris.mapNotNull { uri -> stageOne(dir, uri) }
    }

private fun Context.stageOne(dir: File, uri: Uri): StagedAsset? = try {
    val (displayName, size) = queryNameAndSize(uri)
    val mime = contentResolver.getType(uri) ?: "image/*"
    val target = File(dir, UUID.randomUUID().toString())

    // Straight byte copy: never decode to a Bitmap, never re-encode, never
    // "fix" the orientation. This is what keeps EXIF/XMP intact, and it is
    // also why HEIC works without any special handling.
    contentResolver.openOriginal(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Could not open $uri")

    val flags = readMetadataFlags(target)
    val created = capturedAt(target, uri, fallbackMillis = target.lastModified())

    StagedAsset(
        path = target.absolutePath,
        displayName = displayName ?: target.name,
        mimeType = mime,
        sizeBytes = if (size > 0) size else target.length(),
        createdAt = created.toString(),
        modifiedAt = created.toString(),
        metadata = flags,
    )
} catch (_: Exception) {
    null
}

private fun Context.queryNameAndSize(uri: Uri): Pair<String?, Long> {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                cursor.getString(nameIndex)
            } else {
                null
            }
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                0L
            }
            return name to size
        }
    }
    return null to 0L
}

/**
 * SPEC §3.3: EXIF `DateTimeOriginal` is a naive local timestamp with no offset.
 * Let androidx's [ExifInterface] resolve it — it applies `OffsetTimeOriginal`
 * when present and falls back to the device zone when it isn't. Never hand-roll
 * the parse. Read from the *staged copy*: the URI grant may already be gone,
 * and it avoids a second trip through MediaProvider.
 *
 * Fallback order is DateTimeOriginal, then DateTime, then MediaStore's
 * DATE_TAKEN, then the file's own mtime. Sending "now" would file a photo taken
 * last year under today in Immich, which is exactly the failure to avoid.
 *
 * Caveat the spec did not anticipate: as of exifinterface 1.4.2 these getters
 * are annotated `@RestrictTo(LIBRARY)`. They are public in the bytecode and
 * have behaved stably for years, so they are used deliberately — but pin the
 * version and re-check on upgrade.
 */
@SuppressLint("RestrictedApi")
private fun Context.capturedAt(staged: File, source: Uri, fallbackMillis: Long): Instant {
    val fromExif = runCatching {
        staged.inputStream().use { stream ->
            val exif = ExifInterface(stream)
            exif.dateTimeOriginal ?: exif.dateTime
        }
    }.getOrNull()

    val millis = fromExif ?: dateTakenFromMediaStore(source) ?: fallbackMillis
    return Instant.ofEpochMilli(millis)
}

private fun Context.dateTakenFromMediaStore(uri: Uri): Long? = runCatching {
    contentResolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns.DATE_TAKEN),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
            cursor.getLong(index).takeIf { it > 0 }
        } else {
            null
        }
    }
}.getOrNull()

/**
 * Reads the staged file once to report what survived. Costs one extra
 * ExifInterface read of a file already on disk, and turns an invisible
 * failure into a visible one.
 */
@SuppressLint("RestrictedApi")
private fun readMetadataFlags(staged: File): MetadataFlags = runCatching {
    staged.inputStream().use { stream ->
        val exif = ExifInterface(stream)
        MetadataFlags(
            hasDate = exif.dateTimeOriginal != null || exif.dateTime != null,
            hasGps = exif.latLong != null,
            hasCamera = !exif.getAttribute(ExifInterface.TAG_MAKE).isNullOrBlank() ||
                !exif.getAttribute(ExifInterface.TAG_MODEL).isNullOrBlank(),
        )
    }
}.getOrDefault(MetadataFlags())

fun Context.writeManifest(batchId: String, batch: UploadBatch) {
    val dir = batchDir(batchId).apply { mkdirs() }
    dir.manifest().writeText(json.encodeToString(batch))
}

fun Context.readManifest(batchId: String): UploadBatch? {
    val file = batchDir(batchId).manifest()
    if (!file.exists()) return null
    return runCatching { json.decodeFromString<UploadBatch>(file.readText()) }.getOrNull()
}

fun Context.deleteBatch(batchId: String) {
    batchDir(batchId).deleteRecursively()
}

/**
 * SPEC §3.1: a crash mid-share would otherwise leak staged bytes forever.
 *
 * Age alone is not a safe test — a share made just before a week in a dead spot
 * is still legitimately queued, and deleting its bytes would destroy the upload
 * WorkManager is patiently waiting to run. So a batch is only swept when
 * WorkManager has no unfinished job for it.
 */
suspend fun Context.sweepStaleBatches(now: Long, maxAgeMillis: Long = 24 * 60 * 60 * 1000L) {
    withContext(Dispatchers.IO) {
        val root = pendingRoot()
        if (!root.isDirectory) return@withContext

        root.listFiles()?.forEach { dir ->
            if (now - dir.lastModified() <= maxAgeMillis) return@forEach
            if (!hasPendingUpload(dir.name)) dir.deleteRecursively()
        }
    }
}

private fun Context.hasPendingUpload(batchId: String): Boolean = runCatching {
    WorkManager.getInstance(this)
        .getWorkInfosForUniqueWork("upload-$batchId")
        .get()
        .any { !it.state.isFinished }
}.getOrDefault(true) // On doubt, keep the bytes: losing a queued upload is worse.
