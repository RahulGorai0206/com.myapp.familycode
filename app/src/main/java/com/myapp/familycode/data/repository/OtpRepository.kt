package com.myapp.familycode.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myapp.familycode.data.api.GoogleSheetsApi
import com.myapp.familycode.data.model.SimpleResponse
import com.myapp.familycode.data.model.SyncResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

class OtpRepository(private val context: Context, private val api: GoogleSheetsApi) {

    private val APPS_SCRIPT_URL = stringPreferencesKey("apps_script_url")
    private val API_KEY = stringPreferencesKey("api_key")
    private val DEVICE_ID = stringPreferencesKey("device_id")

    val appsScriptUrl: Flow<String?> = context.dataStore.data.map { it[APPS_SCRIPT_URL] }
    val apiKey: Flow<String?> = context.dataStore.data.map { it[API_KEY] }
    val deviceId: Flow<String> = context.dataStore.data.map { it[DEVICE_ID] ?: "" }

    suspend fun saveSettings(url: String, key: String) {
        context.dataStore.edit { settings ->
            settings[APPS_SCRIPT_URL] = url
            settings[API_KEY] = key
            if (settings[DEVICE_ID] == null) {
                settings[DEVICE_ID] = UUID.randomUUID().toString()
            }
        }
    }

    suspend fun uploadOtp(bankName: String, otpCode: String, fullMessage: String): SimpleResponse {
        val url = appsScriptUrl.first() ?: return SimpleResponse(false, "URL not set")
        val key = apiKey.first() ?: return SimpleResponse(false, "API Key not set")
        
        return api.postAction(
            url = url,
            action = "save_otp",
            apiKey = key,
            bankName = bankName,
            otpCode = otpCode,
            fullMessage = fullMessage
        )
    }

    suspend fun registerDevice(deviceName: String): SimpleResponse {
        val url = appsScriptUrl.first() ?: return SimpleResponse(false, "URL not set")
        val key = apiKey.first() ?: return SimpleResponse(false, "API Key not set")
        val id = deviceId.first().ifEmpty { 
            val newId = UUID.randomUUID().toString()
            context.dataStore.edit { it[DEVICE_ID] = newId }
            newId
        }

        return api.postAction(
            url = url,
            action = "register",
            apiKey = key,
            deviceId = id,
            deviceName = deviceName
        )
    }

    suspend fun fetchLatestOtps(): SyncResponse {
        val url = appsScriptUrl.first() ?: return SyncResponse(false, error = "URL not set")
        val key = apiKey.first() ?: return SyncResponse(false, error = "API Key not set")

        return try {
            api.fetchData(url = url, apiKey = key)
        } catch (e: Exception) {
            SyncResponse(false, error = e.message)
        }
    }
}
