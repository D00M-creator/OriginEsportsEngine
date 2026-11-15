package com.origin.esportsengine.shield

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Keeps short shield follow-up work alive if Android unbinds a service. */
internal object ShieldBackgroundTasks {
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/** Sends a short automatic reply using the user's selected SMS subscription. */
object SmsAutoReplier {
    fun sendInstantSms(context: Context, phoneNumber: String, message: String) {
        if (phoneNumber.isBlank() || message.isBlank()) return
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return

        runCatching {
            smsManagerFor(context.applicationContext).sendTextMessage(
                phoneNumber,
                null,
                message,
                null,
                null,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManagerFor(context: Context): SmsManager {
        val subscriptionId = runCatching { SmsManager.getDefaultSmsSubscriptionId() }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)

        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val subscriptionManager = context.getSystemService(SmsManager::class.java)
                val selected = runCatching {
                    subscriptionManager?.createForSubscriptionId(subscriptionId)
                }.getOrNull()
                if (selected != null) return selected
            } else {
                val selected = runCatching {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }.getOrNull()
                if (selected != null) return selected
            }
        }

        return context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
    }
}
