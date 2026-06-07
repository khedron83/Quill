package com.khedron.quill.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quill_settings")

data class QuillSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val verifySSL: Boolean = true,
)

@Singleton
class PreferencesDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val SERVER_URL = stringPreferencesKey("server_url")
    private val USERNAME = stringPreferencesKey("username")
    private val PASSWORD = stringPreferencesKey("password")
    private val VERIFY_SSL = booleanPreferencesKey("verify_ssl")

    val settings: Flow<QuillSettings> = context.dataStore.data.map { prefs ->
        QuillSettings(
            serverUrl = prefs[SERVER_URL] ?: "",
            username = prefs[USERNAME] ?: "",
            password = prefs[PASSWORD] ?: "",
            verifySSL = prefs[VERIFY_SSL] ?: true,
        )
    }

    suspend fun save(settings: QuillSettings) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = settings.serverUrl
            prefs[USERNAME] = settings.username
            prefs[PASSWORD] = settings.password
            prefs[VERIFY_SSL] = settings.verifySSL
        }
    }
}
