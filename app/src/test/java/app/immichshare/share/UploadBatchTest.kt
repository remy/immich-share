package app.immichshare.share

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest is what survives a process death between staging and uploading,
 * so it has to round-trip exactly — including the assetId that makes a retry
 * skip bytes it already sent.
 */
class UploadBatchTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun asset(name: String, assetId: String? = null) = StagedAsset(
        path = "/cache/pending/batch/$name",
        displayName = name,
        mimeType = "image/jpeg",
        sizeBytes = 4_200_000,
        createdAt = "2026-07-14T17:22:03Z",
        modifiedAt = "2026-07-14T17:22:03Z",
        metadata = MetadataFlags(hasDate = true, hasGps = true, hasCamera = true),
        assetId = assetId,
    )

    @Test
    fun `batch round-trips through the manifest`() {
        val batch = UploadBatch(
            assets = listOf(asset("a.jpg"), asset("b.heic", assetId = "uuid-1")),
            albumId = "album-uuid",
            tagNames = listOf("Holiday/2026", "Beach"),
        )

        val restored = json.decodeFromString<UploadBatch>(json.encodeToString(batch))

        assertEquals(batch, restored)
        assertEquals("uuid-1", restored.assets[1].assetId)
        assertNull(restored.assets[0].assetId)
        assertEquals(listOf("Holiday/2026", "Beach"), restored.tagNames)
    }

    @Test
    fun `unknown fields from a newer version do not break decoding`() {
        val forwardCompatible = """
            {"assets":[],"albumId":null,"tagNames":[],"somethingNew":true}
        """.trimIndent()

        val restored = json.decodeFromString<UploadBatch>(forwardCompatible)

        assertTrue(restored.assets.isEmpty())
    }

    @Test
    fun `a new album name and an existing album id are mutually exclusive in practice`() {
        val newAlbum = UploadBatch(assets = listOf(asset("a.jpg")), newAlbumName = "Summer")

        assertNull(newAlbum.albumId)
        assertEquals("Summer", newAlbum.newAlbumName)
    }
}
