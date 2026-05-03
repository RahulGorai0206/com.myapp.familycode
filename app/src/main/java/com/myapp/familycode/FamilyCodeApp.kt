package com.myapp.familycode

import android.app.Application
import androidx.room.Room
import com.myapp.familycode.data.db.AppDatabase
import com.myapp.familycode.data.repository.OtpRepository
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
    }
}
