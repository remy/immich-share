package app.immichshare.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.Instant

/**
 * Uploads staged assets, then applies album and tags, then cleans up.
 *
 * SPEC §8. Enqueued with a `NetworkType.CONNECTED` constraint and exponential
 * backoff, so a share made offline is queued rather than lost.
 *
 * Scaffolding: [buildUploadBody] is the piece that must not regress — see §3.4.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        /**
         * SPEC §3.4: `File.asRequestBody()` streams. A burst share of 30 HEICs
         * is ~150MB, so never read the file into a `ByteArray`, and never wrap
         * this body in anything that buffers.
         */
        fun buildUploadBody(
            staged: File,
            displayName: String,
            mimeType: String,
            createdAt: Instant,
            modifiedAt: Instant,
        ): MultipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "assetData",
                displayName,
                staged.asRequestBody(mimeType.toMediaType()),
            )
            .addFormDataPart("fileCreatedAt", createdAt.toString())
            .addFormDataPart("fileModifiedAt", modifiedAt.toString())
            .addFormDataPart("filename", displayName)
            .build()
    }
}
