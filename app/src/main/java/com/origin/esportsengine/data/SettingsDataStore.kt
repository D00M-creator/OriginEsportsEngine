package com.origin.esportsengine.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.origin.esportsengine.core.EngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "origin_engine_settings")

data class DashboardSettings(
    val targetFps: Int = 165,
    val autoReplyText: String = "In a tournament match right now, calling you back in 2 mins!",
    val callShieldEnabled: Boolean = true,
    val messageShieldEnabled: Boolean = true,
)

/**
 * Local settings store shared by the dashboard and shield services.
 */
object SettingsDataStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(DashboardSettings())
    val settings = _settings

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        if (appContext === applicationContext) return
        appContext = applicationContext
        scope.launch {
            applicationContext.settingsDataStore.data
                .catch { error ->
                    if (error !is IOException) throw error
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                }
                .collectLatest { preferences ->
                    val loaded = DashboardSettings(
                        targetFps = (preferences[FPS] ?: 165).coerceIn(30, 165),
                        autoReplyText = (preferences[REPLY] ?: DashboardSettings().autoReplyText).take(160),
                        callShieldEnabled = preferences[CALL_SHIELD] ?: true,
                        messageShieldEnabled = preferences[MESSAGE_SHIELD] ?: true,
                    )
                    _settings.value = loaded
                    EngineState.targetFps.set(loaded.targetFps)
                    EngineState.customAutoReplyText = loaded.autoReplyText
                    EngineState.isCallShieldEnabled.set(loaded.callShieldEnabled)
                    EngineState.isMsgShieldEnabled.set(loaded.messageShieldEnabled)
                }
        }
    }

    fun setTargetFps(value: Int) {
        val safeValue = value.coerceIn(30, 165)
        EngineState.targetFps.set(safeValue)
        update { this[FPS] = safeValue }
    }

    fun setAutoReplyText(value: String) {
        val safeValue = value.take(160)
        EngineState.customAutoReplyText = safeValue
        update { this[REPLY] = safeValue }
    }

    fun setCallShieldEnabled(value: Boolean) {
        EngineState.isCallShieldEnabled.set(value)
        update { this[CALL_SHIELD] = value }
    }

    fun setMessageShieldEnabled(value: Boolean) {
        EngineState.isMsgShieldEnabled.set(value)
        update { this[MESSAGE_SHIELD] = value }
    }

    private fun update(block: androidx.datastore.preferences.core.MutablePreferences.() -> Unit) {
        val context = appContext ?: return
        scope.launch { context.settingsDataStore.edit(block) }
    }

    private val FPS = intPreferencesKey("target_fps")
    private val REPLY = stringPreferencesKey("auto_reply_text")
    private val CALL_SHIELD = booleanPreferencesKey("call_shield_enabled")
    private val MESSAGE_SHIELD = booleanPreferencesKey("message_shield_enabled")
}
