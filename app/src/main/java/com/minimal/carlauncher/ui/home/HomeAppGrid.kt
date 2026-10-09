package com.minimal.carlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
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
import com.minimal.carlauncher.data.AppInfo
import com.minimal.carlauncher.ui.common.AppIconImage
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.TextPrimary
import kotlin.math.ceil

/** One cell of the home grid. */
sealed interface HomeTile {
    val key: String

    /** Built-in shortcut (CarPlay, Maps, Music, Dashcam) drawn as a colored glyph tile. */
    data class Shortcut(
        override val key: String,
        val label: String,
        val icon: ImageVector,
        val color: Color,
        val onClick: () -> Unit,
        val onLongClick: (() -> Unit)? = null
    ) : HomeTile

    /** A favorite app pinned by the user. */
    data class Favorite(val app: AppInfo) : HomeTile {
        override val key: String get() = "fav:" + app.packageName
    }

    /** "Add favorite" placeholder, shown while there is room for more favorites. */
    data object AddFavorite : HomeTile {
        override val key: String get() = "add"
    }
}

private val MinCellWidth = 128.dp
private val LabelBlockHeight = 34.dp
private val RowGap = 12.dp

/**
 * Responsive CarPlay-like icon grid. Column count follows the available width and the icon
 * size follows the available height, so the same layout works on 1024×600 and other panels.
 * Falls back to vertical scrolling if the tiles cannot fit.
 */
@Composable
fun HomeAppGrid(
    tiles: List<HomeTile>,
    onFavoriteClick: (AppInfo) -> Unit,
    onFavoriteLongClick: (AppInfo) -> Unit,
    onAddFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = (maxWidth / MinCellWidth).toInt().coerceIn(3, 8)
        val rows = ceil(tiles.size / columns.toFloat()).toInt().coerceAtLeast(1)
        val cellWidth = maxWidth / columns
        val cellHeight = (maxHeight - RowGap * (rows - 1)) / rows
        val iconSize = minOf(cellHeight - LabelBlockHeight, cellWidth - 28.dp, 92.dp)
            .coerceAtLeast(56.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.spacedBy(RowGap, Alignment.CenterVertically)
        ) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowTiles.forEach { tile ->
                        Box(modifier = Modifier.width(cellWidth), contentAlignment = Alignment.TopCenter) {
                            when (tile) {
                                is HomeTile.Shortcut -> ShortcutTile(tile, iconSize)
                                is HomeTile.Favorite -> FavoriteTile(
                                    app = tile.app,
                                    iconSize = iconSize,
                                    onClick = { onFavoriteClick(tile.app) },
                                    onLongClick = { onFavoriteLongClick(tile.app) }
                                )
                                HomeTile.AddFavorite -> AddTile(iconSize, onAddFavorite)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileFrame(
    label: String,
    iconSize: Dp,
    iconBackground: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    iconContent: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * 0.24f))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ShortcutTile(tile: HomeTile.Shortcut, iconSize: Dp) {
    TileFrame(
        label = tile.label,
        iconSize = iconSize,
        iconBackground = tile.color,
        onClick = tile.onClick,
        onLongClick = tile.onLongClick
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = tile.label,
            tint = Color.White,
            modifier = Modifier.size(iconSize * 0.5f)
        )
    }
}

@Composable
private fun FavoriteTile(
    app: AppInfo,
    iconSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    TileFrame(
        label = app.label,
        iconSize = iconSize,
        iconBackground = CarSurface,
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        AppIconImage(app = app, size = iconSize * 0.78f)
    }
}

@Composable
private fun AddTile(iconSize: Dp, onClick: () -> Unit) {
    TileFrame(
        label = "Añadir",
        iconSize = iconSize,
        iconBackground = CarSurface,
        onClick = onClick,
        onLongClick = null
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Añadir favorito",
            tint = AccentCyan,
            modifier = Modifier.size(iconSize * 0.42f)
        )
    }
}
