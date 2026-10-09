package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimal.carlauncher.ui.home.HomeOverlays
import com.minimal.carlauncher.ui.theme.CarBg
import com.minimal.carlauncher.ui.viewmodel.LauncherViewModel

/**
 * Legacy map dashboard (circular map, radio card, speedometer). No longer the home screen —
 * kept for reference; [com.minimal.carlauncher.ui.home.HomeScreen] replaces it.
 */
@Composable
fun DashboardScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val currentSpeed by viewModel.speedometer.currentSpeed.collectAsState()
    val speedUnit by viewModel.speedometer.unit.collectAsState()
    val speedKmH = if (speedUnit == com.minimal.carlauncher.service.SpeedUnit.KMH) {
        currentSpeed.toFloat()
    } else {
        currentSpeed * 1.60934f
    }
    val bearing by viewModel.speedometer.bearing.collectAsState()
    val cardinalDirection by viewModel.speedometer.cardinalDirection.collectAsState()
    val currentLocation by viewModel.speedometer.currentLocation.collectAsState()
    val isGpsActive by viewModel.speedometer.isGpsActive.collectAsState()

    val allApps by viewModel.allApps.collectAsState()
    val pinnedApps by viewModel.pinnedApps.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDrawerOpen by viewModel.isAppDrawerOpen.collectAsState()
    val zlinkApp by viewModel.zlinkApp.collectAsState()
    val navigationApp by viewModel.navigationApp.collectAsState()
    val musicApp by viewModel.musicApp.collectAsState()
    val dvrApp by viewModel.dvrApp.collectAsState()
    val radioStation by viewModel.radioStation.collectAsState()
    val isRadioActive by viewModel.isRadioActive.collectAsState()
    val savedRadioStations by viewModel.savedRadioStations.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    // Dialog States
    val selectedDockApp by viewModel.selectedDockAppForAction.collectAsState()
    val isReplacePickerOpen by viewModel.isReplacePickerOpen.collectAsState()
    val isAddDockPickerOpen by viewModel.isAddDockPickerOpen.collectAsState()
    val selectedDrawerApp by viewModel.selectedDrawerAppForAction.collectAsState()
    val isNavPickerOpen by viewModel.isNavPickerOpen.collectAsState()
    val isMusicPickerOpen by viewModel.isMusicPickerOpen.collectAsState()
    val isDvrPickerOpen by viewModel.isDvrPickerOpen.collectAsState()

    val currentVersion = viewModel.currentVersion
    val isAboutDialogOpen by viewModel.isAboutDialogOpen.collectAsState()
    val isDiagnosticsDialogOpen by viewModel.isDiagnosticsDialogOpen.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateDownloadProgress.collectAsState()

    // Minimap Navigation State
    val isSearchDialogOpen by viewModel.isAddressSearchOpen.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()
    val isCalculatingRoute by viewModel.isCalculatingRoute.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarBg)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Telemetry (Clock, Radio) + Left Bottom Dock (Apps, Pinned, +)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClockWidget(
                    timeFlow = viewModel.currentTime,
                    secondsFlow = viewModel.currentSeconds,
                    dateFlow = viewModel.currentDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.72f)
                )

                RadioStationWidget(
                    radioStation = radioStation,
                    isRadioActive = isRadioActive,
                    savedStations = savedRadioStations,
                    onTunePrevious = { viewModel.tunePreviousStation() },
                    onTuneNext = { viewModel.tuneNextStation() },
                    onSelectSavedStation = { station, index -> viewModel.tuneToSavedStation(station, index) },
                    onSaveCurrentStation = { index -> viewModel.saveCurrentStationToPreset(index) },
                    onLaunchRadio = { viewModel.launchMusic() },
                    onShowDiagnostic = { viewModel.showRadioDiagnostic() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.48f)
                )

                LeftBottomDock(
                    pinnedApps = pinnedApps,
                    onOpenAppDrawer = { viewModel.openAppDrawer() },
                    onLaunchApp = { app -> viewModel.launchApp(app) },
                    onLongClickApp = { app -> viewModel.onDockAppLongClick(app) },
                    onAddApp = { viewModel.openAddDockPicker() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Center Column: Master Circular Moving Map Portal (FULL VERTICAL SPACE AVAILABLE)
            CircularMapPortal(
                location = currentLocation,
                bearing = bearing,
                cardinalDirection = cardinalDirection,
                speedKmH = speedKmH,
                isGpsActive = isGpsActive,
                speed = currentSpeed,
                speedUnit = speedUnit,
                onToggleSpeedUnit = { viewModel.toggleSpeedUnit() },
                activeRoute = activeRoute,
                isNavigating = isNavigating,
                isCalculatingRoute = isCalculatingRoute,
                onStartNavigation = { dest ->
                    viewModel.startNavigationTo(dest.latitude, dest.longitude)
                },
                onStopNavigation = { viewModel.stopNavigation() },
                onOpenSearch = { viewModel.openAddressSearch() },
                onOpenNavigation = { viewModel.launchNavigation() },
                isMapDarkMode = isDarkMode,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )

            // Right Column: Quick-Launch Tiles + Right Bottom Dock (About, Settings)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickLaunchCards(
                    dvrApp = dvrApp,
                    zlinkLabel = zlinkApp?.label ?: "ZLink",
                    navApp = navigationApp,
                    musicApp = musicApp,
                    onLaunchDvr = { viewModel.launchDvr() },
                    onLongClickDvr = { viewModel.openDvrPicker() },
                    onLaunchZLink = { viewModel.launchZLink() },
                    onLaunchNavigation = { viewModel.launchNavigation() },
                    onLaunchMusic = { viewModel.launchMusic() },
                    onLongClickNavigation = { viewModel.openNavPicker() },
                    onLongClickMusic = { viewModel.openMusicPicker() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                RightBottomDock(
                    onOpenAbout = { viewModel.openAboutDialog() },
                    onOpenSettings = { viewModel.launchSettings() },
                    onLongPressSettings = { viewModel.openDiagnosticsDialog() },
                    isUpdateAvailable = updateInfo?.isUpdateAvailable == true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HomeOverlays(viewModel)
    }
}
