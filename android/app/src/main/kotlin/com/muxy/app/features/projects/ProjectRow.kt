package com.muxy.app.features.projects

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muxy.app.design.components.ListRow
import com.muxy.app.design.components.ThemedChevron
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.models.DisplayPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val markSize = 28.dp

@Composable
fun ProjectRow(
    item: ProjectListItem,
    onClick: () -> Unit,
) {
    ThemedListItem(
        headline = { Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supporting = { Text(DisplayPath.abbreviated(item.path), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        leading = { ProjectMark(item, Modifier.padding(start = if (item.isNested) markSize else 0.dp)) },
        trailing = { ThemedChevron() },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun ProjectMark(
    item: ProjectListItem,
    modifier: Modifier,
) {
    val logo = item.logo?.let { rememberLogo(it) }
    Box(modifier = modifier.size(markSize), contentAlignment = Alignment.Center) {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = null,
                modifier = Modifier.size(markSize).clip(RoundedCornerShape(ListRow.nestedCorner)),
                contentScale = ContentScale.Fit,
            )
            return@Box
        }
        Box(modifier = Modifier.size(8.dp).background(rememberProjectColor(item.id, item.iconColor), CircleShape))
    }
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
