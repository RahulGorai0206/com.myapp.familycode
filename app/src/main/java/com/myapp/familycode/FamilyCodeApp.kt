package com.myapp.familycode

import android.app.Application
import com.myapp.familycode.data.api.GoogleSheetsApi
import com.myapp.familycode.data.repository.OtpRepository
import com.myapp.familycode.viewmodel.OtpViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FamilyCodeApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val appModule = module {
            single {
                Retrofit.Builder()
                    .baseUrl("https://script.google.com/") // Base URL is required but dynamic in calls
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GoogleSheetsApi::class.java)
            }
            single { OtpRepository(get(), get()) }
            viewModel { OtpViewModel(get()) }
        }

        startKoin {
            androidContext(this@FamilyCodeApp)
            modules(appModule)
        }
    }
}
