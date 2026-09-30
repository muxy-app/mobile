package com.muxy.app.app

import android.content.Context
import android.net.nsd.NsdManager
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.Lifecycle
import com.muxy.app.core.device.SystemPhoneName
import com.muxy.app.core.security.TokenGenerator
import com.muxy.app.core.validation.ConnectionInputValidator
import com.muxy.app.features.addconnection.AddConnectionViewModel
import com.muxy.app.features.addconnection.PairingCodeInbox
import com.muxy.app.features.connections.ConnectionsListViewModel
import com.muxy.app.features.demo.syncDemoMode
import com.muxy.app.features.navigation.AppRoute
import com.muxy.app.features.navigation.RootViewModel
import com.muxy.app.features.projectdetail.ProjectDetailViewModel
import com.muxy.app.features.projects.ProjectsViewModel
import com.muxy.app.features.settings.SettingsViewModel
import com.muxy.app.networking.muxy1.ConnectionLifecycle
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.discovery.NsdDiscovery
import com.muxy.app.networking.muxy1.transport.WebSocketTransport
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.connections.DataStoreConnectionStore
import com.muxy.app.persistence.preferencesDataStore
import com.muxy.app.persistence.secrets.EncryptedSecretStore
import com.muxy.app.persistence.secrets.KeystoreSecretCipher
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.persistence.settings.DataStoreSettingsStore
import com.muxy.app.persistence.settings.SettingsStore
import com.muxy.app.persistence.workspaces.DataStoreWorkspaceSelectionStore
import com.muxy.app.persistence.workspaces.WorkspaceSelectionStore
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

    val tokenStore: TokenStore =
        SecretTokenStore(
            EncryptedSecretStore(
                dataStore = preferencesDataStore("Secrets", ioScope) { File(context.noBackupFilesDir, SECRETS_FILE) },
                cipher = KeystoreSecretCipher(),
            ),
        )

    private val workspaceSelectionStore: WorkspaceSelectionStore =
        DataStoreWorkspaceSelectionStore(
            preferencesDataStore("Workspace selections", ioScope) {
                context.preferencesDataStoreFile(WORKSPACES_FILE)
            },
        )

    val pairingCodes = PairingCodeInbox()

    val connectionManager =
        ConnectionManager(
            makeTransport = { url -> WebSocketTransport(url, httpClient) },
            tokenStore = tokenStore,
            phoneName = SystemPhoneName(context),
        )

    val connectionLifecycle = ConnectionLifecycle(connectionManager, connectionStore, mainScope)

    fun start(lifecycle: Lifecycle) {
        lifecycle.addObserver(connectionLifecycle)
        ioScope.syncDemoMode(settingsStore.settings, connectionStore, tokenStore)
    }

    fun makeRootViewModel(): RootViewModel = RootViewModel(settingsStore, pairingCodes)

    fun makeSettingsViewModel(): SettingsViewModel = SettingsViewModel(settingsStore)

    fun makeConnectionsListViewModel(): ConnectionsListViewModel = ConnectionsListViewModel(connectionStore, tokenStore)

    fun makeAddConnectionViewModel(): AddConnectionViewModel =
        AddConnectionViewModel(
            store = connectionStore,
            tokens = tokenStore,
            manager = connectionManager,
            validator = validator,
            tokenGenerator = tokenGenerator,
            discovery = NsdDiscovery(context.getSystemService(NsdManager::class.java)),
            inbox = pairingCodes,
        )

    fun makeProjectsViewModel(connectionId: UUID): ProjectsViewModel =
        ProjectsViewModel(connectionId, connectionStore, connectionManager, workspaceSelectionStore)

    fun makeProjectDetailViewModel(route: AppRoute.ProjectDetail): ProjectDetailViewModel =
        ProjectDetailViewModel(route.connectionId, route.projectId, route.projectName, connectionStore, connectionManager)

    private companion object {
        const val SETTINGS_FILE = "settings"
        const val CONNECTIONS_FILE = "connections"
        const val WORKSPACES_FILE = "workspace_selections"
        const val SECRETS_FILE = "secrets.preferences_pb"
        const val HANDSHAKE_TIMEOUT_SECONDS = 8L
    }
}
