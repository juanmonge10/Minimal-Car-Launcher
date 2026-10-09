package com.minimal.carlauncher.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minimal.carlauncher.data.AppInfo
import com.minimal.carlauncher.data.AppRepository
import com.minimal.carlauncher.service.DiagnosticsManager
import com.minimal.carlauncher.service.DiagnosticEvent
import com.minimal.carlauncher.service.NavigationRoute
import com.minimal.carlauncher.service.NavigationService
import com.minimal.carlauncher.service.RadioManager
import com.minimal.carlauncher.service.SpeedometerManager
import com.minimal.carlauncher.service.UpdateInfo
import com.minimal.carlauncher.service.UpdateManager
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    val speedometer = SpeedometerManager(application)
    val radioManager = RadioManager(application)
    private val updateManager = UpdateManager(application)

    val radioStation: StateFlow<String?> = radioManager.radioStation
    val isRadioActive: StateFlow<Boolean> = radioManager.isRadioActive
    val savedRadioStations: StateFlow<List<String>> = radioManager.savedStations

    // Applications State
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _pinnedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val pinnedApps: StateFlow<List<AppInfo>> = _pinnedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(_allApps, _searchQuery) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _zlinkApp = MutableStateFlow<AppInfo?>(null)
    val zlinkApp: StateFlow<AppInfo?> = _zlinkApp.asStateFlow()

    private val _navigationApp = MutableStateFlow<AppInfo?>(null)
    val navigationApp: StateFlow<AppInfo?> = _navigationApp.asStateFlow()

    private val _musicApp = MutableStateFlow<AppInfo?>(null)
    val musicApp: StateFlow<AppInfo?> = _musicApp.asStateFlow()

    private val _dvrApp = MutableStateFlow<AppInfo?>(null)
    val dvrApp: StateFlow<AppInfo?> = _dvrApp.asStateFlow()

    private val _isDarkMode = MutableStateFlow(repository.isDarkMode())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()
    val isMapDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private var hasAutoStartedDvr = false

    private val _isAppDrawerOpen = MutableStateFlow(false)
    val isAppDrawerOpen: StateFlow<Boolean> = _isAppDrawerOpen.asStateFlow()

    // Clock and Date State
    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _currentSeconds = MutableStateFlow("")
    val currentSeconds: StateFlow<String> = _currentSeconds.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    // Long press action dialog states
    private val _selectedDockAppForAction = MutableStateFlow<AppInfo?>(null)
    val selectedDockAppForAction: StateFlow<AppInfo?> = _selectedDockAppForAction.asStateFlow()

    private val _isReplacePickerOpen = MutableStateFlow(false)
    val isReplacePickerOpen: StateFlow<Boolean> = _isReplacePickerOpen.asStateFlow()

    private val _isAddDockPickerOpen = MutableStateFlow(false)
    val isAddDockPickerOpen: StateFlow<Boolean> = _isAddDockPickerOpen.asStateFlow()

    private val _selectedDrawerAppForAction = MutableStateFlow<AppInfo?>(null)
    val selectedDrawerAppForAction: StateFlow<AppInfo?> = _selectedDrawerAppForAction.asStateFlow()

    private val _isNavPickerOpen = MutableStateFlow(false)
    val isNavPickerOpen: StateFlow<Boolean> = _isNavPickerOpen.asStateFlow()

    private val _isMusicPickerOpen = MutableStateFlow(false)
    val isMusicPickerOpen: StateFlow<Boolean> = _isMusicPickerOpen.asStateFlow()

    private val _isDvrPickerOpen = MutableStateFlow(false)
    val isDvrPickerOpen: StateFlow<Boolean> = _isDvrPickerOpen.asStateFlow()

    // Minimap In-App Live Navigation & Search State
    private val _isAddressSearchOpen = MutableStateFlow(false)
    val isAddressSearchOpen: StateFlow<Boolean> = _isAddressSearchOpen.asStateFlow()

    private val _activeRoute = MutableStateFlow<NavigationRoute?>(null)
    val activeRoute: StateFlow<NavigationRoute?> = _activeRoute.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    private val _navigationDestination = MutableStateFlow<GeoPoint?>(null)
    val navigationDestination: StateFlow<GeoPoint?> = _navigationDestination.asStateFlow()

    private val _navigationDestinationName = MutableStateFlow("")
    val navigationDestinationName: StateFlow<String> = _navigationDestinationName.asStateFlow()

    // About & In-App Updater State
    val currentVersion: String = updateManager.getCurrentVersionName()

    private val _isAboutDialogOpen = MutableStateFlow(false)
    val isAboutDialogOpen: StateFlow<Boolean> = _isAboutDialogOpen.asStateFlow()

    // Diagnostics Dialog State
    private val _isDiagnosticsDialogOpen = MutableStateFlow(false)
    val isDiagnosticsDialogOpen: StateFlow<Boolean> = _isDiagnosticsDialogOpen.asStateFlow()
    val diagnosticEvents: StateFlow<List<DiagnosticEvent>> = DiagnosticsManager.events

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Int?>(null)
    val updateDownloadProgress: StateFlow<Int?> = _updateDownloadProgress.asStateFlow()

    init {
        loadApps()
        startClockTicker()
        radioManager.startMonitoring()
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = repository.getInstalledApps()
            val pinned = repository.getPinnedApps(apps)
            val zlink = apps.firstOrNull { it.isZLink }
            val nav = repository.resolveNavApp(apps)
            val music = repository.resolveMusicApp(apps)
            val dvr = repository.resolveDvrApp(apps)

            _allApps.value = apps
            _pinnedApps.value = pinned
            _zlinkApp.value = zlink
            _navigationApp.value = nav
            _musicApp.value = music
            _dvrApp.value = dvr
            hasAutoStartedDvr = true
        }
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val secondsFormat = SimpleDateFormat("ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())

            while (isActive) {
                val now = Date()
                _currentTime.value = timeFormat.format(now)
                _currentSeconds.value = secondsFormat.format(now)
                _currentDate.value = dateFormat.format(now)
                delay(1000L)
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun openAppDrawer() {
        _searchQuery.value = ""
        _isAppDrawerOpen.value = true
    }

    fun closeAppDrawer() {
        _isAppDrawerOpen.value = false
        _searchQuery.value = ""
    }

    fun launchApp(app: AppInfo) {
        repository.launchApp(app)
        closeAppDrawer()
    }

    // --- Dock Customization ---

    fun onDockAppLongClick(app: AppInfo) {
        _selectedDockAppForAction.value = app
    }

    fun dismissDockActionDialog() {
        _selectedDockAppForAction.value = null
    }

    fun removeDockApp(app: AppInfo) {
        repository.removePinnedApp(_allApps.value, app)
        _pinnedApps.value = repository.getPinnedApps(_allApps.value)
        _selectedDockAppForAction.value = null
        Toast.makeText(getApplication(), "Removed from bottom bar", Toast.LENGTH_SHORT).show()
    }

    fun openReplacePicker() {
        _isReplacePickerOpen.value = true
    }

    fun closeReplacePicker() {
        _isReplacePickerOpen.value = false
        _selectedDockAppForAction.value = null
    }

    fun replaceDockAppWith(newApp: AppInfo) {
        val oldApp = _selectedDockAppForAction.value ?: return
        repository.replacePinnedApp(_allApps.value, oldApp, newApp)
        _pinnedApps.value = repository.getPinnedApps(_allApps.value)
        _isReplacePickerOpen.value = false
        _selectedDockAppForAction.value = null
        Toast.makeText(getApplication(), "Replaced with ${newApp.label}", Toast.LENGTH_SHORT).show()
    }

    fun openAddDockPicker() {
        _isAddDockPickerOpen.value = true
    }

    fun closeAddDockPicker() {
        _isAddDockPickerOpen.value = false
    }

    fun addDockApp(newApp: AppInfo) {
        val added = repository.addPinnedApp(_allApps.value, newApp)
        _pinnedApps.value = repository.getPinnedApps(_allApps.value)
        _isAddDockPickerOpen.value = false
        if (added) {
            Toast.makeText(getApplication(), "Added ${newApp.label} to bottom bar", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "${newApp.label} is already in the bottom bar", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Drawer Long Press to Pin ---

    fun onDrawerAppLongClick(app: AppInfo) {
        _selectedDrawerAppForAction.value = app
    }

    fun dismissDrawerActionDialog() {
        _selectedDrawerAppForAction.value = null
    }

    fun pinDrawerAppToDock(app: AppInfo) {
        val added = repository.addPinnedApp(_allApps.value, app)
        _pinnedApps.value = repository.getPinnedApps(_allApps.value)
        _selectedDrawerAppForAction.value = null
        if (added) {
            Toast.makeText(getApplication(), "Added ${app.label} to bottom bar", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "${app.label} is already in the bottom bar", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Navigation & Music Customization ---

    fun openNavPicker() {
        _isNavPickerOpen.value = true
    }

    fun closeNavPicker() {
        _isNavPickerOpen.value = false
    }

    fun selectNavigationApp(app: AppInfo) {
        repository.setCustomNavPackage(app.packageName)
        _navigationApp.value = app
        _isNavPickerOpen.value = false
        Toast.makeText(getApplication(), "Navigation set to ${app.label}", Toast.LENGTH_SHORT).show()
    }

    fun openMusicPicker() {
        _isMusicPickerOpen.value = true
    }

    fun closeMusicPicker() {
        _isMusicPickerOpen.value = false
    }

    fun selectMusicApp(app: AppInfo) {
        repository.setCustomMusicPackage(app.packageName)
        _musicApp.value = app
        _isMusicPickerOpen.value = false
        Toast.makeText(getApplication(), "Music player set to ${app.label}", Toast.LENGTH_SHORT).show()
    }

    fun openDvrPicker() {
        _isDvrPickerOpen.value = true
    }

    fun closeDvrPicker() {
        _isDvrPickerOpen.value = false
    }

    fun selectDvrApp(app: AppInfo) {
        repository.setCustomDvrPackage(app.packageName)
        _dvrApp.value = app
        _isDvrPickerOpen.value = false
        Toast.makeText(getApplication(), "Dashcam set to ${app.label}", Toast.LENGTH_SHORT).show()
    }

    // --- About & In-App GitHub Updater ---

    fun openAboutDialog() {
        _isAboutDialogOpen.value = true
        if (_updateInfo.value == null && !_isCheckingUpdate.value) {
            checkForUpdates(isManualCheck = false)
        }
    }

    fun dismissAboutDialog() {
        if (_updateDownloadProgress.value == null) {
            _isAboutDialogOpen.value = false
        }
    }

    fun openDiagnosticsDialog() {
        _isDiagnosticsDialogOpen.value = true
    }

    fun dismissDiagnosticsDialog() {
        _isDiagnosticsDialogOpen.value = false
    }

    fun copyDiagnosticsReport() {
        DiagnosticsManager.copyReportToClipboard(
            getApplication(),
            radioStation.value,
            isRadioActive.value,
            savedRadioStations.value
        )
    }

    fun clearDiagnosticsLog() {
        DiagnosticsManager.clearEvents()
    }

    fun pingRadio() {
        radioManager.requestRadioInfoPing()
    }

    fun checkForUpdates(isManualCheck: Boolean = true) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val release = updateManager.checkLatestRelease()
            _isCheckingUpdate.value = false

            if (release != null) {
                _updateInfo.value = release
                if (isManualCheck) {
                    if (release.isUpdateAvailable) {
                        Toast.makeText(getApplication(), "Update available: ${release.tagName}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), "You're running the latest version", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (isManualCheck) {
                Toast.makeText(getApplication(), "No updates found on GitHub", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startDownloadAndInstall() {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            _updateDownloadProgress.value = 0
            val downloadedFile = updateManager.downloadApk(info.apkDownloadUrl) { progress ->
                _updateDownloadProgress.value = progress
            }

            if (downloadedFile != null && downloadedFile.exists()) {
                _updateDownloadProgress.value = null
                _isAboutDialogOpen.value = false
                updateManager.promptInstall(downloadedFile)
            } else {
                _updateDownloadProgress.value = null
                Toast.makeText(getApplication(), "Download failed. Please check internet connection.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Other Shortcuts ---

    fun launchDvr() {
        val dvr = _dvrApp.value
        if (dvr != null) {
            repository.launchApp(dvr)
        } else {
            val launched = repository.launchPackage("com.car.dvr") ||
                    repository.launchPackage("com.android.dvr") ||
                    repository.launchPackage("com.xyauto.dvr") ||
                    repository.launchPackage("com.syu.dvr") ||
                    repository.launchPackage("com.teyes.dvr") ||
                    repository.launchPackage("com.topway.dvr") ||
                    repository.launchPackage("com.hcn.dvr") ||
                    repository.launchPackage("com.camera.dvr")
            if (!launched) {
                openDvrPicker()
            }
        }
    }

    fun launchZLink() {
        val zlink = _zlinkApp.value
        if (zlink != null) {
            repository.launchApp(zlink)
        } else {
            val launched = repository.launchPackage("com.zjinnova.zlink") ||
                    repository.launchPackage("com.zjinnova.zlinkx") ||
                    repository.launchPackage("com.xyauto.zlink") ||
                    repository.launchPackage("com.carletter.zlink") ||
                    repository.launchPackage("com.suding.speedplay")
            if (!launched) {
                openAppDrawer()
            }
        }
    }

    fun launchNavigation() {
        val nav = _navigationApp.value
        if (nav != null) {
            repository.launchApp(nav)
        } else {
            if (!repository.launchPackage("com.google.android.apps.maps")) {
                openAppDrawer()
            }
        }
    }

    fun openAddressSearch() {
        _isAddressSearchOpen.value = true
    }

    fun closeAddressSearch() {
        _isAddressSearchOpen.value = false
    }

    fun startNavigationTo(destLat: Double, destLon: Double, destName: String = "Destination") {
        viewModelScope.launch {
            _isCalculatingRoute.value = true
            _navigationDestination.value = GeoPoint(destLat, destLon)
            _navigationDestinationName.value = destName
            _isAddressSearchOpen.value = false

            val currentLoc = speedometer.currentLocation.value
            val startLat = currentLoc?.latitude ?: 37.9838
            val startLon = currentLoc?.longitude ?: 23.7275

            val route = NavigationService.fetchRoute(startLat, startLon, destLat, destLon, destName)
            _activeRoute.value = route
            _isNavigating.value = (route != null)
            _isCalculatingRoute.value = false
        }
    }

    fun stopNavigation() {
        _isNavigating.value = false
        _activeRoute.value = null
        _navigationDestination.value = null
        _navigationDestinationName.value = ""
        _isCalculatingRoute.value = false
    }

    fun launchAddressSearch() {
        repository.launchAddressSearch()
    }

    fun launchNavigationToCoordinates(latitude: Double, longitude: Double) {
        repository.launchNavigationToCoordinates(latitude, longitude)
    }

    fun launchMusic() {
        val currentStation = radioStation.value
        val radioPkgs = listOf(
            "com.nwd.radio",
            "com.nwd.link.radio",
            "com.android.fmradio",
            "com.allwinner.radio",
            "com.qf.radio",
            "com.navimods.radio"
        )
        // If radio station is active, prioritize launching native headunit radio app
        if (!currentStation.isNullOrBlank()) {
            for (pkg in radioPkgs) {
                if (repository.launchPackage(pkg)) return
            }
        }

        val music = _musicApp.value
        if (music != null) {
            repository.launchApp(music)
        } else {
            for (pkg in radioPkgs) {
                if (repository.launchPackage(pkg)) return
            }
            if (!repository.launchPackage("com.spotify.music")) {
                openAppDrawer()
            }
        }
    }

    fun launchSettings() {
        repository.launchSettings()
    }

    fun toggleSpeedUnit() {
        speedometer.toggleUnit()
    }

    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        repository.setDarkMode(next)
    }

    fun toggleMapDarkMode() {
        toggleDarkMode()
    }

    fun refreshRadio() {
        radioManager.readCurrentSettingsFrequency()
        radioManager.requestRadioInfoPing()
    }

    fun tunePreviousStation() {
        radioManager.tunePreviousStation()
    }

    fun tuneNextStation() {
        radioManager.tuneNextStation()
    }

    fun tuneToSavedStation(station: String, index: Int) {
        radioManager.tuneToStation(station, index)
    }

    fun saveCurrentStationToPreset(index: Int) {
        val current = radioStation.value
        if (!current.isNullOrBlank()) {
            radioManager.savePreset(index, current)
        }
    }

    fun showRadioDiagnostic() {
        val info = radioManager.getRadioDiagnosticInfo()
        Toast.makeText(getApplication(), info, Toast.LENGTH_LONG).show()
    }

    override fun onCleared() {
        super.onCleared()
        speedometer.stopTracking()
        radioManager.stopMonitoring()
    }
}
