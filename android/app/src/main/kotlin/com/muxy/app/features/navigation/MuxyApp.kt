package com.muxy.app.features.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.muxy.app.app.AppContainer
import com.muxy.app.core.logging.Log
import com.muxy.app.design.MuxyTheme
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.design.ThemedWindow
import com.muxy.app.features.addconnection.AddConnectionRequest
import com.muxy.app.features.addconnection.AddConnectionScreen
import com.muxy.app.features.connections.ConnectionsListScreen
import com.muxy.app.features.files.FilesModal
import com.muxy.app.features.git.GitModal
import com.muxy.app.features.onboarding.OnboardingScreen
import com.muxy.app.features.projectdetail.ProjectDetailScreen
import com.muxy.app.features.projectdetail.ProjectTool
import com.muxy.app.features.projectdetail.ToolProject
import com.muxy.app.features.projects.ProjectsScreen
import com.muxy.app.features.server.ServerProjectScreen
import com.muxy.app.features.server.ServerProjectsScreen
import com.muxy.app.features.settings.SettingsModal
import com.muxy.app.features.sshterminal.SshTerminalScreen
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.networking.muxy1.ConnectionFocus

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
                onPairDesktop = {
                    viewModel.completeOnboarding()
                    backStack.open(AppRoute.AddConnection)
                },
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
    ConnectionFocusBridge(container, backStack)
    LaunchedEffect(backStack) {
        pendingPairingRequests(container.addConnectionRequests.request, snapshotFlow { backStack.toList() }).collect {
            if (backStack.any { it is AppRoute.ProjectTools }) return@collect
            backStack.removeAll { it == AppRoute.Settings }
            backStack.open(AppRoute.AddConnection)
        }
    }
    val openConnection = { connection: Connection -> backStack.openConnection(connection) }
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
                        viewModel = viewModel { container.makeConnectionsListViewModel() },
                        onSelect = openConnection,
                        onAddConnection = { backStack.open(AppRoute.AddConnection) },
                        onSettings = { backStack.open(AppRoute.Settings) },
                    )
                }
                entry<AppRoute.Settings>(metadata = NavigationTransitions.modal) {
                    SettingsModal(
                        viewModel = viewModel { container.makeSettingsViewModel() },
                        onClose = { backStack.close(AppRoute.Settings) },
                    )
                }
                entry<AppRoute.AddConnection>(metadata = NavigationTransitions.modal) {
                    AddConnectionScreen(
                        viewModel = viewModel { container.makeAddConnectionViewModel() },
                        onCancel = { backStack.close(AppRoute.AddConnection) },
                        onAdded = { connection ->
                            connection.serverId?.let(container.serverDirectory::credentialDidChange)
                            backStack.close(AppRoute.AddConnection)
                            openConnection(connection)
                        },
                    )
                }
                entry<AppRoute.SshTerminal> { route ->
                    SshTerminalScreen(
                        viewModel = viewModel { container.makeSshTerminalViewModel(route.connectionId) },
                        onBack = { backStack.close(route) },
                    )
                }
                entry<AppRoute.Projects> { route ->
                    ProjectsScreen(
                        viewModel = viewModel { container.makeProjectsViewModel(route.connectionId) },
                        onSelect = { project -> backStack.open(AppRoute.ProjectDetail(route.connectionId, project.id, project.name)) },
                        onPairAgain = { container.addConnectionRequests.deliver(AddConnectionRequest.Repair(route.connectionId)) },
                        onBack = { backStack.close(route) },
                    )
                }
                entry<AppRoute.ProjectDetail> { route ->
                    ProjectDetailScreen(
                        viewModel = viewModel { container.makeProjectDetailViewModel(route) },
                        onBack = { backStack.close(route) },
                        onTool = { tool ->
                            backStack.open(
                                AppRoute.ProjectTools(ToolProject.Device(route.connectionId, route.projectId, route.projectName), tool),
                            )
                        },
                    )
                }
                entry<AppRoute.ServerProjects> { route ->
                    ServerProjectsScreen(
                        viewModel = viewModel { container.makeServerProjectsViewModel(route) },
                        onSelect = { projectId -> backStack.open(AppRoute.ServerProject(route.connectionId, route.serverId, projectId)) },
                        onBack = { backStack.close(route) },
                    )
                }
                entry<AppRoute.ServerProject> { route ->
                    ServerProjectScreen(
                        viewModel = viewModel { container.makeServerProjectViewModel(route) },
                        onBack = { backStack.close(route) },
                        onTool = { tool ->
                            backStack.open(
                                AppRoute.ProjectTools(ToolProject.Server(route.connectionId, route.serverId, route.projectId), tool),
                            )
                        },
                    )
                }
                entry<AppRoute.ProjectTools>(metadata = NavigationTransitions.modal) { route ->
                    if (route.tool == ProjectTool.FILES) {
                        FilesModal(
                            viewModel = viewModel { container.projectTools.files(route.project) },
                            onClose = { backStack.close(route) },
                        )
                    } else {
                        GitModal(
                            git = viewModel { container.projectTools.git(route.project) },
                            worktrees = viewModel { container.projectTools.worktrees(route.project) },
                            startsWithWorktrees = route.tool == ProjectTool.WORKTREES,
                            onClose = { backStack.close(route) },
                            onOpenProject = { projectId ->
                                val project = route.project
                                if (project is ToolProject.Server) {
                                    backStack.close(route)
                                    backStack.open(AppRoute.ServerProject(project.connectionId, project.serverId, projectId))
                                }
                            },
                        )
                    }
                }
            },
    )
}

@Composable
private fun ConnectionFocusBridge(
    container: AppContainer,
    backStack: NavBackStack<AppRoute>,
) {
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList().connectionFocus() }.collect(container.connectionLifecycle::focus)
    }
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.toList().serverFocus() }.collect(container.serverDirectory::focus)
    }
    DisposableEffect(container) {
        onDispose { container.connectionLifecycle.focus(ConnectionFocus.Hold) }
    }
}

private fun NavBackStack<AppRoute>.openConnection(connection: Connection) {
    when (connection.kind) {
        ConnectionKind.DEVICE -> {
            open(AppRoute.Projects(connection.id))
        }

        ConnectionKind.SERVER -> {
            val serverId = connection.serverId
            if (serverId == null) {
                Log.connection.error("A Muxy 2 connection has no server id")
                return
            }
            open(AppRoute.ServerProjects(connection.id, serverId))
        }

        ConnectionKind.SSH -> {
            open(AppRoute.SshTerminal(connection.id))
        }
    }
}
