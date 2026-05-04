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
        Log.d("OtpReceiver", "onReceive triggered with action: ${intent.action}")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.d("OtpReceiver", "No SMS messages found in the intent extras.")
                return
            }

            // Concatenate all parts of a multipart SMS into one complete message.
            // Long messages (>160 chars, like Zomato OTPs) are split by the carrier
            // into multiple segments. We must reassemble them before OTP extraction.
            val sender = messages[0].displayOriginatingAddress
            val fullMessageBody = messages.joinToString(" ") { it.displayMessageBody ?: "" }

            Log.d("OtpReceiver", "Received SMS from $sender: $fullMessageBody (length: ${fullMessageBody.length})")

            val otp = extractOtp(fullMessageBody)
            if (otp != null) {
                Log.d("OtpReceiver", "Detected OTP: $otp from $sender")
                // Fallback Toast for debugging
                android.widget.Toast.makeText(context, "OTP detected: $otp", android.widget.Toast.LENGTH_LONG).show()
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
        // Step 1: Clean the message by removing common App Hashes (11 chars at end)
        val cleanedMessage = message.replace(Regex("\\s+[a-zA-Z0-9+/]{11}$"), "").trim()
        val lowerMessage = cleanedMessage.lowercase()
        
        Log.d("OtpReceiver", "Extracting OTP from cleaned message: $cleanedMessage")
        
        // List of keywords that strongly indicate this is NOT an OTP message but a transaction alert
        val transactionKeywords = listOf("spent", "debited", "credited", "available balance", "avl lmt", "card x", "transaction")
        
        // List of keywords that indicate an OTP
        val otpKeywords = listOf("otp", "code", "verification", "vcode", "v-code", "pin", "password", "pwd", "one-time", "passcode", "login")

        val hasTransactionKeyword = transactionKeywords.any { lowerMessage.contains(it) }
        val hasOtpKeyword = otpKeywords.any { lowerMessage.contains(it) || lowerMessage.contains("zomato") }

        Log.d("OtpReceiver", "hasTransactionKeyword: $hasTransactionKeyword, hasOtpKeyword: $hasOtpKeyword")

        // If it looks like a transaction and doesn't mention OTP/Code, it's likely a false positive
        if (hasTransactionKeyword && !hasOtpKeyword) {
            Log.d("OtpReceiver", "Message looks like a transaction without OTP keyword, skipping.")
            return null
        }

        // Find all 4-8 digit numbers in the message (relaxed boundary check)
        val digitPattern = Pattern.compile("(\\d{4,8})")
        val matcher = digitPattern.matcher(cleanedMessage)
        val candidates = mutableListOf<String>()
        
        while (matcher.find()) {
            val code = matcher.group(1)
            if (code != null) {
                val codeInt = code.toIntOrNull()
                if (codeInt != null && codeInt !in 2024..2030) {
                    candidates.add(code)
                }
            }
        }

        Log.d("OtpReceiver", "Digit candidates found: $candidates")

        if (candidates.isEmpty()) {
            Log.d("OtpReceiver", "No digit candidates found.")
            return null
        }

        // Special case: If there is exactly one 6-digit number, it's almost certainly an OTP
        if (candidates.size == 1 && candidates[0].length == 6) {
            Log.d("OtpReceiver", "Single 6-digit candidate found. Returning it as OTP.")
            return candidates[0]
        }

        // If we found digits AND the message has OTP keywords, it's almost certainly an OTP
        if (hasOtpKeyword) {
            Log.d("OtpReceiver", "Detected OTP '${candidates[0]}' in message with keywords.")
            return candidates[0]
        }
        
        // Secondary check: if "is" is used near a number, it's also a strong indicator (like "123456 is...")
        if (lowerMessage.contains(" is ")) {
             Log.d("OtpReceiver", "Detected OTP '${candidates[0]}' via 'is' keyword.")
             return candidates[0]
        }

        Log.d("OtpReceiver", "Found digits but no strong OTP keywords. Skipping.")
        return null
    }

}
