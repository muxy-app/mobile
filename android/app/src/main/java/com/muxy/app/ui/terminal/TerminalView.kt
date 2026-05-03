package com.muxy.app.ui.terminal

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.muxy.app.data.PaneSession
import com.muxy.app.data.SessionRepository
import com.muxy.app.model.PaneOwner
import com.muxy.app.ui.theme.MuxyTheme
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView as TermuxTerminalView
import kotlinx.coroutines.launch

private const val FONT_PATH = "fonts/JetBrainsMonoNerdFontMono-Regular.ttf"
private const val FONT_SIZE_SP = 13

/**
 * Top-level terminal pane. Hosts the vendored termux [TermuxTerminalView] which
 * provides the smooth fling/pinch/scroll/IME machinery, and feeds it bytes from
 * [PaneSession] via a dedicated [MuxyTerminalSession] adapter.
 *
 * The Compose-side `PaneSession` still owns the take-over / release / resize
 * RPCs; we just bypass its internal emulator (via [PaneSession.byteSink]) so
 * the termux view's own emulator does the rendering.
 */
@Composable
fun TerminalView(
    paneID: String,
    session: SessionRepository,
    modifier: Modifier = Modifier,
) {
    val theme by session.deviceTheme.collectAsState()
    val palette = MuxyTheme.from(theme)
    val owners by session.paneOwners.collectAsState()
    val myID by session.myClientID.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val typeface = remember { resolveTypeface(context) }

    var pane by remember(paneID) { mutableStateOf<PaneSession?>(null) }
    val sessionClient = remember { MuxyTerminalSessionClient(context) }
    val viewClient = remember { MuxyTerminalViewClient() }
    val accessory = remember { AccessoryState() }
    val termSession = remember(paneID) { mutableStateOf<MuxyTerminalSession?>(null) }
    val termViewRef = remember(paneID) { mutableStateOf<TermuxTerminalView?>(null) }
    sessionClient.onTextChanged = {
        // Termux's notifyScreenUpdate only forwards a callback; it does not
        // schedule a draw. Without this, bytes appended after takeover are
        // visible only after the next user gesture forces invalidate().
        termViewRef.value?.let { v -> v.post { v.invalidate() } }
    }
    val sizeReporter = remember(paneID) { SizeReporter() }

    @OptIn(ExperimentalLayoutApi::class)
    val keyboardVisible = WindowInsets.isImeVisible

    var measuredCols by remember(paneID) { mutableStateOf<Int?>(null) }
    var measuredRows by remember(paneID) { mutableStateOf<Int?>(null) }
    var autoTakenPaneID by remember { mutableStateOf<String?>(null) }
    // Consumed at mount: true only when the user just opened a project or
    // switched tabs. Bypasses the Mac-owns-it guard for a silent takeover.
    val userInitiatedMount = remember(paneID) { session.consumeAutoTakeover() }
    // Tracks whether the silent-takeover handshake has completed (we became
    // the confirmed owner at least once). After that, normal overlay rules
    // apply — including showing the overlay when the Mac steals back.
    var ownershipConfirmedOnce by remember(paneID) { mutableStateOf(false) }

    val owner = owners[paneID]
    val isOwnedBySelf = remember(owner, myID) {
        val mine = myID
        owner is PaneOwner.Remote && mine != null && owner.deviceID == mine
    }

    viewClient.modifierProvider = { accessory.consume() }
    sessionClient.onPasteRequested = {
        pasteFromClipboardText(context)?.let { text ->
            termSession.value?.write(text.toByteArray(Charsets.UTF_8), 0, text.toByteArray(Charsets.UTF_8).size)
        }
    }

    DisposableEffect(paneID) {
        // 2x2 sentinel so PaneSession can construct; the real takeOverPane is
        // deferred until the termux view reports its measured cols/rows.
        val opened = session.openPane(paneID, 2, 2)
        val ts = MuxyTerminalSession(opened, sessionClient)
        opened.byteSink = { bytes -> ts.acceptRemoteOutput(bytes) }
        pane = opened
        termSession.value = ts
        autoTakenPaneID = null
        onDispose {
            opened.byteSink = null
            ts.finishIfRunning()
            session.closePane(paneID)
            pane = null
            termSession.value = null
            measuredCols = null
            measuredRows = null
        }
    }

    LaunchedEffect(theme, termSession.value) {
        val view = termViewRef.value ?: return@LaunchedEffect
        applyTheme(view, theme?.fg, theme?.bg, theme?.palette)
    }

    LaunchedEffect(isOwnedBySelf) {
        if (isOwnedBySelf) ownershipConfirmedOnce = true
    }

    LaunchedEffect(paneID, pane, measuredCols, measuredRows) {
        val p = pane ?: return@LaunchedEffect
        val c = measuredCols ?: return@LaunchedEffect
        val r = measuredRows ?: return@LaunchedEffect
        if (autoTakenPaneID == paneID) return@LaunchedEffect
        // Auto-takeover policy:
        //  - If the user just opened a project or switched tabs, takeover
        //    silently regardless of current owner (userInitiatedMount).
        //  - Otherwise, never auto-takeover from the Mac — the user must tap
        //    "Take Over" in the overlay.
        if (!userInitiatedMount && owner is PaneOwner.Mac) {
            autoTakenPaneID = paneID // suppress further attempts for this mount
            return@LaunchedEffect
        }
        autoTakenPaneID = paneID
        p.takeOver(c, r)
    }

    Column(
        modifier
            .background(palette.background)
            .imePadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // key(paneID): force a fresh AndroidView per tab so the termux
            // TerminalView is bound to exactly one MuxyTerminalSession for its
            // lifetime. Reusing the same View across tab switches leaves the
            // old session's emulator visible and breaks size reporting.
            key(paneID) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TermuxTerminalView(ctx, null).apply {
                            setTerminalViewClient(viewClient)
                            setTextSize(spToPx(ctx, FONT_SIZE_SP).toInt())
                            setTypeface(typeface)
                            termSession.value?.let { attachSession(it) }
                            applyTheme(this, theme?.fg, theme?.bg, theme?.palette)
                            sizeReporter.attach(this) { c, r ->
                                measuredCols = c
                                measuredRows = r
                                pane?.resize(c, r)
                            }
                            termViewRef.value = this
                        }
                    },
                    update = { view ->
                        termSession.value?.let { view.attachSession(it) }
                        applyTheme(view, theme?.fg, theme?.bg, theme?.palette)
                        sizeReporter.attach(view) { c, r ->
                            measuredCols = c
                            measuredRows = r
                            pane?.resize(c, r)
                        }
                        view.alpha = if (isOwnedBySelf) 1f else 0f
                        view.isFocusable = isOwnedBySelf
                        view.isFocusableInTouchMode = isOwnedBySelf
                    },
                )
            }

            // Suppress overlay only during the brief silent-takeover handshake:
            // user-initiated mount, takeover RPC sent, but server hasn't yet
            // confirmed us as owner. Once confirmed once, normal overlay rules
            // apply — including showing it when the Mac steals back.
            val suppressOverlay =
                userInitiatedMount && autoTakenPaneID == paneID && !ownershipConfirmedOnce
            if (!isOwnedBySelf && owner != null && !suppressOverlay) {
                TakeOverOverlay(
                    ownerName = owner.displayName,
                    foreground = palette.foreground,
                    background = palette.background,
                    onTakeOver = {
                        val p = pane ?: return@TakeOverOverlay
                        // Prefer the termux view's actual size; fall back to the
                        // last reported value, then to a sane default. Without a
                        // fallback the button is dead until layout reports.
                        val view = termViewRef.value
                        val emulator = view?.mEmulator
                        val c = emulator?.mColumns?.takeIf { it > 0 } ?: measuredCols ?: 80
                        val r = emulator?.mRows?.takeIf { it > 0 } ?: measuredRows ?: 24
                        scope.launch {
                            termSession.value?.resetEmulatorScreen()
                            p.takeOver(c, r)
                        }
                    },
                )
            }
        }

        AccessoryBar(
            state = accessory,
            foreground = palette.foreground,
            background = palette.background,
            keyboardVisible = keyboardVisible,
            onSendBytes = { bytes -> pane?.sendBytes(bytes) },
            onPaste = {
                pasteFromClipboardText(context)?.let { text ->
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    termSession.value?.write(bytes, 0, bytes.size)
                }
            },
            onCopy = { /* termux view handles selection + copy through its own action mode */ },
            canCopy = false,
            onToggleKeyboard = {
                val view = termViewRef.value ?: return@AccessoryBar
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    ?: return@AccessoryBar
                if (keyboardVisible) {
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                } else {
                    view.isFocusable = true
                    view.isFocusableInTouchMode = true
                    view.requestFocus()
                    view.post {
                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            },
        )
    }
}

/**
 * Forwards the termux view's actual cols/rows back to Compose state and to
 * [PaneSession.resize]. Re-attached from `update {}` on every recomposition so
 * the first valid size is reported immediately (the OnLayoutChangeListener
 * alone only fires on geometry changes — not when the emulator finally
 * initializes after `attachSession`).
 */
private class SizeReporter {
    private var lastCols = 0
    private var lastRows = 0
    private var attached: View? = null
    private var callback: ((Int, Int) -> Unit)? = null
    private val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
        report(v as TermuxTerminalView)
    }

    fun attach(view: TermuxTerminalView, onSize: (Int, Int) -> Unit) {
        callback = onSize
        if (attached === view) {
            report(view)
            return
        }
        attached?.removeOnLayoutChangeListener(listener)
        view.removeOnLayoutChangeListener(listener)
        view.addOnLayoutChangeListener(listener)
        attached = view
        report(view)
    }

    private fun report(view: TermuxTerminalView) {
        val emulator = view.mEmulator ?: return
        val cols = emulator.mColumns
        val rows = emulator.mRows
        if (cols <= 0 || rows <= 0) return
        if (cols == lastCols && rows == lastRows) return
        lastCols = cols
        lastRows = rows
        callback?.invoke(cols, rows)
    }
}

private fun applyTheme(
    view: TermuxTerminalView,
    fg: Long?,
    bg: Long?,
    palette: List<Long>?,
) {
    val emulator: TerminalEmulator = view.mEmulator ?: return
    val fgInt = ((fg ?: 0xFFFFFFL).toInt() and 0xFFFFFF) or 0xFF000000.toInt()
    val bgInt = ((bg ?: 0x000000L).toInt() and 0xFFFFFF) or 0xFF000000.toInt()
    emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = fgInt
    emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = bgInt
    emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = fgInt
    if (palette != null && palette.size >= 16) {
        for (i in 0 until 16) {
            emulator.mColors.mCurrentColors[i] = (palette[i].toInt() and 0xFFFFFF) or 0xFF000000.toInt()
        }
    }
    view.setBackgroundColor(bgInt)
    view.invalidate()
}

private fun spToPx(context: Context, sp: Int): Float =
    sp * context.resources.displayMetrics.scaledDensity

private fun resolveTypeface(context: Context): Typeface =
    runCatching { Typeface.createFromAsset(context.assets, FONT_PATH) }
        .getOrDefault(Typeface.MONOSPACE)

@Composable
private fun TakeOverOverlay(
    ownerName: String,
    foreground: Color,
    background: Color,
    onTakeOver: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(foreground.copy(alpha = 0.08f))
                .border(width = 1.dp, color = foreground.copy(alpha = 0.2f), shape = RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.Computer, contentDescription = null, tint = foreground, modifier = Modifier.size(28.dp))
            Text("Controlled on $ownerName", color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "This terminal is currently being used on $ownerName. Take over to control it from here.",
                color = foreground.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(foreground)
                    .clickable(onClick = onTakeOver)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Take Over", color = background, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
