package com.minimal.carlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import com.minimal.carlauncher.ui.theme.TileOrange

/**
 * Compact single-row radio control replacing the old large radio card.
 * Tap the station to open the head unit radio, long-press for the tuner diagnostic.
 * Presets: tap to tune, long-press to store the current frequency (hidden on narrow screens).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioStrip(
    radioStation: String?,
    isRadioActive: Boolean,
    presets: List<String>,
    onOpenRadio: () -> Unit,
    onShowDiagnostic: () -> Unit,
    onTunePrevious: () -> Unit,
    onTuneNext: () -> Unit,
    onSelectPreset: (String, Int) -> Unit,
    onSavePreset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Station text arrives as "95.2 FM • NAME" (name optional)
    val parts = radioStation?.split("•")?.map { it.trim() }
    val frequency = parts?.firstOrNull()?.takeIf { it.isNotBlank() }
    val stationName = parts?.getOrNull(1)?.takeIf { it.isNotBlank() }
    val isOnAir = frequency != null || isRadioActive

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val showPresets = maxWidth >= 600.dp && presets.isNotEmpty()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CarSurface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = onOpenRadio, onLongClick = onShowDiagnostic)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(TileOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Radio",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = frequency ?: "Radio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isOnAir) AccentGreen else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stationName ?: if (isOnAir) "En el aire" else "Toca para abrir",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (showPresets) {
                presets.take(4).forEachIndexed { index, preset ->
                    val isCurrent = frequency != null && frequency.startsWith(preset)
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) AccentCyan else CarSurfaceVariant)
                            .combinedClickable(
                                onClick = { onSelectPreset(preset, index) },
                                onLongClick = { onSavePreset(index) }
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrent) Color.White else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            RoundControl(Icons.Default.SkipPrevious, "Emisora anterior", onTunePrevious)
            RoundControl(Icons.Default.SkipNext, "Emisora siguiente", onTuneNext)
        }
    }
}

@Composable
private fun RoundControl(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(CarSurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}
