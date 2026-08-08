package app.immichshare.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Matches the normalisation the sibling web tools use: trim, drop trailing
 * slashes, default to https when no scheme was typed.
 *
 * A bare host or an IP with a port both work, which is what a self-hoster is
 * most likely to type.
 */
fun normaliseHost(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    return if (trimmed.isEmpty() || trimmed.contains("://")) trimmed else "https://$trimmed"
}

/**
 * Immich treats a tag as a `/`-separated path, so surrounding whitespace and
 * stray separators would create tags like `" Beach"` or an empty nesting level.
 */
fun normaliseTag(raw: String): String = raw
    .split('/')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString("/")

data class ServerConfig(
    val host: String,
    val apiKey: String,
) {
    val isComplete: Boolean get() = host.isNotBlank() && apiKey.isNotBlank()
}

/**
 * Server host, API key and last-used album/tags.
 *
 * SPEC §9: stored unencrypted in app-private storage, and kept off cloud backup
 * via `allowBackup=false` plus the data-extraction rules. App-private storage is
 * the real boundary on a non-rooted device.
 */
class Settings(private val context: Context) {

    val server: Flow<ServerConfig> = context.dataStore.data.map {
        ServerConfig(
            host = it[KEY_HOST].orEmpty(),
            apiKey = it[KEY_API_KEY].orEmpty(),
        )
    }

    val lastAlbumId: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_ALBUM_ID] }
    val lastAlbumName: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_ALBUM_NAME] }
    val lastTagNames: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_LAST_TAG_NAMES].orEmpty() }
    val hasOnboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] == true }

    /**
     * Tags pre-selected on every share, set once in settings.
     *
     * Distinct from [lastTagNames], which drifts with whatever was used last.
     * When these are set they win, so "everything from this phone gets tagged
     * X" stays true rather than depending on the previous share.
     */
    val defaultTagNames: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_DEFAULT_TAG_NAMES].orEmpty() }

    suspend fun currentDefaultTags(): Set<String> = defaultTagNames.first()

    suspend fun setDefaultTags(tags: Set<String>) {
        context.dataStore.edit { it[KEY_DEFAULT_TAG_NAMES] = tags.map(::normaliseTag).toSet() }
    }

    suspend fun currentServer(): ServerConfig = server.first()

    suspend fun setServer(host: String, apiKey: String) {
        context.dataStore.edit {
            it[KEY_HOST] = normaliseHost(host)
            it[KEY_API_KEY] = apiKey.trim()
            it[KEY_ONBOARDED] = true
        }
    }

    /**
     * Written only after a *successful* upload — a failed one should not change
     * what the next share defaults to.
     */
    suspend fun rememberSelection(albumId: String?, albumName: String?, tagNames: Set<String>) {
        context.dataStore.edit {
            if (albumId != null) it[KEY_LAST_ALBUM_ID] = albumId else it.remove(KEY_LAST_ALBUM_ID)
            if (albumName != null) {
                it[KEY_LAST_ALBUM_NAME] = albumName
            } else {
                it.remove(KEY_LAST_ALBUM_NAME)
            }
            it[KEY_LAST_TAG_NAMES] = tagNames
        }
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("host")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_LAST_ALBUM_ID = stringPreferencesKey("last_album_id")
        val KEY_LAST_ALBUM_NAME = stringPreferencesKey("last_album_name")
        val KEY_LAST_TAG_NAMES = stringSetPreferencesKey("last_tag_names")
        val KEY_DEFAULT_TAG_NAMES = stringSetPreferencesKey("default_tag_names")
        val KEY_ONBOARDED = booleanPreferencesKey("has_onboarded")
    }
}
