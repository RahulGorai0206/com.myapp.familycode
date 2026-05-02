package com.myapp.familycode.data.repository

import android.content.Context
import com.myapp.familycode.GoogleSheetsLogger
import com.myapp.familycode.data.model.SimpleResponse
import com.myapp.familycode.data.model.SyncResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class OtpRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    val appsScriptUrl: Flow<String?> = flow {
        emit(sharedPrefs.getString("script_url", ""))
    }
    
    val apiKey: Flow<String?> = flow {
        emit(sharedPrefs.getString("api_key", ""))
    }

    suspend fun saveSettings(url: String, key: String) {
        sharedPrefs.edit().apply {
            putString("script_url", url)
            putString("api_key", key)
            if (sharedPrefs.getString("device_id", "").isNullOrBlank()) {
                putString("device_id", UUID.randomUUID().toString())
            }
            apply()
        }
        GoogleSheetsLogger.updateUrl(url)
        GoogleSheetsLogger.updateApiKey(key)
    }

    suspend fun uploadOtp(bankName: String, otpCode: String, fullMessage: String): Boolean {
        return GoogleSheetsLogger.uploadOtp(bankName, otpCode, fullMessage)
    }

    suspend fun fetchLatestOtps(): SyncResponse {
        return GoogleSheetsLogger.fetchLatestOtps()
    }

    suspend fun testConnection(url: String, key: String): String? {
        return GoogleSheetsLogger.testConnection(url, key)
    }
}
