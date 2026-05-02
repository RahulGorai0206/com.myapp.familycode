package com.myapp.familycode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.myapp.familycode.data.repository.OtpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.regex.Pattern

class OtpReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: OtpRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress

                val otp = extractOtp(messageBody)
                if (otp != null) {
                    Log.d("OtpReceiver", "Detected OTP: $otp from $sender")
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            repository.uploadOtp(
                                bankName = sender ?: "Unknown",
                                otpCode = otp,
                                fullMessage = messageBody
                            )
                        } catch (e: Exception) {
                            Log.e("OtpReceiver", "Failed to upload OTP", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
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
