package com.motodrive.tft.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { appSettings ->
                _settings.value = appSettings
            }
        }
    }

    fun updateSpeedUnit(unit: String) = viewModelScope.launch {
        repository.setSpeedUnit(unit)
    }

    fun updateTemperatureUnit(unit: String) = viewModelScope.launch {
        repository.setTemperatureUnit(unit)
    }

    fun update24HourClock(enabled: Boolean) = viewModelScope.launch {
        repository.set24HourClock(enabled)
    }

    fun updateKeepScreenOn(enabled: Boolean) = viewModelScope.launch {
        repository.setKeepScreenOn(enabled)
    }

    fun updateBluetoothAutoLaunch(enabled: Boolean) = viewModelScope.launch {
        repository.setBluetoothAutoLaunch(enabled)
    }

    fun updateHighFrequencyGps(enabled: Boolean) = viewModelScope.launch {
        repository.setHighFrequencyGps(enabled)
    }

    fun updateClusterStyle(style: String) = viewModelScope.launch {
        repository.setClusterStyle(style)
    }

    fun updateThemeStyle(theme: String) = viewModelScope.launch {
        repository.setThemeStyle(theme)
    }

    fun updateIphoneNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setIphoneNotificationsEnabled(enabled)
    }

    fun updateShowNotificationPreviews(enabled: Boolean) = viewModelScope.launch {
        repository.setShowNotificationPreviews(enabled)
    }

    fun updateNotifyIncomingCalls(enabled: Boolean) = viewModelScope.launch {
        repository.setNotifyIncomingCalls(enabled)
    }

    fun updateNotifyWhatsApp(enabled: Boolean) = viewModelScope.launch {
        repository.setNotifyWhatsApp(enabled)
    }

    fun updateNotifyInstagram(enabled: Boolean) = viewModelScope.launch {
        repository.setNotifyInstagram(enabled)
    }

    fun updateNotifySms(enabled: Boolean) = viewModelScope.launch {
        repository.setNotifySms(enabled)
    }

    fun updateNotifySnapchat(enabled: Boolean) = viewModelScope.launch {
        repository.setNotifySnapchat(enabled)
    }
}

