package com.hrv.biofeedback.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "hrv_settings")

data class AppSettings(
    val sessionDurationMinutes: Int = 20,
    val defaultBreathingRate: Double = 6.0,
    val inhaleRatio: Double = 0.4,
    val vibrationEnabled: Boolean = true,
    val audioCuesEnabled: Boolean = true,
    val audioVolume: Int = 50
)

/**
 * User physiological profile for age/sex-adjusted HRV norm comparisons.
 *
 * HRV norms vary significantly by age and sex (Nunan et al. 2010, Shaffer & Ginsberg 2017):
 * - RMSSD declines ~3-4 ms per decade after age 30
 * - Males typically have lower RMSSD than females in younger groups
 * - Height correlates with RF (taller = lower RF, Vaschillo)
 * - Fitness level affects resting HR and HRV baseline
 */
data class UserProfile(
    val birthYear: Int = 0,          // 0 = not set
    val sex: String = "",            // "male", "female", "other", "" = not set
    val heightCm: Int = 0,           // 0 = not set
    val weightKg: Int = 0,           // 0 = not set
    val fitnessLevel: String = "",   // "sedentary", "moderate", "active", "athlete"
    val hasConditions: String = ""   // comma-separated: "hypertension,anxiety,asthma" etc
) {
    val isComplete: Boolean get() = birthYear > 1900 && sex.isNotEmpty()
    val age: Int get() = if (birthYear > 0) java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - birthYear else 0
}

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SESSION_DURATION = intPreferencesKey("session_duration_minutes")
        val BREATHING_RATE = doublePreferencesKey("default_breathing_rate")
        val INHALE_RATIO = doublePreferencesKey("inhale_ratio")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val AUDIO_CUES_ENABLED = booleanPreferencesKey("audio_cues_enabled")
        val AUDIO_VOLUME = intPreferencesKey("audio_volume")

        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val SEX = stringPreferencesKey("sex")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val WEIGHT_KG = intPreferencesKey("weight_kg")
        val FITNESS_LEVEL = stringPreferencesKey("fitness_level")
        val CONDITIONS = stringPreferencesKey("conditions")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            sessionDurationMinutes = prefs[Keys.SESSION_DURATION] ?: 20,
            defaultBreathingRate = prefs[Keys.BREATHING_RATE] ?: 6.0,
            inhaleRatio = prefs[Keys.INHALE_RATIO] ?: 0.4,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            audioCuesEnabled = prefs[Keys.AUDIO_CUES_ENABLED] ?: true,
            audioVolume = prefs[Keys.AUDIO_VOLUME] ?: 50
        )
    }

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            birthYear = prefs[Keys.BIRTH_YEAR] ?: 0,
            sex = prefs[Keys.SEX] ?: "",
            heightCm = prefs[Keys.HEIGHT_CM] ?: 0,
            weightKg = prefs[Keys.WEIGHT_KG] ?: 0,
            fitnessLevel = prefs[Keys.FITNESS_LEVEL] ?: "",
            hasConditions = prefs[Keys.CONDITIONS] ?: ""
        )
    }

    suspend fun setSessionDuration(minutes: Int) { context.dataStore.edit { it[Keys.SESSION_DURATION] = minutes } }
    suspend fun setBreathingRate(rate: Double) { context.dataStore.edit { it[Keys.BREATHING_RATE] = rate } }
    suspend fun setInhaleRatio(ratio: Double) { context.dataStore.edit { it[Keys.INHALE_RATIO] = ratio } }
    suspend fun setVibrationEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled } }
    suspend fun setAudioCuesEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.AUDIO_CUES_ENABLED] = enabled } }
    suspend fun setAudioVolume(volume: Int) { context.dataStore.edit { it[Keys.AUDIO_VOLUME] = volume } }

    suspend fun setBirthYear(year: Int) { context.dataStore.edit { it[Keys.BIRTH_YEAR] = year } }
    suspend fun setSex(sex: String) { context.dataStore.edit { it[Keys.SEX] = sex } }
    suspend fun setHeightCm(cm: Int) { context.dataStore.edit { it[Keys.HEIGHT_CM] = cm } }
    suspend fun setWeightKg(kg: Int) { context.dataStore.edit { it[Keys.WEIGHT_KG] = kg } }
    suspend fun setFitnessLevel(level: String) { context.dataStore.edit { it[Keys.FITNESS_LEVEL] = level } }
    suspend fun setConditions(conditions: String) { context.dataStore.edit { it[Keys.CONDITIONS] = conditions } }
}
