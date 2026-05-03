package com.myapp.familycode.data.repository

import android.content.Context
import android.provider.Settings
import com.myapp.familycode.GoogleSheetsLogger
import com.myapp.familycode.data.db.DeviceDao
import com.myapp.familycode.data.db.OtpDao
import com.myapp.familycode.data.db.toEntity
import com.myapp.familycode.data.model.DeviceInfo
import com.myapp.familycode.data.model.OtpItem
import com.myapp.familycode.data.model.SyncResponse
import com.myapp.familycode.util.CryptoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class OtpRepository(
    private val context: Context,
    private val otpDao: OtpDao,
    private val deviceDao: DeviceDao
) {

    private val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    val appsScriptUrl: Flow<String?> = flow {
        emit(sharedPrefs.getString("script_url", ""))
    }

    val apiKey: Flow<String?> = flow {
        emit(sharedPrefs.getString("api_key", ""))
    }

    /** Expose local data streams */
    val localOtps: Flow<List<OtpItem>> = otpDao.getAllOtps().map { list -> list.map { it.toOtpItem() } }
    val localDevices: Flow<List<DeviceInfo>> = deviceDao.getAllDevices().map { list -> list.map { it.toDeviceInfo() } }

    /** Retrieve the persisted theme preference: "dark", "light", or "system" (default). */
    fun getThemePreference(): String {
        return sharedPrefs.getString("theme_mode", "system") ?: "system"
    }

    /** Expose current device ID to highlight in UI */
    fun getCurrentDeviceId(): String {
        return sharedPrefs.getString("device_id", "") ?: ""
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
                val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                val newId = if (!androidId.isNullOrBlank()) androidId else UUID.randomUUID().toString()
                putString("device_id", newId)
            }
            apply()
        }
        GoogleSheetsLogger.updateUrl(url)
        GoogleSheetsLogger.updateApiKey(key)

        // Trigger registration if deviceName is provided
        if (deviceName != null) {
            val deviceId = sharedPrefs.getString("device_id", "") ?: ""
            // We should ideally encrypt the deviceName during registration too
            val encDeviceName = CryptoUtils.encrypt(deviceName, key)
            GoogleSheetsLogger.registerDevice(deviceId, encDeviceName)
        }
    }

    suspend fun uploadOtp(bankName: String, otpCode: String, fullMessage: String): Boolean {
        val deviceName = sharedPrefs.getString("device_name", "Unknown") ?: "Unknown"
        val deviceId = sharedPrefs.getString("device_id", "") ?: ""
        val key = sharedPrefs.getString("api_key", "") ?: ""
        
        // Encrypt the fields
        val encBankName = CryptoUtils.encrypt(bankName, key)
        val encOtpCode = CryptoUtils.encrypt(otpCode, key)
        val encFullMessage = CryptoUtils.encrypt(fullMessage, key)
        val encDeviceName = CryptoUtils.encrypt(deviceName, key)

        return GoogleSheetsLogger.uploadOtp(encBankName, encOtpCode, encFullMessage, encDeviceName, deviceId)
    }

    /** Fetch from network, decrypt, and save to local Room database. Returns SyncResponse for UI status. */
    suspend fun fetchLatestOtps(): SyncResponse {
        val key = sharedPrefs.getString("api_key", "") ?: ""
        val deviceId = sharedPrefs.getString("device_id", "")
        val response = GoogleSheetsLogger.fetchLatestOtps(deviceId)
        
        if (response.success) {
            // Decrypt OTPs
            val decryptedOtps = response.recentOtps?.map { otp ->
                OtpItem(
                    timestamp = otp.timestamp,
                    bankName = CryptoUtils.decrypt(otp.bankName, key),
                    otpCode = CryptoUtils.decrypt(otp.otpCode, key),
                    fullMessage = CryptoUtils.decrypt(otp.fullMessage, key),
                    deviceName = otp.deviceName?.let { CryptoUtils.decrypt(it, key) }
                )
            } ?: emptyList()
            
            // Decrypt Devices
            val decryptedDevices = response.deviceList?.map { device ->
                DeviceInfo(
                    deviceId = device.deviceId,
                    deviceName = CryptoUtils.decrypt(device.deviceName, key),
                    lastSeen = device.lastSeen
                )
            } ?: emptyList()
            
            // Insert into local DB
            otpDao.refreshOtps(decryptedOtps.map { it.toEntity() })
            deviceDao.refreshDevices(decryptedDevices.map { it.toEntity() })
            
            return response.copy(
                recentOtps = decryptedOtps,
                deviceList = decryptedDevices
            )
        }
        return response
    }

    suspend fun testConnection(url: String, key: String): String? {
        return GoogleSheetsLogger.testConnection(url, key)
    }

    suspend fun deleteExpiredOtps(): Int {
        return GoogleSheetsLogger.deleteExpiredOtps()
    }
    
    /**
     * Delete an OTP from Room immediately, then try to delete it from Google Sheets.
     * Throws an exception if the network call fails.
     */
    suspend fun deleteOtpLocallyAndRemotely(timestamp: String) {
        val otpToRestore = otpDao.getOtp(timestamp)
        // Optimistic local delete
        otpDao.deleteOtp(timestamp)
        
        val deviceId = sharedPrefs.getString("device_id", "") ?: ""
        val success = GoogleSheetsLogger.deleteOtp(timestamp, deviceId)
        
        if (!success) {
            // Restore it on failure
            if (otpToRestore != null) {
                otpDao.insertOtps(listOf(otpToRestore))
            }
            throw Exception("Network error: Please turn on internet to delete.")
        }
    }
}
