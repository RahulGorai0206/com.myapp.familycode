package com.myapp.familycode.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OtpDao {
    @Query("SELECT * FROM otps ORDER BY timestamp DESC")
    fun getAllOtps(): Flow<List<OtpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOtps(otps: List<OtpEntity>)

    @Query("DELETE FROM otps")
    suspend fun clearAll()

    @Query("DELETE FROM otps WHERE timestamp = :timestamp")
    suspend fun deleteOtp(timestamp: String)
    
    @Transaction
    suspend fun refreshOtps(otps: List<OtpEntity>) {
        clearAll()
        insertOtps(otps)
    }
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<DeviceEntity>)

    @Query("DELETE FROM devices")
    suspend fun clearAll()

    @Transaction
    suspend fun refreshDevices(devices: List<DeviceEntity>) {
        clearAll()
        insertDevices(devices)
    }
}

@Database(entities = [OtpEntity::class, DeviceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun otpDao(): OtpDao
    abstract fun deviceDao(): DeviceDao
}
