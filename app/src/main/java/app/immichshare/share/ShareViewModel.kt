package app.immichshare.share

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.immichshare.data.AlbumResponse
import app.immichshare.data.ImmichRepository
import app.immichshare.data.Settings
import app.immichshare.data.TagResponse
import app.immichshare.data.normaliseTag
import app.immichshare.ui.AlbumSelection
import app.immichshare.upload.UploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ShareUiState(
    val configured: Boolean? = null,
    val staging: Boolean = true,
    val assets: List<StagedAsset> = emptyList(),
    val albums: List<AlbumResponse> = emptyList(),
    val tags: List<TagResponse> = emptyList(),
    val pickersLoading: Boolean = true,
    val albumSelection: AlbumSelection = AlbumSelection.None,
    val selectedTags: Set<String> = emptySet(),
    val done: Boolean = false,
)

class ShareViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val repo = ImmichRepository(settings)
    private val batchId = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    /**
     * SPEC §3.1: called from `ShareActivity.onCreate`, before anything renders.
     * The URI read grant dies with the activity and cannot be persisted, so the
     * bytes must be copied now — not when the worker eventually runs.
     */
    fun stage(uris: List<Uri>) {
        viewModelScope.launch {
            val configured = settings.currentServer().isComplete
            _state.update { it.copy(configured = configured) }

            val staged = getApplication<Application>().stageAll(batchId, uris)
            _state.update { it.copy(staging = false, assets = staged) }

            if (configured) loadPickers()
        }
    }

    /**
     * Fetched in parallel with staging. A failure here is not fatal: the user
     * can still type an album or tag name, and `PUT /api/tags` upserts by name,
     * so a tag typed offline resolves correctly when the upload runs.
     */
    private fun loadPickers() {
        viewModelScope.launch {
            val albums = runCatching { repo.albums() }.getOrDefault(emptyList())
            val tags = runCatching { repo.tags() }.getOrDefault(emptyList())

            val lastAlbumId = settings.lastAlbumId.first()
            val lastAlbumName = settings.lastAlbumName.first()

            // An explicit default wins over last-used: "everything from this
            // phone gets tagged X" should stay true regardless of what the
            // previous share happened to use.
            val defaultTags = settings.currentDefaultTags()
            val startingTags = defaultTags.ifEmpty { settings.lastTagNames.first() }

            val restored = when {
                lastAlbumId != null -> albums.firstOrNull { it.id == lastAlbumId }
                    ?.let { AlbumSelection.Existing(it.id, it.albumName) }

                lastAlbumName != null -> AlbumSelection.New(lastAlbumName)
                else -> null
            }

            _state.update {
                it.copy(
                    albums = albums,
                    tags = tags,
                    pickersLoading = false,
                    albumSelection = restored ?: it.albumSelection,
                    selectedTags = if (it.selectedTags.isEmpty()) startingTags else it.selectedTags,
                )
            }
        }
    }

    fun selectAlbum(selection: AlbumSelection) = _state.update { it.copy(albumSelection = selection) }

    fun toggleTag(value: String) = _state.update { current ->
        current.copy(
            selectedTags = if (value in current.selectedTags) {
                current.selectedTags - value
            } else {
                current.selectedTags + value
            }
        )
    }

    fun addTag(value: String) {
        val tag = normaliseTag(value)
        if (tag.isNotEmpty()) _state.update { it.copy(selectedTags = it.selectedTags + tag) }
    }

    fun upload() {
        val current = _state.value
        if (current.staging || current.assets.isEmpty()) return

        val context = getApplication<Application>()
        val batch = UploadBatch(
            assets = current.assets,
            albumId = (current.albumSelection as? AlbumSelection.Existing)?.id,
            newAlbumName = (current.albumSelection as? AlbumSelection.New)?.name,
            tagNames = current.selectedTags.toList(),
        )
        context.writeManifest(batchId, batch)
        UploadWorker.enqueue(context, batchId)

        _state.update { it.copy(done = true) }
    }

    /** Cancelled before upload: the staged copies are dead weight. */
    fun discard() {
        getApplication<Application>().deleteBatch(batchId)
    }
}
