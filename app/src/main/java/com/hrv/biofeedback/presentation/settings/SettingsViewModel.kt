package com.hrv.biofeedback.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.data.local.preferences.AppSettings
import com.hrv.biofeedback.data.local.preferences.UserPreferences
import com.hrv.biofeedback.data.local.preferences.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val settings: StateFlow<AppSettings> = userPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val profile: StateFlow<UserProfile> = userPreferences.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    fun setSessionDuration(minutes: Int) { viewModelScope.launch { userPreferences.setSessionDuration(minutes) } }
    fun setBreathingRate(rate: Double) { viewModelScope.launch { userPreferences.setBreathingRate(rate) } }
    fun setInhaleRatio(ratio: Double) { viewModelScope.launch { userPreferences.setInhaleRatio(ratio) } }
    fun setVibrationEnabled(enabled: Boolean) { viewModelScope.launch { userPreferences.setVibrationEnabled(enabled) } }
    fun setAudioCuesEnabled(enabled: Boolean) { viewModelScope.launch { userPreferences.setAudioCuesEnabled(enabled) } }
    fun setAudioVolume(volume: Int) { viewModelScope.launch { userPreferences.setAudioVolume(volume) } }

    fun setBirthYear(year: Int) { viewModelScope.launch { userPreferences.setBirthYear(year) } }
    fun setSex(sex: String) { viewModelScope.launch { userPreferences.setSex(sex) } }
    fun setHeightCm(cm: Int) { viewModelScope.launch { userPreferences.setHeightCm(cm) } }
    fun setWeightKg(kg: Int) { viewModelScope.launch { userPreferences.setWeightKg(kg) } }
    fun setFitnessLevel(level: String) { viewModelScope.launch { userPreferences.setFitnessLevel(level) } }
    fun setConditions(conditions: String) { viewModelScope.launch { userPreferences.setConditions(conditions) } }
}
