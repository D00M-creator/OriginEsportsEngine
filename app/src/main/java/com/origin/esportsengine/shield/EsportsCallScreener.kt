package com.origin.esportsengine.shield

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import com.origin.esportsengine.core.EngineState
import kotlinx.coroutines.launch

class EsportsCallScreener : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        if (!EngineState.isCurrentlyInsideGame() || !EngineState.isCallShieldEnabled.get()) {
            allowCall(callDetails)
            return
        }

        val number = callDetails.handle
            ?.takeIf { it.scheme.equals("tel", ignoreCase = true) }
            ?.schemeSpecificPart
            ?.takeIf(String::isNotBlank)

        if (number == null) {
            allowCall(callDetails)
            return
        }

        val isEmergencyBreakthrough = recordCallAndCheckBreakthrough(number, SystemClock.elapsedRealtime())
        if (isEmergencyBreakthrough) {
            allowCall(callDetails)
            ShieldBackgroundTasks.ioScope.launch {
                val callerName = resolveCallerName(number)
                EngineState.triggerPillAlert("Emergency Breakthrough: $callerName calling (3rd try)")
            }
            return
        }

        // Respond before contact lookup or SMS work so the screening decision is immediate.
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build(),
        )
        EngineState.callsBlockedCount.incrementAndGet()

        ShieldBackgroundTasks.ioScope.launch {
            val callerName = resolveCallerName(number)
            val reply = EngineState.customAutoReplyText
            SmsAutoReplier.sendInstantSms(applicationContext, number, reply)
            EngineState.triggerPillAlert(
                "$callerName • Auto-Replied: ${reply.take(22)}${if (reply.length > 22) "..." else ""}",
            )
        }
    }

    private fun allowCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    private fun resolveCallerName(number: String): String {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return number
        }

        return runCatching {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            )
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf(String::isNotBlank)
                } else {
                    null
                }
            }
        }.getOrNull() ?: number
    }

    private fun recordCallAndCheckBreakthrough(number: String, nowMs: Long): Boolean = synchronized(recentCalls) {
        recentCalls.entries.removeAll { (_, timestamps) ->
            timestamps.removeAll { nowMs - it > EMERGENCY_WINDOW_MS }
            timestamps.isEmpty()
        }

        val timestamps = recentCalls.getOrPut(number) { mutableListOf() }
        if (timestamps.size >= 2) {
            timestamps.clear()
            true
        } else {
            timestamps.add(nowMs)
            false
        }
    }

    companion object {
        private const val EMERGENCY_WINDOW_MS = 3 * 60 * 1_000L
        private val recentCalls = HashMap<String, MutableList<Long>>()
    }
}
