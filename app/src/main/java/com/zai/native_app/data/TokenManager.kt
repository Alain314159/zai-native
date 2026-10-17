package com.zai.native_app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zai_auth")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val MODEL_KEY = stringPreferencesKey("selected_model")
        private const val PREFS_NAME = "zai_auth_fallback"
        private const val TOKEN_PREF = "token"
        private const val MODEL_PREF = "model"
    }

    // Fallback SharedPreferences for synchronous access (OkHttp interceptor)
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[MODEL_KEY] ?: "glm-5.3" }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
        sharedPrefs.edit().putString(TOKEN_PREF, token).apply()
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
        sharedPrefs.edit().remove(TOKEN_PREF).apply()
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { it[MODEL_KEY] = model }
        sharedPrefs.edit().putString(MODEL_PREF, model).apply()
    }

    fun getTokenSync(): String? {
        return sharedPrefs.getString(TOKEN_PREF, null)
    }

    fun getModelSync(): String {
        return sharedPrefs.getString(MODEL_PREF, "glm-5.3") ?: "glm-5.3"
    }
}
