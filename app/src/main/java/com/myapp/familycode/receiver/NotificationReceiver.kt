package com.myapp.familycode.receiver

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.myapp.familycode.data.repository.OtpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationReceiver : BroadcastReceiver(), KoinComponent {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val repository: OtpRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val action = intent.action
        val notificationId = intent.getIntExtra("notificationId", 0)
        
        Log.d("NotificationReceiver", "Action: $action, ID: $notificationId")

        // Dismiss notification immediately
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        scope.launch {
            try {
                // Cancel the timeout alarm if the user took action (Accept or Deny)
                if (action == "ACCEPT_OTP" || action == "DENY_OTP") {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val timeoutIntent = Intent(context, NotificationReceiver::class.java).apply {
                        this.action = "TIMEOUT_OTP"
                    }
                    val timeoutPendingIntent = PendingIntent.getBroadcast(
                        context, 
                        notificationId + 2, 
                        timeoutIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(timeoutPendingIntent)
                }

                if (action == "ACCEPT_OTP" || action == "TIMEOUT_OTP") {
                    val sender = intent.getStringExtra("sender") ?: "Unknown"
                    val otpCode = intent.getStringExtra("otpCode") ?: ""
                    val fullMessage = intent.getStringExtra("fullMessage") ?: ""

                    if (otpCode.isNotEmpty()) {
                        Log.d("NotificationReceiver", "Uploading OTP: $otpCode from $sender")
                        val success = repository.uploadOtp(
                            bankName = sender,
                            otpCode = otpCode,
                            fullMessage = fullMessage
                        )
                        Log.d("NotificationReceiver", "Upload success: $success")
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error processing OTP", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
