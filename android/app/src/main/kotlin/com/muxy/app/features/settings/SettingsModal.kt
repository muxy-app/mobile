package com.muxy.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.features.navigation.NavigationTransitions
import com.muxy.app.features.navigation.close
import com.muxy.app.features.navigation.open

@Composable
fun SettingsModal(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedTheme = ThemeCatalog.named(settings.themeName).name
    val backStack =
        rememberSerializable(serializer = NavBackStackSerializer(SettingsRoute.serializer())) {
            NavBackStack<SettingsRoute>(SettingsRoute.Main)
        }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = { NavigationTransitions.push() },
        popTransitionSpec = { NavigationTransitions.pop() },
        predictivePopTransitionSpec = { NavigationTransitions.pop() },
        entryProvider =
            entryProvider {
                entry<SettingsRoute.Main> {
                    SettingsScreen(
                        themeName = selectedTheme,
                        demoMode = settings.demoMode,
                        onTheme = { backStack.open(SettingsRoute.Theme) },
                        onDemoModeChange = viewModel::setDemoMode,
                        onClose = onClose,
                    )
                }
                entry<SettingsRoute.Theme> {
                    ThemePickerScreen(
                        selectedTheme = selectedTheme,
                        onSelect = viewModel::selectTheme,
                        onBack = { backStack.close(SettingsRoute.Theme) },
                    )
                }
            },
    )
}
