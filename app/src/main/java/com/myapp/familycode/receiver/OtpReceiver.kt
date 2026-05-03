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
        // Regex to find 4 to 8 digit codes. 
        // Often OTPs are preceded by "OTP is" or "code is" but we'll look for any 4-8 digit number 
        // that isn't part of a larger word.
        val pattern = Pattern.compile("\\b(\\d{4,8})\\b")
        val matcher = pattern.matcher(message)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }
}
