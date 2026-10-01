package com.muxy.app.features.projects

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.rgbColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val projectIconSize = 32.dp
private val nestedIndent = 24.dp
private val emojiSize = 22.sp

@Composable
fun ProjectRow(
    item: ProjectListItem,
    onClick: () -> Unit,
) {
    ThemedListItem(
        headline = { Text(item.name, fontWeight = FontWeight.SemiBold) },
        supporting = { Text(item.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        leading = { ProjectIcon(item, Modifier.padding(start = if (item.isNested) nestedIndent else 0.dp)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ProjectIcon(
    item: ProjectListItem,
    modifier: Modifier,
) {
    val logo = item.logo?.let { rememberLogo(it) }
    Box(modifier = modifier.size(projectIconSize), contentAlignment = Alignment.Center) {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = null,
                modifier = Modifier.size(projectIconSize).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
            )
            return@Box
        }
        when (val icon = item.icon) {
            is ProjectIcon.Symbol -> SymbolIcon(icon.name, item.iconColor)
            is ProjectIcon.Emoji -> Text(icon.text, fontSize = emojiSize)
        }
    }
}

@Composable
private fun SymbolIcon(
    name: String?,
    iconColor: String?,
) {
    val theme = LocalAppTheme.current
    Icon(
        painter = painterResource(ProjectSymbols.drawable(name)),
        contentDescription = null,
        modifier = Modifier.size(26.dp),
        tint = ProjectIconColor.rgb(iconColor, theme.isDark)?.let(::rgbColor) ?: theme.foreground,
    )
}

@Composable
private fun rememberLogo(logo: ProjectLogo): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, logo) {
        value = withContext(Dispatchers.Default) { ProjectLogoDecoder.decode(logo.png) }
    }
    return bitmap
}

object ProjectLogoDecoder {
    private const val MAX_DIMENSION = 256

    fun decode(png: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight)) }
        return BitmapFactory.decodeByteArray(png, 0, png.size, options)?.asImageBitmap()
    }

    private fun sampleSize(largestDimension: Int): Int {
        var sample = 1
        while (largestDimension / (sample * 2) >= MAX_DIMENSION) sample *= 2
        return sample
    }
}
