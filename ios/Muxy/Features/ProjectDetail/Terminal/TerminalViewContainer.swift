import OSLog
import SwiftTerm
import SwiftUI

struct TerminalViewContainer: UIViewRepresentable {
    let session: TerminalSession
    let theme: TerminalTheme
    let fontSize: CGFloat
    let useNerdFont: Bool
    let autoFocusTerminal: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(session: session)
    }

    func makeUIView(context: Context) -> TerminalView {
        let view = FollowAwareTerminalView(frame: .zero, font: TerminalFont.mono(size: fontSize, useNerdFont: useNerdFont))
        view.autoFocusTerminal = autoFocusTerminal
        view.onUserScroll = { [weak coordinator = context.coordinator] position in
            coordinator?.userScrolled(toPosition: position)
        }
        view.session = session
        view.configureAccessoryBar()
        view.terminalDelegate = context.coordinator
        view.allowMouseReporting = true
        apply(theme: theme, to: view)
        session.attach(view)
        context.coordinator.appliedFontSize = fontSize
        context.coordinator.appliedUseNerdFont = useNerdFont
        context.coordinator.appliedTheme = theme
        return view
    }

    func updateUIView(_ view: TerminalView, context: Context) {
        session.takeOverIfReady()
        if context.coordinator.appliedFontSize != fontSize || context.coordinator.appliedUseNerdFont != useNerdFont {
            view.font = TerminalFont.mono(size: fontSize, useNerdFont: useNerdFont)
            context.coordinator.appliedFontSize = fontSize
            context.coordinator.appliedUseNerdFont = useNerdFont
        }
        (view as? FollowAwareTerminalView)?.autoFocusTerminal = autoFocusTerminal
        if context.coordinator.appliedTheme != theme {
            apply(theme: theme, to: view)
            context.coordinator.appliedTheme = theme
        }
    }

    private func apply(theme: TerminalTheme, to view: TerminalView) {
        view.backgroundColor = theme.background
        view.nativeForegroundColor = theme.foreground
        view.nativeBackgroundColor = theme.background
        if !theme.palette.isEmpty {
            view.installColors(theme.palette)
        }
        (view as? FollowAwareTerminalView)?.applyAccessoryTheme(background: theme.background, foreground: theme.foreground)
    }

    @MainActor
    final class Coordinator: NSObject, TerminalViewDelegate {
        private let session: TerminalSession
        var appliedFontSize: CGFloat?
        var appliedUseNerdFont: Bool?
        var appliedTheme: TerminalTheme?

        init(session: TerminalSession) {
            self.session = session
        }

        func sizeChanged(source: TerminalView, newCols: Int, newRows: Int) {
            session.takeOverIfReady()
            session.reportResize(cols: newCols, rows: newRows)
        }

        func send(source: TerminalView, data: ArraySlice<UInt8>) {
            Log.terminal.debug("delegate.send \(data.count, privacy: .public) bytes")
            session.sendBytes(data)
        }

        func scrolled(source: TerminalView, position: Double) {
            guard source.isTracking || source.isDragging || source.isDecelerating else { return }
            userScrolled(toPosition: position)
        }

        func userScrolled(toPosition position: Double) {
            session.userScrolled(toPosition: position)
        }

        func setTerminalTitle(source: TerminalView, title: String) {
            session.setTitle(title)
        }

        func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}

        func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {
            guard let url = URL(string: link), UIApplication.shared.canOpenURL(url) else { return }
            UIApplication.shared.open(url)
        }

        func clipboardCopy(source: TerminalView, content: Data) {
            guard let text = String(data: content, encoding: .utf8) else { return }
            UIPasteboard.general.string = text
        }

        func rangeChanged(source: TerminalView, startY: Int, endY: Int) {
            (source as? FollowAwareTerminalView)?.refreshCopyAvailability()
        }
    }
}

final class FollowAwareTerminalView: TerminalView {
    var onUserScroll: ((Double) -> Void)?
    var autoFocusTerminal = true

    weak var session: TerminalSession?

    private let accessoryBar = TerminalAccessoryBar()
    private var keyboardHidden = false
    private var protectedContentOffset: CGPoint?
    private var isRestoringProtectedOffset = false
    private var wheelScrollGesture: UIPanGestureRecognizer?
    private var wheelScrollAccumulatedY: CGFloat = 0

    private let hiddenKeyboardPlaceholder: UIView = {
        let view = UIView(frame: .zero)
        view.isHidden = true
        return view
    }()

    override var contentOffset: CGPoint {
        didSet {
            if let protectedContentOffset, !isRestoringProtectedOffset {
                isRestoringProtectedOffset = true
                contentOffset = protectedContentOffset
                isRestoringProtectedOffset = false
                return
            }
            guard isTracking || isDragging || isDecelerating else { return }
            onUserScroll?(normalizedScrollPosition)
        }
    }

    func preserveInteractiveOffsetDuringTerminalUpdate(_ update: () -> Void) {
        let shouldProtect = isTracking || isDragging || isDecelerating
        protectedContentOffset = shouldProtect ? contentOffset : nil
        update()
        protectedContentOffset = nil
    }

    func configureAccessoryBar() {
        inputAccessoryView = accessoryBar
        accessoryBar.onKey = { [weak self] text in self?.session?.sendText(text) }
        accessoryBar.onPaste = { [weak self] in self?.session?.paste() }
        accessoryBar.onCopy = { [weak self] in self?.session?.copySelection() }
        accessoryBar.onModifierToggle = { [weak self] armed in self?.session?.setModifierArmed(armed) }
        accessoryBar.onModifierChange = { [weak self] modifier in self?.session?.selectModifier(modifier) }
        accessoryBar.onKeyboardToggle = { [weak self] in self?.toggleKeyboard() }
        session?.onModifierStateChange = { [weak self] modifier, armed in
            self?.accessoryBar.syncActiveModifier(modifier)
            self?.accessoryBar.syncModifierArmed(armed)
        }
    }

    func refreshCopyAvailability() {
        accessoryBar.setCanCopySelection(session?.canCopySelection ?? false)
    }

    func applyAccessoryTheme(background: UIColor, foreground: UIColor) {
        accessoryBar.applyTheme(background: background, foreground: foreground)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
        guard autoFocusTerminal else { return }
        _ = becomeFirstResponder()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        session?.takeOverIfReady()
    }

    private func toggleKeyboard() {
        keyboardHidden.toggle()
        accessoryBar.setKeyboardVisible(!keyboardHidden)
        inputView = keyboardHidden ? hiddenKeyboardPlaceholder : nil
        if !isFirstResponder { _ = becomeFirstResponder() }
        reloadInputViews()
    }

    private var normalizedScrollPosition: Double {
        let maxOffset = max(0, contentSize.height - bounds.height)
        guard maxOffset > 0 else { return 1 }
        return Double(min(max(contentOffset.y, 0), maxOffset) / maxOffset)
    }

    override func mouseModeChanged(source: Terminal) {
        super.mouseModeChanged(source: source)
        if source.mouseMode == .off {
            disableWheelScrollGesture()
            return
        }
        enableWheelScrollGesture()
    }

    private func enableWheelScrollGesture() {
        guard wheelScrollGesture == nil else { return }
        let gesture = UIPanGestureRecognizer(target: self, action: #selector(handleWheelScroll))
        gesture.delegate = self
        addGestureRecognizer(gesture)
        wheelScrollGesture = gesture
    }

    private func disableWheelScrollGesture() {
        guard let gesture = wheelScrollGesture else { return }
        removeGestureRecognizer(gesture)
        wheelScrollGesture = nil
    }

    @objc private func handleWheelScroll(_ gesture: UIPanGestureRecognizer) {
        let terminal = getTerminal()
        guard terminal.mouseMode != .off else { return }
        switch gesture.state {
        case .began:
            wheelScrollAccumulatedY = 0
        case .changed:
            wheelScrollAccumulatedY += gesture.translation(in: self).y
            gesture.setTranslation(.zero, in: self)
            sendWheelEvents(for: gesture, terminal: terminal)
        default:
            break
        }
    }

    private func sendWheelEvents(for gesture: UIPanGestureRecognizer, terminal: Terminal) {
        let rowHeight = bounds.height / CGFloat(max(terminal.rows, 1))
        let columnWidth = bounds.width / CGFloat(max(terminal.cols, 1))
        guard rowHeight > 0, columnWidth > 0 else { return }
        let steps = Int(wheelScrollAccumulatedY / rowHeight)
        guard steps != 0 else { return }
        wheelScrollAccumulatedY -= CGFloat(steps) * rowHeight
        let location = gesture.location(in: self)
        let column = max(0, min(terminal.cols - 1, Int(location.x / columnWidth)))
        let row = max(0, min(terminal.rows - 1, Int(location.y / rowHeight)))
        let button = steps < 0 ? 5 : 4
        let buttonFlags = terminal.encodeButton(button: button, release: false, shift: false, meta: false, control: false)
        for _ in 0 ..< abs(steps) {
            terminal.sendEvent(buttonFlags: buttonFlags, x: column, y: row)
        }
    }
}

extension FollowAwareTerminalView: UIGestureRecognizerDelegate {
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        false
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy other: UIGestureRecognizer) -> Bool {
        gestureRecognizer === wheelScrollGesture && other is UIPanGestureRecognizer
    }
}
