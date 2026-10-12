package com.minimal.carlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minimal.carlauncher.service.DiagnosticEvent
import com.minimal.carlauncher.service.DiagnosticEventType
import com.minimal.carlauncher.service.DiagnosticsManager
import com.minimal.carlauncher.service.PackageDiagnosticInfo
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentBlue
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.AccentRed
import com.minimal.carlauncher.ui.theme.CarBg
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import com.minimal.carlauncher.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    viewModel: LauncherViewModel
) {
    if (!isOpen) return

    val context = LocalContext.current
    val events by viewModel.diagnosticEvents.collectAsState()
    val radioStation by viewModel.radioStation.collectAsState()
    val isRadioActive by viewModel.isRadioActive.collectAsState()
    val savedStations by viewModel.savedRadioStations.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshCounter by remember { mutableIntStateOf(0) }

    var packages by remember { mutableStateOf<List<PackageDiagnosticInfo>>(emptyList()) }
    var settingsList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val sysInfo = remember(context) { DiagnosticsManager.getSystemInfo(context) }

    LaunchedEffect(refreshCounter) {
        withContext(Dispatchers.IO) {
            val pkgs = DiagnosticsManager.getInstalledRadioPackages(context)
            val stg = DiagnosticsManager.getAutomotiveSettings(context)
            withContext(Dispatchers.Main) {
                packages = pkgs
                settingsList = stg
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Fullscreen Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            // Modal Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 360.dp, max = 860.dp)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CarSurface)
                    .border(1.5.dp, CarBorder, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Prevent click through */ }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SYSTEM & RADIO DIAGNOSTICS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Real-time intent inspector & hardware information",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Action Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Copy Full Report Button
                        Button(
                            onClick = {
                                viewModel.copyDiagnosticsReport()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy Full Report",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.saveDiagnosticsReport() },
                            colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guardar",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { viewModel.shareDiagnosticsReport() },
                            colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Compartir",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Refresh Button
                        Button(
                            onClick = { refreshCounter++ },
                            colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Refresh",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Clear Log Button
                        Button(
                            onClick = { viewModel.clearDiagnosticsLog() },
                            colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Clear Log",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Ping Radio Hardware Button
                        Button(
                            onClick = { viewModel.pingRadio() },
                            colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ping Tuner",
                                color = AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Tab Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarBg)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            "Live Events (${events.size})",
                            "Radio & Settings",
                            "Packages (${packages.size})",
                            "Device Specs"
                        )
                        tabs.forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CarSurfaceVariant else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) AccentCyan.copy(alpha = 0.6f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentCyan else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Tab Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarBg.copy(alpha = 0.8f))
                            .border(1.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        when (selectedTab) {
                            0 -> EventsTabContent(events)
                            1 -> RadioSettingsTabContent(radioStation, isRadioActive, savedStations, settingsList)
                            2 -> PackagesTabContent(packages)
                            3 -> DeviceSpecsTabContent(sysInfo)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 5. Footer Hint & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "💡 Tip: Tap 'Copy Full Report' to easily send diagnostics, or check Events while using radio buttons.",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "Close",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsTabContent(events: List<DiagnosticEvent>) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "No events logged yet",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Text(
                    text = "Tap radio buttons, steer controls, or open the stock radio app to see live events appear here.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events, key = { it.id }) { event ->
                EventCard(event)
            }
        }
    }
}

@Composable
private fun EventCard(event: DiagnosticEvent) {
    val (badgeBg, badgeColor, badgeLabel) = when (event.type) {
        DiagnosticEventType.INCOMING_INTENT -> Triple(AccentCyan.copy(alpha = 0.15f), AccentCyan, "INCOMING")
        DiagnosticEventType.OUTGOING_INTENT -> Triple(AccentAmber.copy(alpha = 0.15f), AccentAmber, "OUTGOING")
        DiagnosticEventType.SETTINGS_CHANGE -> Triple(AccentGreen.copy(alpha = 0.15f), AccentGreen, "SETTING")
        DiagnosticEventType.RADIO_LOG -> Triple(AccentBlue.copy(alpha = 0.15f), AccentBlue, "RADIO")
        DiagnosticEventType.SYSTEM_EVENT -> Triple(AccentRed.copy(alpha = 0.15f), AccentRed, "SYSTEM")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CarSurfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, CarBorder.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Tag & Time Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                Text(
                    text = event.action,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Text(
                text = event.timestamp,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }

        if (event.target != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Target: ${event.target}",
                fontSize = 10.sp,
                color = AccentCyan.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Monospaced Details Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CarBg)
                .padding(6.dp)
        ) {
            Text(
                text = event.details,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun RadioSettingsTabContent(
    radioStation: String?,
    isRadioActive: Boolean,
    presets: List<String>,
    settingsList: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CarSurfaceVariant.copy(alpha = 0.7f))
                .border(1.dp, CarBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "CURRENT LAUNCHER RADIO STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Current Frequency:", fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = radioStation ?: "None / Inactive",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (radioStation != null) AccentGreen else TextMuted
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Radio Active Flag:", fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = if (isRadioActive) "Active" else "Idle",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRadioActive) AccentGreen else TextMuted
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Presets:", fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = presets.joinToString(", "),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
        }

        // System Settings Keys Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CarSurfaceVariant.copy(alpha = 0.7f))
                .border(1.dp, CarBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "DETECTED AUTOMOTIVE SYSTEM SETTINGS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )

            if (settingsList.isEmpty()) {
                Text(
                    text = "(No automotive radio keys currently found in Settings.System/Global)",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            } else {
                for ((key, value) in settingsList) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = key,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                        Text(
                            text = value,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = AccentGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackagesTabContent(packages: List<PackageDiagnosticInfo>) {
    if (packages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No vendor radio packages detected on device",
                fontSize = 12.sp,
                color = TextMuted
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(packages, key = { it.packageName }) { pkg ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CarSurfaceVariant.copy(alpha = 0.7f))
                        .border(1.dp, CarBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = pkg.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (pkg.isSystem) AccentGreen.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (pkg.isSystem) "SYSTEM APP" else "USER APP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pkg.isSystem) AccentGreen else TextMuted
                            )
                        }
                    }

                    Text(
                        text = pkg.packageName,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentCyan
                    )

                    Text(
                        text = "Version: ${pkg.versionName} (${pkg.versionCode})",
                        fontSize = 10.sp,
                        color = TextMuted
                    )

                    if (pkg.receivers.isNotEmpty()) {
                        Text(
                            text = "Receivers (${pkg.receivers.size}): ${pkg.receivers.joinToString(", ")}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }

                    if (pkg.services.isNotEmpty()) {
                        Text(
                            text = "Services (${pkg.services.size}): ${pkg.services.joinToString(", ")}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSpecsTabContent(sys: com.minimal.carlauncher.service.SystemDiagnosticInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val specs = listOf(
            "Device Model" to "${sys.manufacturer} ${sys.model}",
            "Brand / Product" to "${sys.brand} / ${sys.product}",
            "Board / Hardware" to "${sys.board} / ${sys.hardware}",
            "Android OS" to "Android ${sys.androidVersion} (API ${sys.apiLevel})",
            "Screen Metrics" to sys.screenMetrics,
            "Build ID / Display" to sys.buildDisplay,
            "Fingerprint" to sys.fingerprint
        )

        for ((label, value) in specs) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CarSurfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(text = label, fontSize = 10.sp, color = TextMuted)
                Text(
                    text = value,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
        }
    }
}
