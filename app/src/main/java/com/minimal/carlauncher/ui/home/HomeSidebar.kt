package com.minimal.carlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary

/**
 * CarPlay-style left rail: clock on top, utility buttons in the middle
 * (theme, about/updates, settings) and the All Apps button anchored at the bottom.
 */
@Composable
fun HomeSidebar(
    time: String,
    date: String,
    isDarkMode: Boolean,
    isUpdateAvailable: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onLongPressSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(104.dp)
            .fillMaxHeight()
            .background(CarSurface)
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = time,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1
        )
        Text(
            text = date,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SidebarButton(
                icon = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = if (isDarkMode) "Tema claro" else "Tema oscuro",
                onClick = onToggleTheme
            )
            SidebarButton(
                icon = Icons.Default.Info,
                contentDescription = "Acerca de y actualizaciones",
                onClick = onOpenAbout,
                showBadge = isUpdateAvailable
            )
            SidebarButton(
                icon = Icons.Default.Settings,
                contentDescription = "Ajustes (mantener pulsado: diagnóstico)",
                onClick = onOpenSettings,
                onLongClick = onLongPressSettings
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(8.dp))

        SidebarButton(
            icon = Icons.Default.Apps,
            contentDescription = "Todas las apps",
            onClick = onOpenDrawer,
            size = 68,
            iconSize = 34,
            background = AccentCyan,
            tint = Color.White,
            shapeCorner = 20
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showBadge: Boolean = false,
    size: Int = 52,
    iconSize: Int = 24,
    background: Color = CarSurfaceVariant,
    tint: Color = TextPrimary,
    shapeCorner: Int? = null
) {
    val shape = if (shapeCorner != null) RoundedCornerShape(shapeCorner.dp) else CircleShape
    Box {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .background(background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize.dp)
            )
        }
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(AccentCyan)
                    .border(2.dp, CarSurface, CircleShape)
            )
        }
    }
}
