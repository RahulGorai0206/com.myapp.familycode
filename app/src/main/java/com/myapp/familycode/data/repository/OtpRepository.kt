package com.myapp.familycode.data.repository

import android.content.Context
import com.myapp.familycode.GoogleSheetsLogger
import com.myapp.familycode.data.model.DeviceInfo
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

    /** Retrieve the persisted theme preference: "dark", "light", or "system" (default). */
    fun getThemePreference(): String {
        return sharedPrefs.getString("theme_mode", "system") ?: "system"
    }

    /** Persist the theme preference. */
    fun saveThemePreference(mode: String) {
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    suspend fun saveSettings(url: String, key: String, deviceName: String? = null) {
        sharedPrefs.edit().apply {
            putString("script_url", url)
            putString("api_key", key)
            if (deviceName != null) putString("device_name", deviceName)
            if (sharedPrefs.getString("device_id", "").isNullOrBlank()) {
                putString("device_id", UUID.randomUUID().toString())
            }
            apply()
        }
        GoogleSheetsLogger.updateUrl(url)
        GoogleSheetsLogger.updateApiKey(key)

        // Trigger registration if deviceName is provided
        if (deviceName != null) {
            val deviceId = sharedPrefs.getString("device_id", "") ?: ""
            GoogleSheetsLogger.registerDevice(deviceId, deviceName)
        }
    }

    suspend fun uploadOtp(bankName: String, otpCode: String, fullMessage: String): Boolean {
        val deviceName = sharedPrefs.getString("device_name", "Unknown") ?: "Unknown"
        val deviceId = sharedPrefs.getString("device_id", "") ?: ""
        return GoogleSheetsLogger.uploadOtp(bankName, otpCode, fullMessage, deviceName, deviceId)
    }

    suspend fun fetchLatestOtps(): SyncResponse {
        return GoogleSheetsLogger.fetchLatestOtps()
    }

    suspend fun testConnection(url: String, key: String): String? {
        return GoogleSheetsLogger.testConnection(url, key)
    }

    /**
     * Asks the backend to purge OTP rows older than 5 minutes from the Google Sheet.
     * @return the number of deleted rows, or -1 on error.
     */
    suspend fun deleteExpiredOtps(): Int {
        return GoogleSheetsLogger.deleteExpiredOtps()
    }
}
