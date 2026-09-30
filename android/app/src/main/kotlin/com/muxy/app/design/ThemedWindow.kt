package com.muxy.app.design

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb

class ThemedWindow(
    private val activity: ComponentActivity,
) {
    private var isDark = true
    private val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }

    fun apply(theme: AppTheme) {
        isDark = theme.isDark
        activity.enableEdgeToEdge(barStyle, barStyle)
        activity.window.decorView.setBackgroundColor(theme.groupedBackground.toArgb())
    }
}
