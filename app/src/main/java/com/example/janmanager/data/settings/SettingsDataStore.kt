package com.example.janmanager.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    val aiSelectionFlow: Flow<String> = context.dataStore.data.map { it[AI_SELECTION_KEY] ?: "GEMINI" }
    val pasteModeFlow: Flow<String> = context.dataStore.data.map { it[PASTE_MODE_KEY] ?: "AUTO" }
    val isItfEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[ITF_ENABLED_KEY] ?: false }
    val selectorConfigFlow: Flow<String> = context.dataStore.data.map { it[SELECTOR_CONFIG_KEY] ?: "" }
    val scanSoundEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[SCAN_SOUND_KEY] ?: true }

    suspend fun setAiSelection(selection: String) {
        context.dataStore.edit { it[AI_SELECTION_KEY] = selection }
    }

    suspend fun setPasteMode(mode: String) {
        context.dataStore.edit { it[PASTE_MODE_KEY] = mode }
    }

    suspend fun setItfEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ITF_ENABLED_KEY] = enabled }
    }

    suspend fun setSelectorConfig(config: String) {
        context.dataStore.edit { it[SELECTOR_CONFIG_KEY] = config }
    }
    
    suspend fun setScanSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SCAN_SOUND_KEY] = enabled }
    }

    companion object {
        private val AI_SELECTION_KEY = stringPreferencesKey("ai_selection")
        private val PASTE_MODE_KEY = stringPreferencesKey("paste_mode")
        private val ITF_ENABLED_KEY = booleanPreferencesKey("itf_enabled")
        private val SELECTOR_CONFIG_KEY = stringPreferencesKey("selector_config")
        private val SCAN_SOUND_KEY = booleanPreferencesKey("scan_sound")
    }
}
