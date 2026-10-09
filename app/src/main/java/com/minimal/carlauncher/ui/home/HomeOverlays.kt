package com.minimal.carlauncher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.minimal.carlauncher.ui.dialogs.AboutDialog
import com.minimal.carlauncher.ui.dialogs.AddressSearchDialog
import com.minimal.carlauncher.ui.dialogs.AppPickerDialog
import com.minimal.carlauncher.ui.dialogs.DiagnosticsDialog
import com.minimal.carlauncher.ui.dialogs.DockActionDialog
import com.minimal.carlauncher.ui.dialogs.DrawerActionDialog
import com.minimal.carlauncher.ui.drawer.AppDrawerDialog
import com.minimal.carlauncher.ui.viewmodel.LauncherViewModel

/**
 * All overlay windows of the launcher (app drawer, pickers, action dialogs, about, diagnostics).
 * Shared by every home layout so the drawer, search, pinning and pickers behave identically.
 */
@Composable
fun HomeOverlays(viewModel: LauncherViewModel) {
    val allApps by viewModel.allApps.collectAsState()
    val pinnedApps by viewModel.pinnedApps.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDrawerOpen by viewModel.isAppDrawerOpen.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val currentLocation by viewModel.speedometer.currentLocation.collectAsState()

    val selectedDockApp by viewModel.selectedDockAppForAction.collectAsState()
    val isReplacePickerOpen by viewModel.isReplacePickerOpen.collectAsState()
    val isAddDockPickerOpen by viewModel.isAddDockPickerOpen.collectAsState()
    val selectedDrawerApp by viewModel.selectedDrawerAppForAction.collectAsState()
    val isNavPickerOpen by viewModel.isNavPickerOpen.collectAsState()
    val isMusicPickerOpen by viewModel.isMusicPickerOpen.collectAsState()
    val isDvrPickerOpen by viewModel.isDvrPickerOpen.collectAsState()

    val isAboutDialogOpen by viewModel.isAboutDialogOpen.collectAsState()
    val isDiagnosticsDialogOpen by viewModel.isDiagnosticsDialogOpen.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateDownloadProgress.collectAsState()
    val isSearchDialogOpen by viewModel.isAddressSearchOpen.collectAsState()

    // All Apps Drawer
    AppDrawerDialog(
        isOpen = isDrawerOpen,
        apps = filteredApps,
        searchQuery = searchQuery,
        onSearchChange = { viewModel.onSearchQueryChange(it) },
        onAppClick = { app -> viewModel.launchApp(app) },
        onAppLongClick = { app -> viewModel.onDrawerAppLongClick(app) },
        onClose = { viewModel.closeAppDrawer() }
    )

    // Favorite Long-Press Action Dialog (Remove or Replace)
    DockActionDialog(
        app = selectedDockApp,
        onReplace = { viewModel.openReplacePicker() },
        onRemove = { selectedDockApp?.let { viewModel.removeDockApp(it) } },
        onDismiss = { viewModel.dismissDockActionDialog() }
    )

    AppPickerDialog(
        isOpen = isReplacePickerOpen,
        apps = allApps,
        title = "Elegir app para reemplazar",
        onAppSelected = { newApp -> viewModel.replaceDockAppWith(newApp) },
        onDismiss = { viewModel.closeReplacePicker() }
    )

    AppPickerDialog(
        isOpen = isAddDockPickerOpen,
        apps = allApps.filter { app -> pinnedApps.none { it.packageName == app.packageName } },
        title = "Añadir a favoritos",
        onAppSelected = { newApp -> viewModel.addDockApp(newApp) },
        onDismiss = { viewModel.closeAddDockPicker() }
    )

    AppPickerDialog(
        isOpen = isNavPickerOpen,
        apps = allApps,
        title = "App de navegación predeterminada",
        onAppSelected = { app -> viewModel.selectNavigationApp(app) },
        onDismiss = { viewModel.closeNavPicker() }
    )

    AppPickerDialog(
        isOpen = isMusicPickerOpen,
        apps = allApps,
        title = "App de música predeterminada",
        onAppSelected = { app -> viewModel.selectMusicApp(app) },
        onDismiss = { viewModel.closeMusicPicker() }
    )

    AppPickerDialog(
        isOpen = isDvrPickerOpen,
        apps = allApps,
        title = "App de dashcam / DVR",
        onAppSelected = { app -> viewModel.selectDvrApp(app) },
        onDismiss = { viewModel.closeDvrPicker() }
    )

    // Drawer Long-Press Action Dialog (Pin to favorites)
    DrawerActionDialog(
        app = selectedDrawerApp,
        onAddToDock = { selectedDrawerApp?.let { viewModel.pinDrawerAppToDock(it) } },
        onDismiss = { viewModel.dismissDrawerActionDialog() }
    )

    AboutDialog(
        isOpen = isAboutDialogOpen,
        currentVersion = viewModel.currentVersion,
        updateInfo = updateInfo,
        isCheckingUpdate = isCheckingUpdate,
        downloadProgress = updateProgress,
        onCheckUpdate = { viewModel.checkForUpdates(isManualCheck = true) },
        onInstall = { viewModel.startDownloadAndInstall() },
        onDismiss = { viewModel.dismissAboutDialog() },
        isDarkMode = isDarkMode,
        onToggleDarkMode = { viewModel.toggleDarkMode() }
    )

    // System & Radio Diagnostics (long-press Settings)
    DiagnosticsDialog(
        isOpen = isDiagnosticsDialogOpen,
        onDismiss = { viewModel.dismissDiagnosticsDialog() },
        viewModel = viewModel
    )

    // Address search (only reachable from the legacy map dashboard)
    AddressSearchDialog(
        isOpen = isSearchDialogOpen,
        currentLat = currentLocation?.latitude ?: 37.9838,
        currentLon = currentLocation?.longitude ?: 23.7275,
        onSelectDestination = { lat, lon, name ->
            viewModel.startNavigationTo(lat, lon, name)
        },
        onDismiss = { viewModel.closeAddressSearch() }
    )
}
