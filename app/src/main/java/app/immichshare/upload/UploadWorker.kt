package app.immichshare.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.immichshare.data.ImmichRepository
import app.immichshare.data.Settings
import app.immichshare.share.StagedAsset
import app.immichshare.share.UploadBatch
import app.immichshare.share.deleteBatch
import app.immichshare.share.readManifest
import app.immichshare.share.writeManifest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Uploads a staged batch, then applies album and tags, then cleans up.
 *
 * SPEC §8. Enqueued with a `NetworkType.CONNECTED` constraint and exponential
 * backoff, so a share made offline is queued rather than lost — the main
 * practical win over doing this in a browser.
 */
class UploadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val notifications = UploadNotifications(context)

    override suspend fun doWork(): Result {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return Result.failure()
        val batch = context.readManifest(batchId) ?: return Result.failure()

        val settings = Settings(context)
        if (!settings.currentServer().isComplete) {
            notifications.failure(context.getString(app.immichshare.R.string.error_not_configured))
            context.deleteBatch(batchId)
            return Result.failure()
        }

        val repo = ImmichRepository(settings)
        val total = batch.assets.size
        showProgress(0, total)

        // Kept index-aligned with batch.assets so a failed upload stays in the
        // manifest and is retried, rather than being dropped from it.
        val results = batch.assets.toMutableList()
        var failed = 0
        var retryable = false

        batch.assets.forEachIndexed { index, asset ->
            // Idempotency on retry: bytes already accepted are never re-sent.
            if (asset.assetId != null) return@forEachIndexed

            val file = File(asset.path)
            if (!file.exists()) {
                failed++
                return@forEachIndexed
            }

            try {
                val part = MultipartBody.Part.createFormData(
                    "assetData",
                    asset.displayName,
                    // SPEC §3.4: streams from disk. A burst share of 30 HEICs is
                    // ~150MB; reading that into a ByteArray OOMs a mid-range phone.
                    file.asRequestBody(asset.mimeType.toMediaType()),
                )
                val (assetId, duplicate) = repo.upload(
                    assetData = part,
                    fileCreatedAt = asset.createdAt,
                    fileModifiedAt = asset.modifiedAt,
                    filename = asset.displayName,
                )
                results[index] = asset.copy(assetId = assetId, duplicate = duplicate)
            } catch (e: HttpException) {
                // 4xx will never succeed on retry — a bad key stays bad.
                if (e.code() >= 500) retryable = true
                failed++
            } catch (_: IOException) {
                retryable = true
                failed++
            }

            showProgress(index + 1, total)
            // Persist progress so a retry resumes rather than restarting.
            context.writeManifest(batchId, batch.copy(assets = results))
        }

        val processed = results.filter { it.assetId != null }
        val assetIds = processed.mapNotNull { it.assetId }

        if (assetIds.isEmpty()) {
            return if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                notifications.result(uploaded = 0, duplicates = 0, failed = failed)
                context.deleteBatch(batchId)
                Result.failure()
            }
        }

        // Partial success is normal: if 3 of 5 uploaded, the 3 still get their
        // album and tags rather than the batch failing wholesale.
        val album = applyAlbum(repo, batch, assetIds)
        val tagFailure = applyTags(repo, batch, assetIds)

        if (album.error != null || tagFailure != null) {
            notifications.failure(
                context.getString(
                    app.immichshare.R.string.error_partial,
                    album.error ?: tagFailure.orEmpty(),
                )
            )
        } else {
            notifications.result(
                uploaded = processed.count { !it.duplicate },
                duplicates = processed.count { it.duplicate },
                failed = failed,
            )
            // Store the resolved album id — including the one just created, so
            // the next share adds to that album rather than creating a second
            // one with the same name.
            settings.rememberSelection(
                albumId = album.id,
                albumName = album.name,
                tagNames = batch.tagNames.toSet(),
            )
        }

        context.deleteBatch(batchId)
        return Result.success()
    }

    /** Resolved album after the fact, so a created album can be remembered by id. */
    private data class AlbumOutcome(
        val id: String? = null,
        val name: String? = null,
        val error: String? = null,
    )

    private suspend fun applyAlbum(
        repo: ImmichRepository,
        batch: UploadBatch,
        assetIds: List<String>,
    ): AlbumOutcome = try {
        when {
            !batch.newAlbumName.isNullOrBlank() -> {
                val created = repo.createAlbum(batch.newAlbumName, assetIds)
                AlbumOutcome(id = created.id, name = created.albumName)
            }

            !batch.albumId.isNullOrBlank() -> {
                repo.addToAlbum(batch.albumId, assetIds)
                AlbumOutcome(id = batch.albumId)
            }

            else -> AlbumOutcome()
        }
    } catch (e: Exception) {
        AlbumOutcome(error = context.getString(app.immichshare.R.string.error_album, e.message.orEmpty()))
    }

    /**
     * Promoting to foreground can be refused (background start restrictions on
     * API 31+, or a denied notification permission). The upload works either
     * way, so a refusal must not sink it.
     */
    private suspend fun showProgress(done: Int, total: Int) {
        runCatching { setForeground(notifications.progress(done, total)) }
    }

    private suspend fun applyTags(
        repo: ImmichRepository,
        batch: UploadBatch,
        assetIds: List<String>,
    ): String? = try {
        repo.applyTags(batch.tagNames, assetIds)
        null
    } catch (e: Exception) {
        context.getString(app.immichshare.R.string.error_tags, e.message.orEmpty())
    }

    companion object {
        private const val KEY_BATCH_ID = "batch_id"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context, batchId: String) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(Data.Builder().putString(KEY_BATCH_ID, batchId).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("upload-$batchId", ExistingWorkPolicy.KEEP, request)
        }

        const val TAG = "immich-upload"
    }
}
