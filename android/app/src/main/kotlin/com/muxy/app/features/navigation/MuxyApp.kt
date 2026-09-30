package com.muxy.app.features.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.muxy.app.app.AppContainer
import com.muxy.app.design.MuxyTheme
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.design.ThemedWindow
import com.muxy.app.features.connections.ConnectionsListScreen
import com.muxy.app.features.onboarding.OnboardingScreen
import com.muxy.app.features.settings.SettingsModal

@Composable
fun MuxyApp(
    container: AppContainer,
    viewModel: RootViewModel,
    themedWindow: ThemedWindow,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: return
    val backStack =
        rememberSerializable(serializer = NavBackStackSerializer(AppRoute.serializer())) {
            NavBackStack<AppRoute>(AppRoute.Connections)
        }
    MuxyTheme(ThemeCatalog.named(loaded.themeName), themedWindow) {
        if (!loaded.hasCompletedOnboarding) {
            OnboardingScreen(
                onSkip = viewModel::completeOnboarding,
                onPairDesktop = viewModel::completeOnboarding,
            )
            return@MuxyTheme
        }
        AppNavigation(container, backStack)
    }
}

@Composable
private fun AppNavigation(
    container: AppContainer,
    backStack: NavBackStack<AppRoute>,
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        transitionSpec = { NavigationTransitions.push() },
        popTransitionSpec = { NavigationTransitions.pop() },
        predictivePopTransitionSpec = { NavigationTransitions.pop() },
        entryProvider =
            entryProvider {
                entry<AppRoute.Connections> {
                    ConnectionsListScreen(
                        onAddConnection = {},
                        onSettings = { backStack.open(AppRoute.Settings) },
                    )
                }
                entry<AppRoute.Settings>(metadata = NavigationTransitions.modal) {
                    SettingsModal(
                        viewModel = viewModel { container.makeSettingsViewModel() },
                        onClose = { backStack.close(AppRoute.Settings) },
                    )
                }
            },
    )
}
