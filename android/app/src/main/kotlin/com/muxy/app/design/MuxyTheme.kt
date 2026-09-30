package com.muxy.app.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private const val THEME_CROSSFADE_MILLIS = 250

@Composable
fun MuxyTheme(
    palette: ThemePalette,
    themedWindow: ThemedWindow,
    content: @Composable () -> Unit,
) {
    val target = remember(palette) { AppTheme.from(palette) }
    LaunchedEffect(target) { themedWindow.apply(target) }
    val theme = target.animated()
    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(colorScheme = theme.colorScheme(), content = content)
    }
}

@Composable
private fun AppTheme.animated(): AppTheme =
    copy(
        background = animated(background),
        secondaryBackground = animated(secondaryBackground),
        groupedBackground = animated(groupedBackground),
        secondaryGroupedBackground = animated(secondaryGroupedBackground),
        separator = animated(separator),
        foreground = animated(foreground),
        secondaryForeground = animated(secondaryForeground),
        accent = animated(accent),
        onAccent = animated(onAccent),
        red = animated(red),
        green = animated(green),
        yellow = animated(yellow),
        cyan = animated(cyan),
    )

@Composable
private fun animated(color: Color): Color = animateColorAsState(color, tween(THEME_CROSSFADE_MILLIS), label = "theme").value

private fun AppTheme.colorScheme(): ColorScheme =
    ColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = secondaryGroupedBackground,
        onSecondaryContainer = foreground,
        tertiary = cyan,
        onTertiary = groupedBackground,
        tertiaryContainer = secondaryGroupedBackground,
        onTertiaryContainer = foreground,
        background = groupedBackground,
        onBackground = foreground,
        surface = groupedBackground,
        onSurface = foreground,
        surfaceVariant = secondaryGroupedBackground,
        onSurfaceVariant = secondaryForeground,
        surfaceTint = groupedBackground,
        inverseSurface = foreground,
        inverseOnSurface = groupedBackground,
        error = red,
        onError = groupedBackground,
        errorContainer = secondaryGroupedBackground,
        onErrorContainer = red,
        outline = separator,
        outlineVariant = separator,
        scrim = Color.Black,
        surfaceBright = secondaryGroupedBackground,
        surfaceDim = groupedBackground,
        surfaceContainer = secondaryGroupedBackground,
        surfaceContainerHigh = secondaryGroupedBackground,
        surfaceContainerHighest = secondaryGroupedBackground,
        surfaceContainerLow = secondaryGroupedBackground,
        surfaceContainerLowest = secondaryGroupedBackground,
        primaryFixed = accent,
        primaryFixedDim = accent,
        onPrimaryFixed = onAccent,
        onPrimaryFixedVariant = onAccent,
        secondaryFixed = accent,
        secondaryFixedDim = accent,
        onSecondaryFixed = onAccent,
        onSecondaryFixedVariant = onAccent,
        tertiaryFixed = cyan,
        tertiaryFixedDim = cyan,
        onTertiaryFixed = groupedBackground,
        onTertiaryFixedVariant = groupedBackground,
    )
