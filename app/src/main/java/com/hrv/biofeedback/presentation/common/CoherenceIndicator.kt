package com.hrv.biofeedback.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hrv.biofeedback.domain.model.CoherenceLevel
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.CoherenceLow
import com.hrv.biofeedback.presentation.theme.CoherenceMedium

@Composable
fun CoherenceIndicator(
    level: CoherenceLevel,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        CoherenceLevel.HIGH -> CoherenceHigh
        CoherenceLevel.MEDIUM -> CoherenceMedium
        CoherenceLevel.LOW -> CoherenceLow
    }

    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = level.name,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}
