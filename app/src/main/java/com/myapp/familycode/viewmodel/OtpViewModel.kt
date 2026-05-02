package com.myapp.familycode.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.familycode.data.model.OtpItem
import com.myapp.familycode.data.repository.OtpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OtpViewModel(private val repository: OtpRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deviceCount = MutableStateFlow(0)
    val deviceCount: StateFlow<Int> = _deviceCount.asStateFlow()

    private val _otpList = MutableStateFlow<List<OtpItem>>(emptyList())
    val otpList: StateFlow<List<OtpItem>> = _otpList.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val response = repository.fetchLatestOtps()
            if (response.success) {
                _deviceCount.value = response.deviceCount ?: 0
                _otpList.value = response.recentOtps ?: emptyList()
            } else {
                _error.value = response.error ?: "Unknown error"
            }
            _isLoading.value = false
        }
    }

    fun saveSetup(url: String, key: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.saveSettings(url, key)
            onComplete()
        }
    }

    suspend fun testConnection(url: String, key: String): String? {
        return repository.testConnection(url, key)
    }
}
