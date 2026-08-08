package app.immichshare.data

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Immich REST surface used by this app, per SPEC §4.
 *
 * Auth is the `x-api-key` header, added by an interceptor in [ImmichClient].
 * Do not add `deviceAssetId` / `deviceId` to the upload: the current spec drops
 * them, and unknown fields risk DTO validation rejection.
 */
interface ImmichApi {

    @GET("api/users/me")
    suspend fun me(): UserResponse

    /**
     * Every field is passed as a pre-built [MultipartBody.Part], including the
     * text ones.
     *
     * `@Part("name") String` would route the value through the converter, and
     * the kotlinx-serialization converter JSON-encodes a String — so
     * `fileCreatedAt` would arrive as `"2026-07-14T17:22:03Z"`, quotes and all,
     * and the date would not parse. Building the parts by hand sidesteps the
     * converter entirely.
     */
    @Multipart
    @POST("api/assets")
    suspend fun uploadAsset(@Part parts: List<MultipartBody.Part>): Response<AssetUploadResponse>

    @GET("api/albums")
    suspend fun albums(): List<AlbumResponse>

    @POST("api/albums")
    suspend fun createAlbum(@Body body: CreateAlbumRequest): AlbumResponse

    @PUT("api/albums/{id}/assets")
    suspend fun addAssetsToAlbum(
        @Path("id") albumId: String,
        @Body body: BulkIdsRequest,
    ): List<BulkIdResponse>

    @GET("api/tags")
    suspend fun tags(): List<TagResponse>

    @PUT("api/tags")
    suspend fun upsertTags(@Body body: TagUpsertRequest): List<TagResponse>

    @PUT("api/tags/assets")
    suspend fun tagAssets(@Body body: TagAssetsRequest): TagAssetsResponse
}
