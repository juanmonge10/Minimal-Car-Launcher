package com.minimal.carlauncher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.data.AppInfo
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.util.BitmapHelper

/**
 * Renders an installed app's icon at the requested size.
 * The bitmap is rasterized once per icon/size and cached across recompositions,
 * which keeps scrolling smooth on low-RAM head units.
 */
@Composable
fun AppIconImage(
    app: AppInfo,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val imageBitmap = remember(app.icon, sizePx) {
        BitmapHelper.safeDrawableToImageBitmap(app.icon, sizePx, sizePx)
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = app.label,
            modifier = modifier.size(size)
        )
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Text(
                text = app.label.take(1).uppercase(),
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
        }
    }
}
