import OSLog
import SwiftTerm
import SwiftUI

struct TerminalViewContainer: UIViewRepresentable {
    let session: TerminalSession
    let theme: TerminalTheme
    let fontSize: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(session: session)
    }

    func makeUIView(context: Context) -> TerminalView {
        let view = FollowAwareTerminalView(frame: .zero, font: TerminalFont.mono(size: fontSize))
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
        context.coordinator.appliedTheme = theme
        return view
    }

    func updateUIView(_ view: TerminalView, context: Context) {
        session.takeOverIfReady()
        if context.coordinator.appliedFontSize != fontSize {
            view.font = TerminalFont.mono(size: fontSize)
            context.coordinator.appliedFontSize = fontSize
        }
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
    }

    @MainActor
    final class Coordinator: NSObject, TerminalViewDelegate {
        private let session: TerminalSession
        var appliedFontSize: CGFloat?
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

    weak var session: TerminalSession?

    private let accessoryBar = TerminalAccessoryBar()
    private var keyboardHidden = false
    private var protectedContentOffset: CGPoint?
    private var isRestoringProtectedOffset = false

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

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
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
}
