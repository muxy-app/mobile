package com.muxy.app.app

import android.content.Context
import android.net.nsd.NsdManager
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.Lifecycle
import com.muxy.app.core.device.SystemPhoneName
import com.muxy.app.core.security.TokenGenerator
import com.muxy.app.core.validation.ConnectionInputValidator
import com.muxy.app.features.addconnection.AddConnectionInbox
import com.muxy.app.features.addconnection.AddConnectionViewModel
import com.muxy.app.features.addconnection.ServerPairingModel
import com.muxy.app.features.addconnection.SshConnectionAdder
import com.muxy.app.features.connections.ConnectionsListViewModel
import com.muxy.app.features.demo.syncDemoMode
import com.muxy.app.features.navigation.AppRoute
import com.muxy.app.features.navigation.RootViewModel
import com.muxy.app.features.projectdetail.ProjectDetailViewModel
import com.muxy.app.features.projectdetail.ProjectToolsFactory
import com.muxy.app.features.projects.ProjectsViewModel
import com.muxy.app.features.server.ServerDirectory
import com.muxy.app.features.server.ServerProjectViewModel
import com.muxy.app.features.server.ServerProjectsViewModel
import com.muxy.app.features.settings.SettingsViewModel
import com.muxy.app.features.sshterminal.SshTerminalViewModel
import com.muxy.app.features.terminal.SystemTerminalClipboard
import com.muxy.app.networking.muxy1.ConnectionLifecycle
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.discovery.NsdDiscovery
import com.muxy.app.networking.muxy1.transport.WebSocketTransport
import com.muxy.app.networking.server.sdk.SdkPairingService
import com.muxy.app.networking.server.sdk.SdkServerConnector
import com.muxy.app.networking.ssh.SshClientFactory
import com.muxy.app.networking.ssh.SshConnectionTester
import com.muxy.app.networking.ssh.SshHostKeyTrust
import com.muxy.app.networking.ssh.SshjClient
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.connections.DataStoreConnectionStore
import com.muxy.app.persistence.credentials.CredentialStore
import com.muxy.app.persistence.credentials.SecretCredentialStore
import com.muxy.app.persistence.preferencesDataStore
import com.muxy.app.persistence.secrets.EncryptedSecretStore
import com.muxy.app.persistence.secrets.KeystoreSecretCipher
import com.muxy.app.persistence.secrets.SecretStore
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.persistence.settings.DataStoreSettingsStore
import com.muxy.app.persistence.settings.SettingsStore
import com.muxy.app.persistence.workspaces.DataStoreWorkspaceSelectionStore
import com.muxy.app.persistence.workspaces.WorkspaceSelectionStore
import com.muxy.app.persistence.worktrees.DataStoreWorktreeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class AppContainer(
    private val context: Context,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val validator = ConnectionInputValidator()
    private val tokenGenerator = TokenGenerator()
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    val settingsStore: SettingsStore =
        DataStoreSettingsStore(
            dataStore = preferencesDataStore("Settings", ioScope) { context.preferencesDataStoreFile(SETTINGS_FILE) },
            scope = ioScope,
        )

    val connectionStore: ConnectionStore =
        DataStoreConnectionStore(
            dataStore = preferencesDataStore("Connections", ioScope) { context.preferencesDataStoreFile(CONNECTIONS_FILE) },
            scope = ioScope,
        )

    private val secretStore: SecretStore =
        EncryptedSecretStore(
            dataStore = preferencesDataStore("Secrets", ioScope) { File(context.noBackupFilesDir, SECRETS_FILE) },
            cipher = KeystoreSecretCipher(),
        )

    val tokenStore: TokenStore = SecretTokenStore(secretStore)

    private val sshClients = SshClientFactory { SshjClient() }
    private val sshTrust = SshHostKeyTrust(secretStore)
    private val sshTester = SshConnectionTester(sshClients, secretStore, sshTrust)

    private val credentialStore: CredentialStore = SecretCredentialStore(secretStore)

    private val workspaceSelectionStore: WorkspaceSelectionStore =
        DataStoreWorkspaceSelectionStore(
            preferencesDataStore("Workspace selections", ioScope) {
                context.preferencesDataStoreFile(WORKSPACES_FILE)
            },
        )

    val addConnectionRequests = AddConnectionInbox()

    private val terminalClipboard = SystemTerminalClipboard(context)

    private val phoneName = SystemPhoneName(context)

    val connectionManager =
        ConnectionManager(
            makeTransport = { url -> WebSocketTransport(url, httpClient) },
            tokenStore = tokenStore,
            phoneName = phoneName,
        )

    val connectionLifecycle = ConnectionLifecycle(connectionManager, connectionStore, mainScope)

    val serverDirectory = ServerDirectory(credentialStore, SdkServerConnector(), mainScope)

    val projectTools =
        ProjectToolsFactory(
            connectionManager,
            serverDirectory,
            DataStoreWorktreeCache(preferencesDataStore("Worktrees", ioScope) { context.preferencesDataStoreFile(WORKTREES_FILE) }),
        )

    fun start(lifecycle: Lifecycle) {
        lifecycle.addObserver(connectionLifecycle)
        lifecycle.addObserver(serverDirectory)
        ioScope.syncDemoMode(settingsStore.settings, connectionStore, tokenStore)
    }

    fun makeRootViewModel(): RootViewModel = RootViewModel(settingsStore, addConnectionRequests)

    fun makeSettingsViewModel(): SettingsViewModel = SettingsViewModel(settingsStore)

    fun makeConnectionsListViewModel(): ConnectionsListViewModel =
        ConnectionsListViewModel(connectionStore, tokenStore, credentialStore, serverDirectory)

    fun makeAddConnectionViewModel(): AddConnectionViewModel =
        AddConnectionViewModel(
            store = connectionStore,
            tokens = tokenStore,
            manager = connectionManager,
            validator = validator,
            tokenGenerator = tokenGenerator,
            discovery = NsdDiscovery(context.getSystemService(NsdManager::class.java)),
            inbox = addConnectionRequests,
            sshAdding = SshConnectionAdder(connectionStore, secretStore, sshTester),
            serverPairing =
                ServerPairingModel(
                    pairing = SdkPairingService(),
                    credentials = credentialStore,
                    store = connectionStore,
                    deviceName = phoneName.current(),
                ),
        )

    fun makeSshTerminalViewModel(connectionId: UUID): SshTerminalViewModel =
        SshTerminalViewModel(
            connectionId,
            connectionStore,
            settingsStore,
            secretStore,
            sshTrust,
            sshClients,
            ioScope,
            terminalClipboard,
        )

    fun makeProjectsViewModel(connectionId: UUID): ProjectsViewModel =
        ProjectsViewModel(connectionId, connectionStore, connectionManager, workspaceSelectionStore)

    fun makeProjectDetailViewModel(route: AppRoute.ProjectDetail): ProjectDetailViewModel =
        ProjectDetailViewModel(
            connectionId = route.connectionId,
            projectId = route.projectId,
            projectName = route.projectName,
            connectionStore = connectionStore,
            manager = connectionManager,
            settingsStore = settingsStore,
            outbound = mainScope,
            clipboard = terminalClipboard,
        )

    fun makeServerProjectsViewModel(route: AppRoute.ServerProjects): ServerProjectsViewModel =
        ServerProjectsViewModel(route.connectionId, serverDirectory.controller(route.serverId), connectionStore)

    fun makeServerProjectViewModel(route: AppRoute.ServerProject): ServerProjectViewModel =
        ServerProjectViewModel(
            connectionId = route.connectionId,
            projectId = route.projectId,
            server = serverDirectory.controller(route.serverId),
            connectionStore = connectionStore,
            settingsStore = settingsStore,
        )

    private companion object {
        const val SETTINGS_FILE = "settings"
        const val CONNECTIONS_FILE = "connections"
        const val WORKSPACES_FILE = "workspace_selections"
        const val WORKTREES_FILE = "worktrees"
        const val SECRETS_FILE = "secrets.preferences_pb"
        const val HANDSHAKE_TIMEOUT_SECONDS = 8L
    }
}
