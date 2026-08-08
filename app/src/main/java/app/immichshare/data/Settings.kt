package app.immichshare.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Matches the normalisation the sibling web tools use: trim, drop trailing
 * slashes, default to https when no scheme was typed.
 */
fun normaliseHost(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    return if (trimmed.isEmpty() || trimmed.contains("://")) trimmed else "https://$trimmed"
}

/**
 * Server host, API key and last-used album/tags.
 *
 * SPEC §9: stored unencrypted in app-private storage, and kept off cloud backup
 * via `allowBackup=false` plus the data-extraction rules.
 */
class Settings(private val context: Context) {

    val host: Flow<String?> = context.dataStore.data.map { it[KEY_HOST] }
    val apiKey: Flow<String?> = context.dataStore.data.map { it[KEY_API_KEY] }
    val lastAlbumId: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_ALBUM_ID] }
    val lastTagNames: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_LAST_TAG_NAMES].orEmpty() }

    suspend fun setServer(host: String, apiKey: String) {
        context.dataStore.edit {
            it[KEY_HOST] = normaliseHost(host)
            it[KEY_API_KEY] = apiKey
        }
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("host")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_LAST_ALBUM_ID = stringPreferencesKey("last_album_id")
        val KEY_LAST_TAG_NAMES = stringSetPreferencesKey("last_tag_names")
    }
}
