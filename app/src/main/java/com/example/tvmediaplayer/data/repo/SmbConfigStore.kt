package com.example.tvmediaplayer.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tvmediaplayer.domain.model.SmbConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.smbDataStore by preferencesDataStore(name = "smb_config")

class SmbConfigStore(private val appContext: Context) {

    val configFlow: Flow<SmbConfig> = appContext.smbDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { preferences -> preferences.toConfig() }

    suspend fun load(): SmbConfig = configFlow.first()

    suspend fun save(config: SmbConfig) {
        appContext.smbDataStore.edit { preferences ->
            preferences[Keys.HOST] = config.host
            preferences[Keys.SHARE] = config.share
            preferences[Keys.PATH] = config.path
            preferences[Keys.USERNAME] = config.username
            preferences[Keys.PASSWORD] = config.password
            preferences[Keys.GUEST] = config.guest
            preferences[Keys.SMB1] = config.smb1Enabled
        }
    }

    private fun Preferences.toConfig(): SmbConfig =
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
    }
}

