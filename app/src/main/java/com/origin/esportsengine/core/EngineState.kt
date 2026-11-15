package com.origin.esportsengine.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ShiftEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val fromState: String,
    val toState: String,
    val targetFps: Int,
    val peakTempC: Float,
    val callsBlocked: Int,
    val messagesFiltered: Int
)

object EngineState {
    val isUniversallyArmed = AtomicBoolean(false)
    val activeGameId = AtomicInteger(0)

    val targetFps = AtomicInteger(165)
    val isCallShieldEnabled = AtomicBoolean(true)
    val isMsgShieldEnabled = AtomicBoolean(true)

    @Volatile
    var customAutoReplyText: String = "In a match, calling back in 2 mins!"

    val callsBlockedCount = AtomicInteger(0)
    val messagesFilteredCount = AtomicInteger(0)

    @Volatile
    var currentTempC: Float = 36.0f

    @Volatile
    var currentFrameTimeMs: Float = 16.6f

    private val _statusText = MutableStateFlow("DISARMED • SYSTEM NORMAL")
    val statusText: StateFlow<String> = _statusText

    private val _latestPillAlert = MutableStateFlow<String?>(null)
    val latestPillAlert: StateFlow<String?> = _latestPillAlert

    private val _shiftHistory = MutableStateFlow<List<ShiftEvent>>(emptyList())
    val shiftHistory: StateFlow<List<ShiftEvent>> = _shiftHistory

    fun updateStatus(text: String) {
        _statusText.value = text
    }

    fun triggerPillAlert(message: String) {
        _latestPillAlert.value = message
    }

    fun recordShift(from: String, to: String) {
        val event = ShiftEvent(
            fromState = from,
            toState = to,
            targetFps = targetFps.get(),
            peakTempC = currentTempC,
            callsBlocked = callsBlockedCount.get(),
            messagesFiltered = messagesFilteredCount.get()
        )
        _shiftHistory.value = listOf(event) + _shiftHistory.value
    }

    fun isCurrentlyInsideGame(): Boolean {
        return isUniversallyArmed.get() && activeGameId.get() != 0
    }
}