package com.minimal.carlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimal.carlauncher.data.AppRepository
import com.minimal.carlauncher.ui.theme.CarBg
import com.minimal.carlauncher.ui.theme.TileBlue
import com.minimal.carlauncher.ui.theme.TileGraphite
import com.minimal.carlauncher.ui.theme.TileGreen
import com.minimal.carlauncher.ui.theme.TilePink
import com.minimal.carlauncher.ui.viewmodel.LauncherViewModel

/**
 * Minimal CarPlay-style home screen:
 *  - left rail: clock, theme toggle, about/updates, settings, All Apps button
 *  - main area: icon grid (CarPlay/ZLINK, Maps, Music, Dashcam + favorites) and a compact radio bar.
 * All behavior is delegated to [LauncherViewModel]; dialogs live in [HomeOverlays].
 */
@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val time by viewModel.currentTime.collectAsState()
    val date by viewModel.currentDate.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()

    val pinnedApps by viewModel.pinnedApps.collectAsState()
    val navigationApp by viewModel.navigationApp.collectAsState()
    val musicApp by viewModel.musicApp.collectAsState()
    val dvrApp by viewModel.dvrApp.collectAsState()

    val radioStation by viewModel.radioStation.collectAsState()
    val isRadioActive by viewModel.isRadioActive.collectAsState()
    val savedRadioStations by viewModel.savedRadioStations.collectAsState()

    val tiles = buildList<HomeTile> {
        add(
            HomeTile.Shortcut(
                key = "carplay",
                label = "CarPlay",
                icon = Icons.Default.PhoneIphone,
                color = TileGreen,
                onClick = { viewModel.launchZLink() }
            )
        )
        add(
            HomeTile.Shortcut(
                key = "maps",
                label = navigationApp?.label ?: "Mapas",
                icon = Icons.Default.Navigation,
                color = TileBlue,
                onClick = { viewModel.launchNavigation() },
                onLongClick = { viewModel.openNavPicker() }
            )
        )
        add(
            HomeTile.Shortcut(
                key = "music",
                label = musicApp?.label ?: "Música",
                icon = Icons.Default.MusicNote,
                color = TilePink,
                onClick = { viewModel.launchMusic() },
                onLongClick = { viewModel.openMusicPicker() }
            )
        )
        add(
            HomeTile.Shortcut(
                key = "dashcam",
                label = dvrApp?.label ?: "Dashcam",
                icon = Icons.Default.Videocam,
                color = TileGraphite,
                onClick = { viewModel.launchDvr() },
                onLongClick = { viewModel.openDvrPicker() }
            )
        )
        pinnedApps.forEach { add(HomeTile.Favorite(it)) }
        if (pinnedApps.size < AppRepository.MAX_DOCK_APPS) add(HomeTile.AddFavorite)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarBg)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            HomeSidebar(
                time = time,
                date = date,
                isDarkMode = isDarkMode,
                isUpdateAvailable = updateInfo?.isUpdateAvailable == true,
                onOpenDrawer = { viewModel.openAppDrawer() },
                onToggleTheme = { viewModel.toggleDarkMode() },
                onOpenAbout = { viewModel.openAboutDialog() },
                onOpenSettings = { viewModel.launchSettings() },
                onLongPressSettings = { viewModel.openDiagnosticsDialog() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HomeAppGrid(
                    tiles = tiles,
                    onFavoriteClick = { app -> viewModel.launchApp(app) },
                    onFavoriteLongClick = { app -> viewModel.onDockAppLongClick(app) },
                    onAddFavorite = { viewModel.openAddDockPicker() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                RadioStrip(
                    radioStation = radioStation,
                    isRadioActive = isRadioActive,
                    presets = savedRadioStations,
                    onOpenRadio = { viewModel.onRadioBarClick() },
                    onLongPress = { viewModel.openRadioScreen() },
                    onTunePrevious = { viewModel.tunePreviousStation() },
                    onTuneNext = { viewModel.tuneNextStation() },
                    onSelectPreset = { station, index -> viewModel.tuneToSavedStation(station, index) },
                    onSavePreset = { index -> viewModel.saveCurrentStationToPreset(index) }
                )
            }
        }

        HomeOverlays(viewModel)
    }
}
