package com.myapp.familycode.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.familycode.data.model.DeviceInfo
import com.myapp.familycode.data.model.OtpItem
import com.myapp.familycode.data.repository.OtpRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Controls which theme variant is applied. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class OtpViewModel(private val repository: OtpRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Observe local Room DB directly
    val otpList: StateFlow<List<OtpItem>> = repository.localOtps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceList: StateFlow<List<DeviceInfo>> = repository.localDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derive device count from the local device list
    val deviceCount: StateFlow<Int> = deviceList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
            if (!response.success && !response.error.isNullOrBlank()) {
                _error.value = response.error
            }
            _isLoading.value = false
        }
    }

    // ----------------------------------------------------------------
    //  Swipe-to-Delete
    // ----------------------------------------------------------------

    fun deleteOtp(timestamp: String) {
        viewModelScope.launch {
            try {
                repository.deleteOtpLocallyAndRemotely(timestamp)
            } catch (e: Exception) {
                _error.value = e.message
                // Optionally refresh data here to revert local delete if remote fails
                refreshData()
            }
        }
    }

    fun clearError() {
        _error.value = null
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
