package app.immichshare.share

import kotlinx.serialization.Serializable

/**
 * What the staged bytes turned out to contain. Surfaced as chips on the confirm
 * sheet so a silent metadata loss becomes a visible one — the whole point of
 * the app is that nothing gets stripped, and redaction is otherwise invisible.
 */
@Serializable
data class MetadataFlags(
    val hasDate: Boolean = false,
    val hasGps: Boolean = false,
    val hasCamera: Boolean = false,
)

/**
 * One image copied into app-private cache, with everything that had to be read
 * from the ContentResolver while the URI grant was still alive.
 */
@Serializable
data class StagedAsset(
    val path: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** ISO-8601 instant; capture date where known, never "now" if avoidable. */
    val createdAt: String,
    val modifiedAt: String,
    val metadata: MetadataFlags,
    /** Set once uploaded, so a retry does not re-send the bytes. */
    val assetId: String? = null,
    val duplicate: Boolean = false,
)

/** The unit of work handed to [app.immichshare.upload.UploadWorker]. */
@Serializable
data class UploadBatch(
    val assets: List<StagedAsset>,
    val albumId: String? = null,
    val newAlbumName: String? = null,
    val tagNames: List<String> = emptyList(),
)
