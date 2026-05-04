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
            if (messages.isNullOrEmpty()) return

            // Concatenate all parts of a multipart SMS into one complete message.
            // Long messages (>160 chars, like Zomato OTPs) are split by the carrier
            // into multiple segments. We must reassemble them before OTP extraction.
            val sender = messages[0].displayOriginatingAddress
            val fullMessageBody = messages.joinToString("") { it.displayMessageBody ?: "" }

            Log.d("OtpReceiver", "Received SMS from $sender: $fullMessageBody")

            val otp = extractOtp(fullMessageBody)
            if (otp != null) {
                Log.d("OtpReceiver", "Detected OTP: $otp from $sender")
                showOtpNotification(context, sender ?: "Unknown", otp, fullMessageBody)
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
        Log.d("OtpReceiver", "Extracting OTP from: $message")
        
        // List of keywords that strongly indicate this is NOT an OTP message but a transaction alert
        val transactionKeywords = listOf("spent", "debited", "credited", "available balance", "avl lmt", "card x", "transaction")
        
        // List of keywords that indicate an OTP
        val otpKeywords = listOf("otp", "code", "verification", "vcode", "v-code", "pin", "password", "pwd", "one-time", "passcode")

        val hasTransactionKeyword = transactionKeywords.any { lowerMessage.contains(it) }
        val hasOtpKeyword = otpKeywords.any { lowerMessage.contains(it) }

        // If it looks like a transaction and doesn't mention OTP/Code, it's likely a false positive
        if (hasTransactionKeyword && !hasOtpKeyword) {
            Log.d("OtpReceiver", "Message looks like a transaction without OTP keyword, skipping.")
            return null
        }

        // Keywords for regex - added more variants and "is" with word boundaries
        val keywordPattern = "\\b(?:otp|code|verification|vcode|pin|password|pwd|one-time|is|v-code)\\b"
        
        // Patterns to look for OTPs
        val patterns = listOf(
            // Pattern 1: OTP following a keyword (e.g., "OTP: 123456" or "Your code is 1234")
            Pattern.compile("(?i)$keywordPattern\\D*(\\d{4,8})\\b"),
            // Pattern 2: OTP preceding a keyword (e.g., "123456 is your OTP" - common in Zomato)
            Pattern.compile("(?i)\\b(\\d{4,8})\\D*$keywordPattern"),
            // Pattern 3: Any 4-8 digit number with word boundaries (fallback)
            Pattern.compile("\\b(\\d{4,8})\\b")
        )

        for ((index, pattern) in patterns.withIndex()) {
            val matcher = pattern.matcher(message)
            while (matcher.find()) {
                val code = matcher.group(1)
                val codeInt = code.toIntOrNull()
                // Basic validation: Avoid common years (2024-2030)
                if (codeInt != null && codeInt !in 2024..2030) {
                    Log.d("OtpReceiver", "Found candidate '$code' using pattern ${index + 1}")
                    return code
                }
            }
        }
        
        Log.d("OtpReceiver", "No OTP candidate found.")
        return null
    }

}
