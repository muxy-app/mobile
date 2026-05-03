import SwiftTerm
import SwiftUI
import UIKit

struct SwiftTermRepresentable: UIViewRepresentable {
    let paneID: UUID
    let onSize: (UInt32, UInt32) -> Void
    @Environment(ConnectionManager.self) private var connection

    func makeUIView(context: Context) -> MuxySwiftTermView {
        let view = MuxySwiftTermView(frame: .zero, font: TerminalFont.regular(size: TerminalFont.fontSize))
        view.paneID = paneID
        view.connection = connection
        view.terminalDelegate = context.coordinator
        view.backspaceSendsControlH = false
        view.allowMouseReporting = false
        applyTheme(to: view)
        context.coordinator.bind(view: view, paneID: paneID, connection: connection, onSize: onSize)
        subscribe(view: view, paneID: paneID)
        return view
    }

    func updateUIView(_ uiView: MuxySwiftTermView, context: Context) {
        if let previousPaneID = uiView.paneID, previousPaneID != paneID {
            context.coordinator.unbind()
            connection.unsubscribeTerminalBytes(paneID: previousPaneID)
            Task { await connection.releasePane(paneID: previousPaneID) }
            uiView.getTerminal().resetToInitialState()
            uiView.paneID = paneID
            context.coordinator.bind(view: uiView, paneID: paneID, connection: connection, onSize: onSize)
            subscribe(view: uiView, paneID: paneID)
        } else {
            context.coordinator.updateOnSize(onSize)
        }
        applyTheme(to: uiView)
    }

    static func dismantleUIView(_ uiView: MuxySwiftTermView, coordinator: Coordinator) {
        if let paneID = uiView.paneID {
            coordinator.connection?.unsubscribeTerminalBytes(paneID: paneID)
        }
        coordinator.unbind()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    private func subscribe(view: MuxySwiftTermView, paneID: UUID) {
        connection.subscribeTerminalBytes(paneID: paneID) { [weak view] data in
            guard let view else { return }
            let bytes = [UInt8](data)
            view.feedPreservingScroll(byteArray: bytes[...])
        }
    }

    private func applyTheme(to view: MuxySwiftTermView) {
        view.applyMuxyTheme(connection.deviceTheme)
    }

    @MainActor
    final class Coordinator: NSObject, TerminalViewDelegate {
        weak var view: MuxySwiftTermView?
        weak var connection: ConnectionManager?
        var paneID: UUID?
        private var onSize: ((UInt32, UInt32) -> Void)?
        private var lastReportedCols: Int = 0
        private var lastReportedRows: Int = 0
        private var isReady: Bool = false

        func bind(view: MuxySwiftTermView, paneID: UUID, connection: ConnectionManager, onSize: @escaping (UInt32, UInt32) -> Void) {
            self.view = view
            self.paneID = paneID
            self.connection = connection
            self.onSize = onSize
            lastReportedCols = 0
            lastReportedRows = 0
            isReady = true
        }

        func updateOnSize(_ onSize: @escaping (UInt32, UInt32) -> Void) {
            self.onSize = onSize
        }

        func unbind() {
            isReady = false
            view = nil
            paneID = nil
            connection = nil
            onSize = nil
        }

        nonisolated func send(source _: SwiftTerm.TerminalView, data: ArraySlice<UInt8>) {
            MainActor.assumeIsolated {
                guard let paneID, let connection, let view else { return }
                let bytes = view.accessoryTransformedBytes(data)
                guard !bytes.isEmpty else { return }
                connection.sendTerminalInput(paneID: paneID, bytes: bytes)
            }
        }

        nonisolated func sizeChanged(source _: SwiftTerm.TerminalView, newCols: Int, newRows: Int) {
            MainActor.assumeIsolated {
                guard isReady else { return }
                guard newCols > 0, newRows > 0 else { return }
                if newCols == lastReportedCols, newRows == lastReportedRows { return }
                lastReportedCols = newCols
                lastReportedRows = newRows
                let cols = UInt32(newCols)
                let rows = UInt32(newRows)
                onSize?(cols, rows)
                guard let paneID, let connection else { return }
                Task { await connection.resizeTerminal(paneID: paneID, cols: cols, rows: rows) }
            }
        }

        nonisolated func setTerminalTitle(source _: SwiftTerm.TerminalView, title _: String) {}
        nonisolated func hostCurrentDirectoryUpdate(source _: SwiftTerm.TerminalView, directory _: String?) {}
        nonisolated func scrolled(source _: SwiftTerm.TerminalView, position _: Double) {}
        nonisolated func requestOpenLink(source _: SwiftTerm.TerminalView, link _: String, params _: [String: String]) {}
        nonisolated func rangeChanged(source _: SwiftTerm.TerminalView, startY _: Int, endY _: Int) {}
        nonisolated func clipboardCopy(source _: SwiftTerm.TerminalView, content: Data) {
            MainActor.assumeIsolated {
                if let text = String(data: content, encoding: .utf8) {
                    UIPasteboard.general.string = text
                }
            }
        }
    }
}

final class MuxySwiftTermView: SwiftTerm.TerminalView {
    var paneID: UUID?
    weak var connection: ConnectionManager?

    private let muxyAccessoryBar: TerminalAccessoryBar = .init()

    private var keyboardHidden = false
    private var wheelAccumulatedDelta: CGFloat = 0
    private static let wheelPointsPerTick: CGFloat = 16
    private static let wheelMaxTicksPerFrame: Int = 2

    private var userDetachedFromBottom = false
    private static let bottomStickThreshold: CGFloat = 2

    private let hiddenKeyboardPlaceholder: UIView = {
        let view = UIView(frame: .zero)
        view.isHidden = true
        return view
    }()

    override init(frame: CGRect, font: UIFont?) {
        super.init(frame: frame, font: font)
        muxyAccessoryBar.onKey = { [weak self] text in self?.sendAccessoryKey(text) }
        muxyAccessoryBar.onPaste = { [weak self] in self?.pasteFromClipboard() }
        muxyAccessoryBar.onCopy = { [weak self] in self?.copySelectionToClipboard() }
        muxyAccessoryBar.onKeyboardToggle = { [weak self] in self?.toggleKeyboard() }
        inputAccessoryView = muxyAccessoryBar
        setupWheelGesture()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var contentOffset: CGPoint {
        didSet {
            if isTracking || isDragging || isDecelerating {
                updateUserDetachedFromBottom()
            }
        }
    }

    func feedPreservingScroll(byteArray: ArraySlice<UInt8>) {
        preserveDetachedScrollPosition {
            feed(byteArray: byteArray)
        }
    }

    func applyAccessoryTheme(_ theme: ConnectionManager.DeviceTheme?) {
        muxyAccessoryBar.applyTheme(theme)
    }

    private var lastAppliedFg: UInt32?
    private var lastAppliedBg: UInt32?
    private var lastAppliedPalette: [UInt32]?

    func applyMuxyTheme(_ theme: ConnectionManager.DeviceTheme?) {
        let fgRGB = theme?.fg ?? 0xFFFFFF
        let bgRGB = theme?.bg ?? 0x000000
        if fgRGB != lastAppliedFg || bgRGB != lastAppliedBg {
            lastAppliedFg = fgRGB
            lastAppliedBg = bgRGB
            let terminal = getTerminal()
            setForegroundColor(source: terminal, color: Self.swiftTermColor(fgRGB))
            setBackgroundColor(source: terminal, color: Self.swiftTermColor(bgRGB))
        }
        if let palette = theme?.palette, palette.count == 16, palette != lastAppliedPalette {
            lastAppliedPalette = palette
            installColors(palette.map(Self.swiftTermColor))
        }
        caretColor = UIColor(theme?.fgColor ?? .white)
        overrideUserInterfaceStyle = (theme?.isDark ?? true) ? .dark : .light
        applyAccessoryTheme(theme)
    }

    private static func swiftTermColor(_ rgb: UInt32) -> SwiftTerm.Color {
        SwiftTerm.Color(
            red: UInt16((rgb >> 16) & 0xFF) * 0x0101,
            green: UInt16((rgb >> 8) & 0xFF) * 0x0101,
            blue: UInt16(rgb & 0xFF) * 0x0101
        )
    }

    var modifierIsArmed: Bool { muxyAccessoryBar.modifierArmed }
    var activeAccessoryModifier: TerminalModifier { muxyAccessoryBar.activeModifier }

    func clearArmedModifier() {
        muxyAccessoryBar.setModifierArmed(false)
    }

    func accessoryTransformedBytes(_ slice: ArraySlice<UInt8>) -> Data {
        if modifierIsArmed,
           let text = String(bytes: slice, encoding: .utf8),
           let transformed = Self.transform(text, with: activeAccessoryModifier)
        {
            clearArmedModifier()
            return Data(transformed.utf8)
        }
        return Data(slice)
    }

    private func sendAccessoryKey(_ text: String) {
        sendBytes(Data(text.utf8))
    }

    private func sendBytes(_ bytes: Data) {
        guard !bytes.isEmpty, let paneID, let connection else { return }
        connection.sendTerminalInput(paneID: paneID, bytes: bytes)
    }

    private func pasteFromClipboard() {
        guard let text = UIPasteboard.general.string, !text.isEmpty else { return }
        sendBytes(Data(text.utf8))
    }

    private func copySelectionToClipboard() {
        guard let text = getSelection(), !text.isEmpty else { return }
        UIPasteboard.general.string = text
    }

    private func toggleKeyboard() {
        keyboardHidden.toggle()
        muxyAccessoryBar.setKeyboardVisible(!keyboardHidden)
        inputView = keyboardHidden ? hiddenKeyboardPlaceholder : nil
        if !isFirstResponder { _ = becomeFirstResponder() }
        reloadInputViews()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
        _ = becomeFirstResponder()
    }

    private func setupWheelGesture() {
        let gesture = UIPanGestureRecognizer(target: self, action: #selector(handleWheelPan(_:)))
        gesture.minimumNumberOfTouches = 1
        gesture.maximumNumberOfTouches = 1
        gesture.delegate = wheelGestureDelegate
        addGestureRecognizer(gesture)
    }

    private func preserveDetachedScrollPosition(_ update: () -> Void) {
        let wasDetached = userDetachedFromBottom
        let preservedOffset = contentOffset
        update()
        guard wasDetached else {
            updateUserDetachedFromBottom()
            return
        }
        let maxOffsetY = max(0, contentSize.height - bounds.height)
        let restoredOffset = CGPoint(x: preservedOffset.x, y: min(preservedOffset.y, maxOffsetY))
        if contentOffset != restoredOffset {
            setContentOffset(restoredOffset, animated: false)
        }
        updateUserDetachedFromBottom()
    }

    private func updateUserDetachedFromBottom() {
        let maxOffsetY = max(0, contentSize.height - bounds.height)
        let distanceFromBottom = maxOffsetY - contentOffset.y
        userDetachedFromBottom = distanceFromBottom > Self.bottomStickThreshold
    }

    private lazy var wheelGestureDelegate: WheelGestureDelegate = {
        let d = WheelGestureDelegate()
        d.shouldFire = { [weak self] in
            self?.getTerminal().mouseMode != .off
        }
        return d
    }()

    @objc
    private func handleWheelPan(_ gesture: UIPanGestureRecognizer) {
        let terminal = getTerminal()
        guard terminal.mouseMode != .off else { return }

        switch gesture.state {
        case .began:
            wheelAccumulatedDelta = 0
            gesture.setTranslation(.zero, in: self)
        case .changed:
            let translation = gesture.translation(in: self)
            gesture.setTranslation(.zero, in: self)
            wheelAccumulatedDelta += translation.y
            let baseTicks = Int((wheelAccumulatedDelta / Self.wheelPointsPerTick).rounded(.towardZero))
            guard baseTicks != 0 else { return }
            wheelAccumulatedDelta -= CGFloat(baseTicks) * Self.wheelPointsPerTick
            let clamped = max(-Self.wheelMaxTicksPerFrame, min(Self.wheelMaxTicksPerFrame, baseTicks))
            guard clamped != 0 else { return }
            emitWheelTicks(clamped, terminal: terminal, location: gesture.location(in: self))
        case .ended,
             .cancelled,
             .failed:
            wheelAccumulatedDelta = 0
        default:
            break
        }
    }

    private func emitWheelTicks(_ ticks: Int, terminal: Terminal, location _: CGPoint) {
        let col = max(0, terminal.cols / 2)
        let row = max(0, terminal.rows / 2)
        let button = ticks > 0 ? 4 : 5
        let count = abs(ticks)
        let encoded = terminal.encodeButton(button: button, release: false, shift: false, meta: false, control: false)
        for _ in 0 ..< count {
            terminal.sendEvent(buttonFlags: encoded, x: col, y: row)
        }
    }

    private static func transform(_ text: String, with modifier: TerminalModifier) -> String? {
        switch modifier {
        case .ctrl: ctrlTransform(text)
        case .shift: text.uppercased()
        case .alt: "\u{1B}" + text
        case .cmd: text
        }
    }

    private static func ctrlTransform(_ text: String) -> String? {
        guard text.count == 1, let scalar = text.unicodeScalars.first else { return nil }
        let value = scalar.value
        switch value {
        case 0x40 ... 0x5F:
            // swiftlint:disable:next force_unwrapping
            return String(UnicodeScalar(value - 0x40)!)
        case 0x61 ... 0x7A:
            // swiftlint:disable:next force_unwrapping
            return String(UnicodeScalar(value - 0x60)!)
        case 0x20:
            return "\u{00}"
        default:
            return nil
        }
    }
}

final class WheelGestureDelegate: NSObject, UIGestureRecognizerDelegate {
    var shouldFire: (() -> Bool)?

    func gestureRecognizerShouldBegin(_: UIGestureRecognizer) -> Bool {
        shouldFire?() ?? false
    }

    func gestureRecognizer(_: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith _: UIGestureRecognizer) -> Bool {
        true
    }
}
