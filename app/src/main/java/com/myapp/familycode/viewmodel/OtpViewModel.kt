package com.myapp.familycode.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.familycode.data.model.DeviceInfo
import com.myapp.familycode.data.model.OtpItem
import com.myapp.familycode.data.repository.OtpRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Controls which theme variant is applied. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class OtpViewModel(private val repository: OtpRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deviceCount = MutableStateFlow(0)
    val deviceCount: StateFlow<Int> = _deviceCount.asStateFlow()

    private val _deviceList = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val deviceList: StateFlow<List<DeviceInfo>> = _deviceList.asStateFlow()

    private val _otpList = MutableStateFlow<List<OtpItem>>(emptyList())
    val otpList: StateFlow<List<OtpItem>> = _otpList.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Theme mode — loaded from SharedPreferences on init
    private val _themeMode = MutableStateFlow(
        when (repository.getThemePreference()) {
            "light"  -> ThemeMode.LIGHT
            "dark"   -> ThemeMode.DARK
            else     -> ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Ticker that fires every second so OTP countdown timers stay live. */
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        refreshData()
        startTickerAndAutoRefresh()
    }

    // ----------------------------------------------------------------
    //  Data refresh
    // ----------------------------------------------------------------

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val response = repository.fetchLatestOtps()
            if (response.success) {
                _deviceCount.value = response.deviceCount ?: 0
                _deviceList.value  = response.deviceList  ?: emptyList()
                _otpList.value     = response.recentOtps  ?: emptyList()
            } else {
                _error.value = response.error ?: "Unknown error"
            }
            _isLoading.value = false
        }
    }

    // ----------------------------------------------------------------
    //  Ticker + auto-refresh every 30 s
    // ----------------------------------------------------------------

    private fun startTickerAndAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            var secondCount = 0
            while (isActive) {
                delay(1_000)
                _tick.value = System.currentTimeMillis()
                secondCount++
                // Auto-refresh data every 30 seconds
                if (secondCount % 30 == 0) {
                    refreshData()
                }
            }
        }
    }

    // ----------------------------------------------------------------
    //  Setup helpers
    // ----------------------------------------------------------------

    fun saveSetup(url: String, key: String, deviceName: String? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveSettings(url, key, deviceName)
            onComplete()
        }
    }

    suspend fun testConnection(url: String, key: String): String? {
        return repository.testConnection(url, key)
    }

    // ----------------------------------------------------------------
    //  Theme toggle
    // ----------------------------------------------------------------

    /**
     * Cycles SYSTEM → LIGHT → DARK → SYSTEM and persists the choice.
     */
    fun toggleTheme() {
        val next = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT  -> ThemeMode.DARK
            ThemeMode.DARK   -> ThemeMode.SYSTEM
        }
        _themeMode.value = next
        repository.saveThemePreference(
            when (next) {
                ThemeMode.LIGHT  -> "light"
                ThemeMode.DARK   -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        )
    }
}
