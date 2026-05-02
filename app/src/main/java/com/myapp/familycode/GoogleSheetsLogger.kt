package com.myapp.familycode

import android.content.Context
import android.util.Log
import com.myapp.familycode.data.api.GoogleSheetsApi
import com.myapp.familycode.data.model.SyncResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.*

object GoogleSheetsLogger {
    private var api: GoogleSheetsApi? = null
    private var currentUrl: String? = null
    private var apiKey: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DUMMY_BASE_URL = "https://script.google.com/"

    fun init(context: Context) {
        val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val url = sharedPrefs.getString("script_url", "") ?: ""
        apiKey = sharedPrefs.getString("api_key", "") ?: ""
        updateUrl(url)
    }

    fun updateUrl(url: String) {
        currentUrl = url
        if (api == null) {
            api = Retrofit.Builder()
                .baseUrl(DUMMY_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(GoogleSheetsApi::class.java)
        }
    }

    fun updateApiKey(key: String) {
        apiKey = key
    }

    suspend fun testConnection(url: String, key: String): String? {
        if (api == null) {
            api = Retrofit.Builder()
                .baseUrl(DUMMY_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(GoogleSheetsApi::class.java)
        }
        return try {
            val response = api?.fetchData(url, apiKey = key)
            if (response?.success == true) {
                null // Success
            } else {
                response?.error ?: "Invalid API Key or Script URL"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Connection failed: ${e.localizedMessage}"
        }
    }

    suspend fun uploadOtp(bankName: String, otpCode: String, fullMessage: String, deviceName: String): Boolean {
        val loggerApi = api ?: return false
        val url = currentUrl
        val key = apiKey
        if (url.isNullOrBlank() || key.isNullOrBlank()) return false

        return try {
            val response = loggerApi.postAction(
                url = url,
                action = "save_otp",
                apiKey = key,
                bankName = bankName,
                otpCode = otpCode,
                fullMessage = fullMessage,
                deviceName = deviceName
            )
            response.success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun registerDevice(deviceId: String, deviceName: String): Boolean {
        val loggerApi = api ?: return false
        val url = currentUrl
        val key = apiKey
        if (url.isNullOrBlank() || key.isNullOrBlank()) return false

        return try {
            val response = loggerApi.postAction(
                url = url,
                action = "register",
                apiKey = key,
                deviceId = deviceId,
                deviceName = deviceName
            )
            response.success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun fetchLatestOtps(): SyncResponse {
        val url = currentUrl
        val key = apiKey
        
        // Use local variable checks instead of currentUrl/apiKey directly to avoid race conditions
        if (url.isNullOrBlank() || key.isNullOrBlank()) {
             return SyncResponse(false, error = "Credential Missing")
        }

        val loggerApi = api ?: return SyncResponse(false, error = "API Client not initialized")

        return try {
            loggerApi.fetchData(url = url, apiKey = key)
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResponse(false, error = e.localizedMessage)
        }
    }
}
