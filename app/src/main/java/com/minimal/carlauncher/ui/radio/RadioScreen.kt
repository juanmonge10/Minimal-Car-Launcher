package com.minimal.carlauncher.ui.radio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarBg
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary

/**
 * Launcher's own full-screen radio. It is a remote control for the head unit tuner
 * (via [com.minimal.carlauncher.service.RadioManager]); audio still comes from the system radio.
 */
@Composable
fun RadioScreen(
    isOpen: Boolean,
    radioStation: String?,
    isRadioActive: Boolean,
    presets: List<String>,
    presetNames: List<String>,
    useAsDefault: Boolean,
    externalAppLabel: String?,
    onUseAsDefaultChange: (Boolean) -> Unit,
    onSeekPrevious: () -> Unit,
    onSeekNext: () -> Unit,
    onFineTune: (Double) -> Unit,
    onSelectPreset: (String, Int) -> Unit,
    onSavePreset: (Int) -> Unit,
    onOpenExternalApp: () -> Unit,
    onChooseExternalApp: () -> Unit,
    onShowDiagnostic: () -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return

    // Station text arrives as "95.2 FM • NAME" (band and name optional)
    val parts = radioStation?.split("•")?.map { it.trim() }
    val freqTokens = parts?.firstOrNull()?.split(" ")?.filter { it.isNotBlank() }
    val frequency = freqTokens?.firstOrNull()
    val band = freqTokens?.getOrNull(1) ?: "FM"
    val stationName = parts?.getOrNull(1)?.takeIf { it.isNotBlank() }
    val isOnAir = frequency != null || isRadioActive

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CarBg)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleButton(Icons.Default.Close, "Cerrar", onClose, size = 52.dp)
                Text(
                    text = "Radio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                StatusDot(isOnAir)
                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .background(CarSurface)
                        .clickable { onUseAsDefaultChange(!useAsDefault) }
                        .padding(start = 16.dp, end = 8.dp)
                        .height(52.dp)
                ) {
                    Text(
                        text = "Abrir al tocar la radio",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = useAsDefault,
                        onCheckedChange = onUseAsDefaultChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = AccentCyan,
                            checkedThumbColor = Color.White
                        )
                    )
                }

                PillButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = externalAppLabel ?: "App de radio",
                    onClick = onOpenExternalApp,
                    onLongClick = onChooseExternalApp
                )
                CircleButton(Icons.Default.Info, "Diagnóstico de radio", onShowDiagnostic, size = 52.dp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val isWide = maxWidth >= 720.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        TunerPanel(
                            frequency, band, stationName, onSeekPrevious, onSeekNext, onFineTune,
                            Modifier.weight(1f).fillMaxHeight()
                        )
                        PresetGrid(
                            presets, presetNames, frequency, columns = 3,
                            onSelectPreset = onSelectPreset, onSavePreset = onSavePreset,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TunerPanel(
                            frequency, band, stationName, onSeekPrevious, onSeekNext, onFineTune,
                            Modifier.fillMaxWidth().weight(1f)
                        )
                        PresetGrid(
                            presets, presetNames, frequency, columns = 4,
                            onSelectPreset = onSelectPreset, onSavePreset = onSavePreset,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Toca una presintonía para sintonizar · mantenla pulsada para guardar la emisora actual · " +
                        "mantén pulsado el botón de app para elegir otra app de radio",
                fontSize = 12.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TunerPanel(
    frequency: String?,
    band: String,
    stationName: String?,
    onSeekPrevious: () -> Unit,
    onSeekNext: () -> Unit,
    onFineTune: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CarSurface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = frequency ?: "--.-",
                fontSize = 88.sp,
                lineHeight = 88.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = band,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentCyan,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Text(
            text = stationName ?: if (frequency != null) "Sin nombre de emisora" else "Esperando datos de la radio…",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircleButton(Icons.Default.SkipPrevious, "Buscar anterior", onSeekPrevious, size = 76.dp, filled = true)
            CircleButton(Icons.Default.Remove, "Bajar 0.1 MHz", { onFineTune(-0.1) }, size = 56.dp)
            CircleButton(Icons.Default.Add, "Subir 0.1 MHz", { onFineTune(0.1) }, size = 56.dp)
            CircleButton(Icons.Default.SkipNext, "Buscar siguiente", onSeekNext, size = 76.dp, filled = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetGrid(
    presets: List<String>,
    presetNames: List<String>,
    currentFrequency: String?,
    columns: Int,
    onSelectPreset: (String, Int) -> Unit,
    onSavePreset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.indices.chunked(columns).forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowIndices.forEach { index ->
                    val preset = presets[index]
                    val name = presetNames.getOrNull(index).orEmpty()
                    val isEmpty = preset.isBlank()
                    val isCurrent = !isEmpty && currentFrequency == preset
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isCurrent) AccentCyan else CarSurface)
                            .border(
                                1.dp,
                                if (isEmpty) CarBorder else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(
                                onClick = { onSelectPreset(preset, index) },
                                onLongClick = { onSavePreset(index) }
                            )
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isEmpty) "+" else preset,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isCurrent -> Color.White
                                isEmpty -> TextMuted
                                else -> TextPrimary
                            },
                            maxLines = 1
                        )
                        Text(
                            text = when {
                                isEmpty -> "Mantén para guardar"
                                name.isNotBlank() -> name
                                else -> "P${index + 1}"
                            },
                            fontSize = 12.sp,
                            color = if (isCurrent) Color.White.copy(alpha = 0.85f) else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Keep cell widths equal on a short last row
                repeat(columns - rowIndices.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatusDot(isOnAir: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CarSurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isOnAir) AccentGreen else TextMuted)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isOnAir) "En el aire" else "Sin datos",
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    filled: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) AccentCyan else CarSurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) Color.White else TextPrimary,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PillButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(CarSurface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp)
        )
    }
}
