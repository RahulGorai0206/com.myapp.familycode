package com.myapp.familycode.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.myapp.familycode.data.repository.OtpRepository
import com.myapp.familycode.ui.screens.HomeScreen
import com.myapp.familycode.ui.screens.SetupScreen
import com.myapp.familycode.ui.theme.FamilyCodeTheme
import com.myapp.familycode.viewmodel.OtpViewModel
import com.myapp.familycode.viewmodel.ThemeMode
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val repository: OtpRepository by inject()
    private val viewModel: OtpViewModel by viewModel()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Handle permissions if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.DARK   -> true
                ThemeMode.LIGHT  -> false
                ThemeMode.SYSTEM -> systemDark
            }

            FamilyCodeTheme(darkTheme = isDark) {
                val appsScriptUrl by repository.appsScriptUrl.collectAsState(initial = null)
                val apiKey by repository.apiKey.collectAsState(initial = null)

                var isSetupComplete by remember(appsScriptUrl, apiKey) {
                    mutableStateOf(!appsScriptUrl.isNullOrBlank() && !apiKey.isNullOrBlank())
                }

                if (isSetupComplete) {
                    HomeScreen(
                        viewModel = viewModel,
                        onSettingsClick = { isSetupComplete = false }
                    )
                } else {
                    SetupScreen(viewModel = viewModel, onSetupComplete = {
                        isSetupComplete = true
                    })
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
