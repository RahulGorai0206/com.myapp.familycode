package com.myapp.familycode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.myapp.familycode.R
import java.util.regex.Pattern

class OtpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress

                val otp = extractOtp(messageBody)
                if (otp != null) {
                    Log.d("OtpReceiver", "Detected OTP: $otp from $sender")
                    showOtpNotification(context, sender ?: "Unknown", otp, messageBody)
                }
            }
        }
    }

    private fun showOtpNotification(context: Context, sender: String, otpCode: String, fullMessage: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "otp_alerts"

        val channel = NotificationChannel(
            channelId,
            "OTP Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        fun createOtpIntent(action: String): Intent {
            return Intent(context, NotificationReceiver::class.java).apply {
                this.action = action
                putExtra("notificationId", notificationId)
                putExtra("sender", sender)
                putExtra("otpCode", otpCode)
                putExtra("fullMessage", fullMessage)
            }
        }

        val acceptPendingIntent = PendingIntent.getBroadcast(context, notificationId, createOtpIntent("ACCEPT_OTP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val denyPendingIntent = PendingIntent.getBroadcast(context, notificationId + 1, Intent(context, NotificationReceiver::class.java).apply { 
            action = "DENY_OTP"
            putExtra("notificationId", notificationId)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val triggerAt = System.currentTimeMillis() + 15000 // 15 seconds timeout
        val timeoutPendingIntent = PendingIntent.getBroadcast(context, notificationId + 2, createOtpIntent("TIMEOUT_OTP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New OTP detected")
            .setContentText("Share OTP $otpCode from $sender?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(triggerAt)
            .setTimeoutAfter(15000)
            .addAction(android.R.drawable.ic_input_add, "Share", acceptPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Ignore", denyPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.setAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            triggerAt,
            timeoutPendingIntent
        )
    }

    private fun extractOtp(message: String): String? {
        val lowerMessage = message.lowercase()
        
        // List of keywords that strongly indicate this is NOT an OTP message but a transaction alert
        val transactionKeywords = listOf("spent", "debited", "credited", "available balance", "avl lmt", "card x", "transaction")
        
        // List of keywords that indicate an OTP
        val otpKeywords = listOf("otp", "code", "verification", "vcode", "pin", "password", "pwd", "one-time")

        val hasTransactionKeyword = transactionKeywords.any { lowerMessage.contains(it) }
        val hasOtpKeyword = otpKeywords.any { lowerMessage.contains(it) }

        // If it looks like a transaction and doesn't mention OTP/Code, it's likely a false positive
        if (hasTransactionKeyword && !hasOtpKeyword) {
            return null
        }

        // Patterns to look for OTPs
        val patterns = listOf(
            // Pattern 1: OTP following a keyword (e.g., "OTP: 123456" or "code is 1234")
            Pattern.compile("(?i)(?:otp|code|is|verification|vcode|pin)\\D*(\\d{4,8})\\b"),
            // Pattern 2: Any 4-8 digit number with word boundaries (fallback)
            Pattern.compile("\\b(\\d{4,8})\\b")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            while (matcher.find()) {
                val code = matcher.group(1)
                // Basic validation: Avoid common years (2024-2030)
                val codeInt = code.toIntOrNull()
                if (codeInt != null && codeInt !in 2024..2030) {
                    return code
                }
            }
        }
        
        return null
    }

}
