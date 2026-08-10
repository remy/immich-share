package app.immichshare.data

import okhttp3.MultipartBody
import retrofit2.HttpException
import java.io.IOException

/** Outcome of the settings screen's "Test connection" button. */
sealed interface ConnectionResult {
    data class Success(val email: String) : ConnectionResult
    /** The server answered, but rejected the key. */
    data object BadKey : ConnectionResult
    /** Rejected while proxy headers are configured — could be either credential. */
    data class Rejected(val code: Int) : ConnectionResult
    /** The server answered with something unexpected. */
    data class ServerError(val code: Int, val detail: String) : ConnectionResult
    /** Never reached the server at all. */
    data class Unreachable(val detail: String) : ConnectionResult
}

/**
 * Thin wrapper over [ImmichApi] that builds a client per call from the stored
 * settings, so a changed host or key takes effect immediately.
 */
class ImmichRepository(private val settings: Settings) {

    private suspend fun api(): ImmichApi {
        val config = settings.currentServer()
        require(config.isComplete) { "Immich server is not configured" }
        return ImmichClient.create(config.host, config.apiKey, settings.currentAccessHeaders())
    }

    /**
     * Uses `GET /api/users/me` rather than `/api/server/ping`: ping validates
     * the host but not the key, so it would report success with a bad key.
     *
     * Distinguishing "key rejected" from "couldn't reach the server" matters —
     * they need completely different fixes, and a self-hoster hits both.
     */
    suspend fun testConnection(
        host: String,
        apiKey: String,
        accessHeaders: AccessHeaders = AccessHeaders(),
    ): ConnectionResult {
        val normalised = normaliseHost(host)
        if (normalised.isBlank()) return ConnectionResult.Unreachable("No server address entered")
        if (apiKey.isBlank()) return ConnectionResult.BadKey

        return try {
            val user = ImmichClient.create(normalised, apiKey.trim(), accessHeaders).me()
            ConnectionResult.Success(user.email)
        } catch (e: HttpException) {
            when (e.code()) {
                // A proxy in front of Immich also answers 401/403, and blaming
                // the API key then sends you looking in the wrong place.
                401, 403 -> if (accessHeaders.isConfigured) {
                    ConnectionResult.Rejected(e.code())
                } else {
                    ConnectionResult.BadKey
                }

                else -> ConnectionResult.ServerError(e.code(), e.message().orEmpty())
            }
        } catch (e: IOException) {
            ConnectionResult.Unreachable(e.message ?: "Connection failed")
        } catch (e: IllegalArgumentException) {
            // Retrofit rejects a malformed base URL before any network call.
            ConnectionResult.Unreachable("That doesn't look like a valid address")
        }
    }

    suspend fun albums(): List<AlbumResponse> = api().albums()

    suspend fun tags(): List<TagResponse> = api().tags()

    /**
     * Returns the asset id and whether the server already had it.
     *
     * A duplicate still yields the existing asset's id, so album and tag
     * assignment must proceed normally — it is a success with a different
     * label, not an error.
     */
    suspend fun upload(
        assetData: MultipartBody.Part,
        fileCreatedAt: String,
        fileModifiedAt: String,
        filename: String,
    ): Pair<String, Boolean> {
        val parts = listOf(
            assetData,
            MultipartBody.Part.createFormData("fileCreatedAt", fileCreatedAt),
            MultipartBody.Part.createFormData("fileModifiedAt", fileModifiedAt),
            MultipartBody.Part.createFormData("filename", filename),
        )
        val response = api().uploadAsset(parts)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        val body = response.body() ?: error("Upload succeeded but returned no body")
        return body.id to body.isDuplicate
    }

    suspend fun createAlbum(name: String, assetIds: List<String>): AlbumResponse =
        api().createAlbum(CreateAlbumRequest(albumName = name, assetIds = assetIds))

    suspend fun addToAlbum(albumId: String, assetIds: List<String>): List<BulkIdResponse> =
        api().addAssetsToAlbum(albumId, BulkIdsRequest(ids = assetIds))

    /**
     * `PUT /api/tags` upserts by name and handles `/`-nested paths, so it covers
     * both existing and new tags in one call.
     *
     * The server returns 200 before the write has fully settled, so do not
     * verify by reading back straight afterwards.
     */
    suspend fun applyTags(tagNames: List<String>, assetIds: List<String>): Int {
        if (tagNames.isEmpty() || assetIds.isEmpty()) return 0
        val tags = api().upsertTags(TagUpsertRequest(tags = tagNames))
        val ids = tags.map { it.id }
        if (ids.isEmpty()) return 0
        return api().tagAssets(TagAssetsRequest(tagIds = ids, assetIds = assetIds)).count
    }
}
