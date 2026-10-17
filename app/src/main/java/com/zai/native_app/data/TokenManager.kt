package com.zai.native_app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zai_auth")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val MODEL_KEY = stringPreferencesKey("selected_model")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[MODEL_KEY] ?: "glm-5.3" }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(TOKEN_KEY) }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { it[MODEL_KEY] = model }
    }

    fun getTokenSync(): String? {
        // Blocking read for OkHttp interceptor
        var token: String? = null
        kotlinx.coroutines.runBlocking {
            context.dataStore.data.collect {
                token = it[TOKEN_KEY]
                return@collect
            }
        }
        return token
    }
}

// Need import for edit
