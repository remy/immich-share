package app.immichshare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.immichshare.data.AccessHeaders
import app.immichshare.data.ConnectionResult
import app.immichshare.data.ImmichRepository
import app.immichshare.data.Settings
import app.immichshare.data.normaliseTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val host: String = "",
    val apiKey: String = "",
    val defaultTags: Set<String> = emptySet(),
    val accessHeaders: AccessHeaders = AccessHeaders(),
    val loaded: Boolean = false,
    val testing: Boolean = false,
    val result: ConnectionResult? = null,
    val saved: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val repo = ImmichRepository(settings)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settings.currentServer()
            val tags = settings.currentDefaultTags()
            val headers = settings.currentAccessHeaders()
            _state.update {
                it.copy(
                    host = config.host,
                    apiKey = config.apiKey,
                    defaultTags = tags,
                    accessHeaders = headers,
                    loaded = true,
                )
            }
        }
    }

    /** Saved immediately rather than behind the server card's button — these are independent. */
    fun addDefaultTag(raw: String) {
        val tag = normaliseTag(raw)
        if (tag.isEmpty() || tag in _state.value.defaultTags) return
        updateDefaultTags(_state.value.defaultTags + tag)
    }

    fun removeDefaultTag(tag: String) = updateDefaultTags(_state.value.defaultTags - tag)

    private fun updateDefaultTags(tags: Set<String>) {
        _state.update { it.copy(defaultTags = tags) }
        viewModelScope.launch { settings.setDefaultTags(tags) }
    }

    fun onHostChange(value: String) = _state.update {
        it.copy(host = value, result = null, saved = false)
    }

    fun onApiKeyChange(value: String) = _state.update {
        it.copy(apiKey = value, result = null, saved = false)
    }

    fun onAccessHeadersChange(headers: AccessHeaders) = _state.update {
        it.copy(accessHeaders = headers, result = null, saved = false)
    }

    /**
     * Saves first, then tests, so a working config is persisted even if the
     * server happens to be unreachable at that moment.
     */
    fun saveAndTest() {
        val current = _state.value
        if (current.testing) return

        viewModelScope.launch {
            _state.update { it.copy(testing = true, result = null) }
            settings.setServer(current.host, current.apiKey)
            settings.setAccessHeaders(current.accessHeaders)
            val result = repo.testConnection(
                host = current.host,
                apiKey = current.apiKey,
                accessHeaders = current.accessHeaders,
            )
            _state.update {
                it.copy(
                    testing = false,
                    result = result,
                    saved = true,
                    // Reflect the normalised form back so the user sees what
                    // was actually stored.
                    host = settings.currentServer().host,
                )
            }
        }
    }
}
