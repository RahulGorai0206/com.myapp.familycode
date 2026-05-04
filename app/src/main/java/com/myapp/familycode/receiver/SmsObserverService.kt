package com.myapp.familycode.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.myapp.familycode.R
import com.myapp.familycode.ui.MainActivity
import java.util.regex.Pattern

/**
 * A foreground service that uses a ContentObserver on the SMS inbox to detect
 * OTP messages that are suppressed by the SMS Retriever API.
 *
 * When an app (like Zomato) includes an 11-character App Hash in its SMS,
 * Android's SMS Retriever intercepts the message and suppresses the standard
 * SMS_RECEIVED broadcast. However, the SMS is still written to the SMS
 * content provider (content://sms). This observer watches for those writes
 * and processes them as a fallback.
 */
class SmsObserverService : Service() {

    private var smsObserver: ContentObserver? = null

    companion object {
        private const val TAG = "SmsObserverService"
        private const val CHANNEL_ID = "sms_observer_service"
        private const val NOTIFICATION_ID = 9001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForegroundWithNotification()
        registerSmsObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "SMS observer unregistered")
        }
    }

    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OTP Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors incoming SMS for OTPs"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FamilyCode")
            .setContentText("Monitoring OTPs in background")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun registerSmsObserver() {
        val handler = Handler(Looper.getMainLooper())

        smsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "SMS content changed: $uri")
                checkLatestSms()
            }
        }

        contentResolver.registerContentObserver(
            Uri.parse("content://sms"),
            true,
            smsObserver!!
        )
        Log.d(TAG, "SMS observer registered on content://sms")
    }

    private fun checkLatestSms() {
        try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id", "address", "body", "date"),
                null, null,
                "date DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    val address = it.getString(1) ?: "Unknown"
                    val body = it.getString(2) ?: ""
                    val date = it.getLong(3)

                    // Only process SMS received within the last 10 seconds
                    val ageMs = System.currentTimeMillis() - date
                    if (ageMs > 10_000) {
                        Log.d(TAG, "SMS too old (${ageMs}ms), skipping")
                        return
                    }

                    // Dedup key: use SMS database _id
                    val dedupKey = "sms_$id"
                    if (!OtpDeduplicator.tryProcess(dedupKey)) {
                        Log.d(TAG, "SMS $id already processed, skipping")
                        return
                    }

                    Log.d(TAG, "Processing SMS from $address: $body")

                    val otp = extractOtp(body)
                    if (otp != null) {
                        // Also dedup by sender+otp to avoid clash with BroadcastReceiver
                        val otpKey = "${address}|${otp}"
                        if (OtpDeduplicator.tryProcess(otpKey)) {
                            Log.d(TAG, "ContentObserver detected OTP: $otp from $address (broadcast was likely suppressed)")
                            showOtpNotification(address, otp, body)
                        } else {
                            Log.d(TAG, "OTP $otp from $address already handled by BroadcastReceiver")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS inbox", e)
        }
    }

    private fun showOtpNotification(sender: String, otpCode: String, fullMessage: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "otp_alerts"

        val channel = NotificationChannel(
            channelId,
            "OTP Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        fun createOtpIntent(action: String): Intent {
            return Intent(this, NotificationReceiver::class.java).apply {
                this.action = action
                putExtra("notificationId", notificationId)
                putExtra("sender", sender)
                putExtra("otpCode", otpCode)
                putExtra("fullMessage", fullMessage)
            }
        }

        val acceptPendingIntent = PendingIntent.getBroadcast(
            this, notificationId, createOtpIntent("ACCEPT_OTP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val denyPendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 1,
            Intent(this, NotificationReceiver::class.java).apply {
                action = "DENY_OTP"
                putExtra("notificationId", notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + 15000
        val timeoutPendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 2, createOtpIntent("TIMEOUT_OTP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
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

        val alarmManager = getSystemService(android.app.AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            triggerAt,
            timeoutPendingIntent
        )
    }

    /**
     * OTP extraction logic — mirrors OtpReceiver.extractOtp().
     * Duplicated here to keep the service self-contained.
     */
    private fun extractOtp(message: String): String? {
        // Step 1: Clean the message by removing common App Hashes (11 chars at end)
        val cleanedMessage = message.replace(Regex("\\s+[a-zA-Z0-9+/]{11}$"), "").trim()
        val lowerMessage = cleanedMessage.lowercase()

        val transactionKeywords = listOf("spent", "debited", "credited", "available balance", "avl lmt", "card x", "transaction")
        val otpKeywords = listOf("otp", "code", "verification", "vcode", "v-code", "pin", "password", "pwd", "one-time", "passcode", "login")

        val hasTransactionKeyword = transactionKeywords.any { lowerMessage.contains(it) }
        val hasOtpKeyword = otpKeywords.any { lowerMessage.contains(it) }

        if (hasTransactionKeyword && !hasOtpKeyword) return null

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

        if (candidates.isEmpty()) return null

        if (candidates.size == 1 && candidates[0].length == 6) return candidates[0]
        if (hasOtpKeyword) return candidates[0]
        if (lowerMessage.contains(" is ")) return candidates[0]

        return null
    }
}
