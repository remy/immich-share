package app.immichshare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.immichshare.data.ConnectionResult
import app.immichshare.data.ImmichRepository
import app.immichshare.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val host: String = "",
    val apiKey: String = "",
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
            _state.update {
                it.copy(host = config.host, apiKey = config.apiKey, loaded = true)
            }
        }
    }

    fun onHostChange(value: String) = _state.update {
        it.copy(host = value, result = null, saved = false)
    }

    fun onApiKeyChange(value: String) = _state.update {
        it.copy(apiKey = value, result = null, saved = false)
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
            val result = repo.testConnection(current.host, current.apiKey)
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
