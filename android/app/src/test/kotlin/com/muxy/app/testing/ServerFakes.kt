package com.muxy.app.testing

import com.muxy.app.networking.server.ScrollbackSnapshot
import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerConnector
import com.muxy.app.networking.server.ServerGitRepository
import com.muxy.app.networking.server.ServerPairingService
import com.muxy.app.networking.server.ServerProject
import com.muxy.app.networking.server.ServerProjectFiles
import com.muxy.app.networking.server.ServerTerminalChannel
import com.muxy.app.persistence.credentials.CredentialStore
import uniffi.muxy_mobile.ClientKind
import uniffi.muxy_mobile.ConnectionEvent
import uniffi.muxy_mobile.Cursor
import uniffi.muxy_mobile.CursorShape
import uniffi.muxy_mobile.Key
import uniffi.muxy_mobile.Line
import uniffi.muxy_mobile.MobileException
import uniffi.muxy_mobile.Modifiers
import uniffi.muxy_mobile.MouseButton
import uniffi.muxy_mobile.PairingLink
import uniffi.muxy_mobile.Screen
import uniffi.muxy_mobile.ScrollDirection
import uniffi.muxy_mobile.ServerCredential
import uniffi.muxy_mobile.Session
import uniffi.muxy_mobile.SessionStatus
import uniffi.muxy_mobile.Span
import uniffi.muxy_mobile.Style
import uniffi.muxy_mobile.TerminalColor
import uniffi.muxy_mobile.Underline

const val PAIRING_LINK = "muxy://pair?v=1&h=192.168.1.20&h=studio.local&p=7419&f=ab&s=cd"

fun serverCredential(
    id: String = "server-1",
    name: String = "Studio",
): ServerCredential =
    ServerCredential(
        serverId = id,
        serverName = name,
        hosts = listOf("192.168.1.20", "studio.local"),
        port = 7419u,
        fingerprint = ByteArray(32) { 7 },
        deviceId = "3f2a0000-0000-4000-8000-000000000000",
        token = ByteArray(32) { 9 },
    )

fun serverProject(
    id: String,
    name: String,
    parentId: String? = null,
    isHome: Boolean = false,
    isWorktree: Boolean = false,
    icon: String? = null,
    logo: ByteArray? = null,
): ServerProject =
    ServerProject(
        id = id,
        name = name,
        directory = "/Users/demo/$name",
        color = "#C370D3",
        icon = icon,
        logo = logo,
        parentId = parentId,
        isHome = isHome,
        isWorktree = isWorktree,
    )

val defaultServerProjects: List<ServerProject>
    get() = listOf(serverProject("home", "Home", isHome = true), serverProject("muxy", "muxy"))

fun session(
    id: ULong,
    project: String = "muxy",
    status: SessionStatus = SessionStatus.LIVE,
): Session = Session(id, project, "/Users/demo/muxy", status, ClientKind.DESKTOP, attached = false)

fun sdkStyle(
    foreground: TerminalColor = TerminalColor.Default,
    inverse: Boolean = false,
    underline: Underline = Underline.NONE,
): Style =
    Style(
        foreground = foreground,
        background = TerminalColor.Default,
        bold = false,
        italic = false,
        faint = false,
        underline = underline,
        underlineColor = TerminalColor.Default,
        strikethrough = false,
        overline = false,
        inverse = inverse,
        invisible = false,
    )

fun sdkLine(text: String): Line = Line(listOf(Span(text, text.length.toUShort(), sdkStyle())))

fun sdkScreen(
    title: String = "",
    columns: Int = 80,
    rows: Int = 24,
    lines: List<Line> = emptyList(),
    directory: String = "/Users/demo/muxy",
    historyRows: Long = 0,
    mouseTracking: Boolean = false,
    alternateScroll: Boolean = false,
): Screen =
    Screen(
        columns = columns.toUShort(),
        rows = rows.toUShort(),
        lines = lines,
        cursor = Cursor(0u, 0u, true, CursorShape.BLOCK),
        title = title,
        directory = directory,
        historyRows = historyRows.toULong(),
        applicationCursorKeys = false,
        bracketedPaste = false,
        mouseTracking = mouseTracking,
        alternateScroll = alternateScroll,
    )

class FakeTerminalChannel(
    override val sessionId: ULong,
    var currentScreen: Screen = sdkScreen(title = "shell $sessionId"),
    private val scrollbacks: ArrayDeque<ScrollbackSnapshot> = ArrayDeque(),
) : ServerTerminalChannel {
    val sent = mutableListOf<String>()
    val pastes = mutableListOf<String>()
    val resizes = mutableListOf<Pair<Int, Int>>()
    val clicks = mutableListOf<String>()
    val scrolls = mutableListOf<String>()
    var detachCount = 0
        private set
    var isClosed = false
        private set
    var failingResizes = 0

    override fun screen(): Screen = currentScreen

    override fun send(text: String) {
        sent += "text:$text"
    }

    override fun send(
        key: Key,
        modifiers: Modifiers,
    ) {
        val control = if (modifiers.control) "ctrl+" else ""
        val name = (key as? Key.Character)?.let { "character(${it.text})" } ?: key::class.simpleName
        sent += "key:$control$name"
    }

    override fun paste(text: String) {
        pastes += text
    }

    override fun click(
        button: MouseButton,
        row: Int,
        column: Int,
        modifiers: Modifiers,
    ) {
        clicks += "$button@$row,$column"
    }

    override fun scroll(
        direction: ScrollDirection,
        row: Int,
        column: Int,
    ) {
        scrolls += "$direction@$row,$column"
    }

    override suspend fun resize(
        columns: Int,
        rows: Int,
    ) {
        resizes += columns to rows
        if (failingResizes <= 0) return
        failingResizes -= 1
        throw MobileException.Timeout()
    }

    override suspend fun scrollback(maxRows: Int): ScrollbackSnapshot =
        scrollbacks.removeFirstOrNull() ?: StubScrollbackSnapshot(emptyList(), 0)

    override suspend fun detach() {
        detachCount += 1
    }

    override fun close() {
        isClosed = true
    }
}

class FakeServerConnection(
    var projects: List<ServerProject> = defaultServerProjects,
    sessions: List<Session> = emptyList(),
    private val attachFailures: MutableMap<ULong, MobileException> = mutableMapOf(),
) : ServerConnection {
    private val sessions = sessions.toMutableList()
    private val channels = mutableMapOf<ULong, FakeTerminalChannel>()
    private var nextSessionId: ULong = 900u

    val attached = mutableListOf<ULong>()
    val created = mutableListOf<Pair<Int, Int>>()
    val ended = mutableListOf<ULong>()
    var isDisconnected = false
        private set
    var failsEnding = false

    override val serverVersion: String = "test"

    fun channel(sessionId: ULong): FakeTerminalChannel? = channels[sessionId]

    override suspend fun projects(): List<ServerProject> {
        checkOpen()
        return projects
    }

    override suspend fun sessions(projectId: String): List<Session> {
        checkOpen()
        return sessions.filter { it.projectId == projectId }
    }

    override suspend fun createSession(
        projectId: String,
        columns: Int,
        rows: Int,
    ): Session {
        checkOpen()
        val created = session(nextSessionId, projectId)
        nextSessionId += 1u
        sessions += created
        this.created += columns to rows
        return created
    }

    override suspend fun endSession(sessionId: ULong) {
        checkOpen()
        if (failsEnding) throw MobileException.Server("busy")
        ended += sessionId
        sessions.removeAll { it.id == sessionId }
    }

    override suspend fun attach(
        sessionId: ULong,
        columns: Int,
        rows: Int,
    ): ServerTerminalChannel {
        checkOpen()
        attachFailures.remove(sessionId)?.let { throw it }
        val channel = FakeTerminalChannel(sessionId)
        channels[sessionId] = channel
        attached += sessionId
        return channel
    }

    var projectFiles: (String) -> ServerProjectFiles = { error("No files configured for $it") }
    var repositories: (String) -> ServerGitRepository = { error("No Git repository configured for $it") }

    override fun files(projectId: String): ServerProjectFiles {
        checkOpen()
        return projectFiles(projectId)
    }

    override fun git(projectId: String): ServerGitRepository {
        checkOpen()
        return repositories(projectId)
    }

    override fun disconnect() {
        isDisconnected = true
    }

    private fun checkOpen() {
        if (isDisconnected) throw MobileException.Disconnected()
    }
}

class FakeServerConnector(
    outcomes: List<Result<FakeServerConnection>>,
) : ServerConnector {
    private val outcomes = ArrayDeque(outcomes)
    private val handlers = mutableListOf<(ConnectionEvent) -> Unit>()
    val credentials = mutableListOf<ServerCredential>()

    val connectCount: Int
        get() = handlers.size

    override suspend fun connect(
        credential: ServerCredential,
        events: (ConnectionEvent) -> Unit,
    ): ServerConnection {
        handlers += events
        credentials += credential
        val outcome = outcomes.removeFirstOrNull() ?: Result.failure(MobileException.Unreachable("no more outcomes"))
        return outcome.getOrThrow()
    }

    fun emit(
        event: ConnectionEvent,
        fromAttempt: Int? = null,
    ) {
        val handler = fromAttempt?.let { handlers[it - 1] } ?: handlers.last()
        handler(event)
    }
}

class StubPairingService(
    private val accepts: (String) -> Boolean = { true },
    private val paired: Result<ServerCredential> = Result.success(serverCredential()),
) : ServerPairingService {
    val pairedNames = mutableListOf<String>()

    override fun parse(link: String): PairingLink {
        if (!accepts(link)) throw MobileException.InvalidLink()
        return PairingLink(listOf("192.168.1.20", "studio.local"), 7419u)
    }

    override suspend fun pair(
        link: String,
        deviceName: String,
    ): ServerCredential {
        pairedNames += deviceName
        return paired.getOrThrow()
    }
}

class InMemoryCredentialStore(
    private val failsSaving: Boolean = false,
) : CredentialStore {
    val items = mutableMapOf<String, ServerCredential>()

    override suspend fun credential(serverId: String): ServerCredential? = items[serverId]

    override suspend fun save(credential: ServerCredential) {
        if (failsSaving) error("The Keystore is unavailable")
        items[credential.serverId] = credential
    }

    override suspend fun delete(serverId: String) {
        items.remove(serverId)
    }
}

class StubScrollbackSnapshot(
    private val initial: List<Line>,
    override val historyRows: Long,
    olderPages: List<Result<List<Line>>> = emptyList(),
) : ScrollbackSnapshot {
    private val pages = ArrayDeque(olderPages)

    var isClosed = false
        private set

    override fun lines(): List<Line> = initial

    override suspend fun loadOlder(maxRows: Int): List<Line> = (pages.removeFirstOrNull() ?: Result.success(emptyList())).getOrThrow()

    override fun close() {
        isClosed = true
    }
}
