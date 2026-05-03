package com.myapp.familycode.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single OTP item from the backend.
 */
data class OtpItem(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("bank_name") val bankName: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("full_message") val fullMessage: String,
    @SerializedName("device_name") val deviceName: String? = null
)

/**
 * Represents a registered family device.
 */
data class DeviceInfo(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("last_seen") val lastSeen: String
)

/**
 * Represents the combined response from the 'fetch_data' action.
 */
data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("device_count") val deviceCount: Int? = null,
    @SerializedName("device_list") val deviceList: List<DeviceInfo>? = null,
    @SerializedName("recent_otps") val recentOtps: List<OtpItem>? = null,
    @SerializedName("error") val error: String? = null
)

/**
 * Generic response for simple POST actions.
 */
data class SimpleResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("deleted_count") val deletedCount: Int? = null,
    @SerializedName("error") val error: String? = null
)
