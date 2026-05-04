package com.myapp.familycode

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.myapp.familycode.data.db.AppDatabase
import com.myapp.familycode.data.repository.OtpRepository
import com.myapp.familycode.receiver.SmsObserverService
import com.myapp.familycode.viewmodel.OtpViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

class FamilyCodeApp : Application() {
    override fun onCreate() {
        super.onCreate()

        GoogleSheetsLogger.init(this)

        val appModule = module {
            single {
                Room.databaseBuilder(
                    androidContext(),
                    AppDatabase::class.java,
                    "familycode_db"
                ).build()
            }
            single { get<AppDatabase>().otpDao() }
            single { get<AppDatabase>().deviceDao() }
            single { OtpRepository(get(), get(), get()) }
            viewModel { OtpViewModel(get()) }
        }

        startKoin {
            androidContext(this@FamilyCodeApp)
            modules(appModule)
        }

        // Start the SMS observer service to catch OTPs suppressed by SMS Retriever API
        // (e.g., Zomato OTPs with app hash)
        try {
            val serviceIntent = Intent(this, SmsObserverService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

