package app.immichshare.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `POST /api/assets`. `status` is `created` or `duplicate`; both carry a usable id. */
@Serializable
data class AssetUploadResponse(
    val id: String,
    val status: String,
) {
    val isDuplicate: Boolean get() = status == "duplicate"
}

/** `GET /api/users/me` — validates host *and* key in one call. */
@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String? = null,
)

@Serializable
data class AlbumResponse(
    val id: String,
    val albumName: String,
    val assetCount: Int = 0,
)

@Serializable
data class CreateAlbumRequest(
    val albumName: String,
    val assetIds: List<String> = emptyList(),
)

@Serializable
data class BulkIdsRequest(
    val ids: List<String>,
)

@Serializable
data class BulkIdResponse(
    val id: String,
    val success: Boolean,
    val error: String? = null,
)

@Serializable
data class TagResponse(
    val id: String,
    val name: String,
    val value: String,
    val parentId: String? = null,
)

/** `PUT /api/tags` upserts by name and handles `/`-nested paths. */
@Serializable
data class TagUpsertRequest(
    val tags: List<String>,
)

@Serializable
data class TagAssetsRequest(
    val tagIds: List<String>,
    val assetIds: List<String>,
)

@Serializable
data class TagAssetsResponse(
    @SerialName("count") val count: Int,
)
