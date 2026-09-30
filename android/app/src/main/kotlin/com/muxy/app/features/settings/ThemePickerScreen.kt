package com.muxy.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.design.ThemePalette
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.design.components.themedSection
import com.muxy.app.design.rgbColor

private val swatchWidth = 40.dp
private val swatchSeparatorInset = 16.dp + swatchWidth + 16.dp
private val checkSize = 24.dp

@Composable
fun ThemePickerScreen(
    selectedTheme: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = "Theme",
                navigationIcon = { TopBarAction(R.drawable.ic_arrow_back, "Back", onBack) },
            )
        },
    ) { padding ->
        ThemedList(contentPadding = padding, modifier = Modifier.selectableGroup()) {
            themedSection(items = ThemeCatalog.all, key = { it.name }, separatorInset = swatchSeparatorInset) { palette ->
                ThemeRow(
                    palette = palette,
                    isSelected = palette.name == selectedTheme,
                    onSelect = { onSelect(palette.name) },
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    palette: ThemePalette,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    ThemedListItem(
        headline = { Text(palette.name) },
        modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect),
        supporting = { AnsiStrip(palette.ansi) },
        leading = { Swatch(palette) },
        trailing = {
            Box(modifier = Modifier.size(checkSize)) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = LocalAppTheme.current.accent,
                    )
                }
            }
        },
    )
}

@Composable
private fun Swatch(palette: ThemePalette) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            Modifier
                .size(width = swatchWidth, height = 32.dp)
                .clip(shape)
                .background(rgbColor(palette.background))
                .border(1.dp, LocalAppTheme.current.separator, shape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Ab",
            color = rgbColor(palette.foreground),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AnsiStrip(colors: List<Int>) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        colors.forEach { color ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(rgbColor(color)),
            )
        }
    }
}
