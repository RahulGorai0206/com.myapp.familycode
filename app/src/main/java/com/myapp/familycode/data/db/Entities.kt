package com.myapp.familycode.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.myapp.familycode.data.model.DeviceInfo
import com.myapp.familycode.data.model.OtpItem

@Entity(tableName = "otps")
data class OtpEntity(
    @PrimaryKey val timestamp: String,
    val bankName: String,
    val otpCode: String,
    val fullMessage: String,
    val deviceName: String?
) {
    fun toOtpItem() = OtpItem(
        timestamp = timestamp,
        bankName = bankName,
        otpCode = otpCode,
        fullMessage = fullMessage,
        deviceName = deviceName
    )
}

fun OtpItem.toEntity() = OtpEntity(
    timestamp = timestamp,
    bankName = bankName,
    otpCode = otpCode,
    fullMessage = fullMessage,
    deviceName = deviceName
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val lastSeen: String
) {
    fun toDeviceInfo() = DeviceInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        lastSeen = lastSeen
    )
}

fun DeviceInfo.toEntity() = DeviceEntity(
    deviceId = deviceId,
    deviceName = deviceName,
    lastSeen = lastSeen
)
