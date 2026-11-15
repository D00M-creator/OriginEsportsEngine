package com.origin.esportsengine.shield

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.origin.esportsengine.core.EngineState
import kotlinx.coroutines.launch
import java.util.Locale

class ZeroLagNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !EngineState.isCurrentlyInsideGame() || !EngineState.isMsgShieldEnabled.get()) return
        if (sbn.packageName == packageName || sbn.isOngoing) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val otpCode = OTP_PATTERN.find(text)?.value
        if (otpCode != null && OTP_KEYWORDS.containsMatchIn(text)) {
            EngineState.triggerPillAlert("OTP: $otpCode (From ${title.ifBlank { sbn.packageName }})")
            return
        }

        if (sbn.packageName != WHATSAPP_PACKAGE && sbn.packageName != WHATSAPP_BUSINESS_PACKAGE) return

        val replyAction = sbn.notification.actions
            ?.firstOrNull { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true }
            ?: return
        val remoteInputs = replyAction.remoteInputs
            ?.filter { it.allowFreeFormInput }
            ?.toTypedArray()
            ?: return
        if (remoteInputs.isEmpty()) return

        val senderKey = senderKey(title, text, sbn.key)
        val timestamp = SystemClock.elapsedRealtime()
        if (!claimReplyCooldown(senderKey, timestamp)) return
        val replyText = EngineState.customAutoReplyText
        val messageTitle = title.ifBlank { sbn.packageName }

        ShieldBackgroundTasks.ioScope.launch {
            val replySent = runCatching {
                val replyIntent = Intent()
                val replyResults = Bundle().apply {
                    remoteInputs.forEach { input ->
                        putCharSequence(input.resultKey, replyText)
                    }
                }
                RemoteInput.addResultsToIntent(remoteInputs, replyIntent, replyResults)
                replyAction.actionIntent.send(applicationContext, 0, replyIntent)
            }.isSuccess

            if (!replySent) {
                releaseReplyCooldown(senderKey, timestamp)
                return@launch
            }

            EngineState.messagesFilteredCount.incrementAndGet()
            EngineState.triggerPillAlert("$messageTitle [Messaged]")
        }
    }

    private fun senderKey(title: String, text: String, notificationKey: String): String {
        val groupSender = text.substringBefore(':').trim()
        val sender = if (groupSender.isNotBlank() && groupSender.length <= MAX_SENDER_LENGTH && ':' in text) {
            groupSender
        } else {
            title.ifBlank { notificationKey }
        }
        return sender.lowercase(Locale.ROOT)
    }

    private fun claimReplyCooldown(sender: String, nowMs: Long): Boolean = synchronized(replyCooldowns) {
        replyCooldowns.entries.removeAll { nowMs - it.value >= REPLY_COOLDOWN_MS }
        val previous = replyCooldowns[sender]
        if (previous != null && nowMs - previous < REPLY_COOLDOWN_MS) {
            false
        } else {
            replyCooldowns[sender] = nowMs
            true
        }
    }

    private fun releaseReplyCooldown(sender: String, timestamp: Long) = synchronized(replyCooldowns) {
        if (replyCooldowns[sender] == timestamp) replyCooldowns.remove(sender)
    }

    companion object {
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        private const val REPLY_COOLDOWN_MS = 2 * 60 * 1_000L
        private const val MAX_SENDER_LENGTH = 64
        private val OTP_PATTERN = Regex("\\b\\d{4,8}\\b")
        private val OTP_KEYWORDS = Regex("\\b(?:otp|code|verification|bank|garena|krafton)\\b", RegexOption.IGNORE_CASE)
        private val replyCooldowns = HashMap<String, Long>()
    }
}
