package com.muxy.app.features.sshterminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muxy.app.R
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.design.components.TabStripItem
import com.muxy.app.features.terminal.TerminalClipboard
import com.muxy.app.features.terminal.TerminalSettings
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.networking.ssh.SshClientFactory
import com.muxy.app.networking.ssh.SshError
import com.muxy.app.networking.ssh.SshHostKeyTrust
import com.muxy.app.persistence.connections.ConnectionStore
import com.muxy.app.persistence.secrets.SecretStore
import com.muxy.app.persistence.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SshTerminalUiState(
    val name: String = "",
    val tabs: List<TabStripItem> = emptyList(),
    val selectedId: UUID? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class SshTerminalViewModel(
    connectionId: UUID,
    store: ConnectionStore,
    settings: SettingsStore,
    private val secrets: SecretStore,
    private val trust: SshHostKeyTrust,
    private val clients: SshClientFactory,
    private val cleanup: CoroutineScope,
    private val clipboard: TerminalClipboard,
) : ViewModel() {
    private var connection: Connection? = null
    private val sessions = linkedMapOf<UUID, SshTerminalTab>()
    private val mutableState = MutableStateFlow(SshTerminalUiState())
    val uiState = mutableState.asStateFlow()

    val terminalSettings =
        settings.settings
            .filterNotNull()
            .map(TerminalSettings::from)
            .stateIn(viewModelScope, SharingStarted.Eagerly, settings.settings.value?.let(TerminalSettings::from) ?: TerminalSettings())

    init {
        viewModelScope.launch {
            connection = attempt { store.load().firstOrNull { it.id == connectionId && it.kind == ConnectionKind.SSH } }.getOrNull()
            val loaded = connection
            if (loaded == null) {
                mutableState.value = SshTerminalUiState(loading = false, error = SshError.MISSING_CREDENTIALS.message)
                return@launch
            }
            mutableState.value = SshTerminalUiState(name = loaded.name, loading = false)
            createTab()
        }
    }

    fun terminal(item: TabStripItem): SshTerminalTab? = sessions[item.id]

    fun select(item: TabStripItem) {
        val id = sessions[item.id]?.id ?: return
        mutableState.value = mutableState.value.copy(selectedId = id)
    }

    fun createTab() {
        val loaded = connection ?: return
        val id = UUID.randomUUID()
        sessions[id] = SshTerminalTab(id, loaded, secrets, trust, clients, viewModelScope, cleanup, clipboard, ::close)
        mutableState.value = mutableState.value.copy(tabs = tabItems(), selectedId = id)
    }

    fun close(item: TabStripItem) {
        terminal(item)?.let { close(it.id) }
    }

    private fun close(id: UUID) {
        sessions.remove(id)?.close() ?: return
        val state = mutableState.value
        val selected = if (state.selectedId == id) sessions.keys.lastOrNull() else state.selectedId
        mutableState.value = state.copy(tabs = tabItems(), selectedId = selected)
    }

    private fun tabItems(): List<TabStripItem> = sessions.keys.map { TabStripItem(it, "Terminal", R.drawable.ic_terminal) }

    override fun onCleared() {
        sessions.values.forEach(SshTerminalTab::close)
        sessions.clear()
    }
}
