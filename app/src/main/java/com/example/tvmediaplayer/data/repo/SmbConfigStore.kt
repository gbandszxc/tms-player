package com.example.tvmediaplayer.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tvmediaplayer.domain.model.SavedSmbConnection
import com.example.tvmediaplayer.domain.model.SmbConfig
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.smbDataStore by preferencesDataStore(name = "smb_config")

data class SmbConfigStoreState(
    val activeConfig: SmbConfig,
    val activeConnectionId: String?,
    val savedConnections: List<SavedSmbConnection>
)

class SmbConfigStore(private val appContext: Context) {

    suspend fun loadState(): SmbConfigStoreState {
        val preferences = appContext.smbDataStore.data
            .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }
            .first()

        val saved = decodeSaved(preferences[Keys.SAVED_CONNECTIONS_JSON])
        val activeId = preferences[Keys.ACTIVE_CONNECTION_ID]

        if (saved.isNotEmpty()) {
            val active = saved.firstOrNull { it.id == activeId } ?: saved.first()
            return SmbConfigStoreState(
                activeConfig = active.config,
                activeConnectionId = active.id,
                savedConnections = saved
            )
        }

        return SmbConfigStoreState(
            activeConfig = preferences.toLegacyConfig(),
            activeConnectionId = null,
            savedConnections = emptyList()
        )
    }

    suspend fun saveConnection(connection: SavedSmbConnection, activate: Boolean = true) {
        appContext.smbDataStore.edit { preferences ->
            val current = decodeSaved(preferences[Keys.SAVED_CONNECTIONS_JSON]).toMutableList()
            val index = current.indexOfFirst { it.id == connection.id }
            if (index >= 0) current[index] = connection else current.add(connection)

            preferences[Keys.SAVED_CONNECTIONS_JSON] = encodeSaved(current)
            if (activate) {
                preferences[Keys.ACTIVE_CONNECTION_ID] = connection.id
                writeLegacy(preferences, connection.config)
            }
        }
    }

    suspend fun setActiveConnection(id: String) {
        appContext.smbDataStore.edit { preferences ->
            val current = decodeSaved(preferences[Keys.SAVED_CONNECTIONS_JSON])
            val target = current.firstOrNull { it.id == id } ?: return@edit
            preferences[Keys.ACTIVE_CONNECTION_ID] = id
            writeLegacy(preferences, target.config)
        }
    }

    fun newConnectionId(): String = UUID.randomUUID().toString()

    private fun writeLegacy(preferences: androidx.datastore.preferences.core.MutablePreferences, config: SmbConfig) {
        preferences[Keys.HOST] = config.host
        preferences[Keys.SHARE] = config.share
        preferences[Keys.PATH] = config.path
        preferences[Keys.USERNAME] = config.username
        preferences[Keys.PASSWORD] = config.password
        preferences[Keys.GUEST] = config.guest
        preferences[Keys.SMB1] = config.smb1Enabled
    }

    private fun decodeSaved(json: String?): List<SavedSmbConnection> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val configObj = item.optJSONObject("config") ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        SavedSmbConnection(
                            id = id,
                            name = item.optString("name"),
                            config = SmbConfig(
                                host = configObj.optString("host"),
                                share = configObj.optString("share"),
                                path = configObj.optString("path"),
                                username = configObj.optString("username"),
                                password = configObj.optString("password"),
                                guest = configObj.optBoolean("guest", true),
                                smb1Enabled = configObj.optBoolean("smb1Enabled", false)
                            )
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSaved(list: List<SavedSmbConnection>): String {
        val array = JSONArray()
        list.forEach { saved ->
            array.put(
                JSONObject().apply {
                    put("id", saved.id)
                    put("name", saved.name)
                    put(
                        "config",
                        JSONObject().apply {
                            put("host", saved.config.host)
                            put("share", saved.config.share)
                            put("path", saved.config.path)
                            put("username", saved.config.username)
                            put("password", saved.config.password)
                            put("guest", saved.config.guest)
                            put("smb1Enabled", saved.config.smb1Enabled)
                        }
                    )
                }
            )
        }
        return array.toString()
    }

    private fun Preferences.toLegacyConfig(): SmbConfig =
        SmbConfig(
            host = this[Keys.HOST].orEmpty(),
            share = this[Keys.SHARE].orEmpty(),
            path = this[Keys.PATH].orEmpty(),
            username = this[Keys.USERNAME].orEmpty(),
            password = this[Keys.PASSWORD].orEmpty(),
            guest = this[Keys.GUEST] ?: true,
            smb1Enabled = this[Keys.SMB1] ?: false
        )

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val SHARE = stringPreferencesKey("share")
        val PATH = stringPreferencesKey("path")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val GUEST = booleanPreferencesKey("guest")
        val SMB1 = booleanPreferencesKey("smb1")
        val SAVED_CONNECTIONS_JSON = stringPreferencesKey("saved_connections_json")
        val ACTIVE_CONNECTION_ID = stringPreferencesKey("active_connection_id")
    }
}
