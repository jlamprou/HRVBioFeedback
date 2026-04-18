package com.hrv.biofeedback.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- User Profile (for age/sex-adjusted norms) ---
            SectionHeader("Your Profile")
            SettingsCard {
                SliderSetting(
                    label = "Birth Year",
                    value = profile.birthYear.toFloat().coerceIn(1940f, 2010f),
                    valueLabel = if (profile.birthYear > 0) "${profile.birthYear} (age ${profile.age})" else "Not set",
                    range = 1940f..2010f,
                    steps = 69,
                    onValueChange = { viewModel.setBirthYear(it.toInt()) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Sex selection
                Text("Sex", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("male", "female", "other").forEach { option ->
                        FilterChip(
                            selected = profile.sex == option,
                            onClick = { viewModel.setSex(option) },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SliderSetting(
                    label = "Height",
                    value = profile.heightCm.toFloat().coerceIn(140f, 210f),
                    valueLabel = if (profile.heightCm > 0) "${profile.heightCm} cm" else "Not set",
                    range = 140f..210f,
                    steps = 69,
                    onValueChange = { viewModel.setHeightCm(it.toInt()) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SliderSetting(
                    label = "Weight",
                    value = profile.weightKg.toFloat().coerceIn(40f, 150f),
                    valueLabel = if (profile.weightKg > 0) "${profile.weightKg} kg" else "Not set",
                    range = 40f..150f,
                    steps = 109,
                    onValueChange = { viewModel.setWeightKg(it.toInt()) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Fitness level
                Text("Fitness Level", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("sedentary", "moderate", "active", "athlete").forEach { option ->
                        FilterChip(
                            selected = profile.fitnessLevel == option,
                            onClick = { viewModel.setFitnessLevel(option) },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Session Defaults ---
            SectionHeader("Session Defaults")
            SettingsCard {
                SliderSetting(
                    label = "Session Duration",
                    value = settings.sessionDurationMinutes.toFloat(),
                    valueLabel = "${settings.sessionDurationMinutes} min",
                    range = 5f..60f,
                    steps = 10,
                    onValueChange = { viewModel.setSessionDuration(it.toInt()) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SliderSetting(
                    label = "Default Breathing Rate",
                    value = settings.defaultBreathingRate.toFloat(),
                    valueLabel = "%.1f bpm".format(settings.defaultBreathingRate),
                    range = 4.0f..8.0f,
                    steps = 7,
                    onValueChange = { viewModel.setBreathingRate(it.toDouble()) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SliderSetting(
                    label = "Inhale / Exhale Ratio",
                    value = (settings.inhaleRatio * 100).toFloat(),
                    valueLabel = "%.0f%% / %.0f%%".format(
                        settings.inhaleRatio * 100,
                        (1.0 - settings.inhaleRatio) * 100
                    ),
                    range = 30f..50f,
                    steps = 3,
                    onValueChange = { viewModel.setInhaleRatio(it.toDouble() / 100.0) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Breathing Pacer ---
            SectionHeader("Breathing Pacer")
            SettingsCard {
                SwitchSetting(
                    label = "Vibration Guidance",
                    description = "Haptic pulses on inhale/exhale transitions",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = { viewModel.setVibrationEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                SwitchSetting(
                    label = "Audio Cues",
                    description = "Tone on inhale start, lower tone on exhale",
                    checked = settings.audioCuesEnabled,
                    onCheckedChange = { viewModel.setAudioCuesEnabled(it) }
                )

                if (settings.audioCuesEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    SliderSetting(
                        label = "Audio Volume",
                        value = settings.audioVolume.toFloat(),
                        valueLabel = "${settings.audioVolume}%",
                        range = 0f..100f,
                        steps = 9,
                        onValueChange = { viewModel.setAudioVolume(it.toInt()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Experimental ---
            SectionHeader("Experimental")
            SettingsCard {
                SwitchSetting(
                    label = "Adaptive Breathing Rate (BETA)",
                    description = "Guided Training will auto-adjust breathing rate in real-time " +
                            "based on your HRV response (\u00B10.5 bpm from baseline, max 0.1 bpm per " +
                            "2-min step). This is an emerging technique (Laborde et al. 2022) with " +
                            "limited validation. Changes apply at the end of each breathing cycle " +
                            "to avoid disrupting your rhythm.",
                    checked = settings.adaptiveBreathingEnabled,
                    onCheckedChange = { viewModel.setAdaptiveBreathingEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- About ---
            SectionHeader("About")
            SettingsCard {
                InfoRow("Version", "1.0.0")
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                InfoRow("RF Protocol", "Lehrer / Shaffer et al. 2020")
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                InfoRow("Spectral Method", "AR-16 Burg's Method")
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                InfoRow("Coherence", "HeartMath (McCraty 2022)")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary
            )
        )
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Primary)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
