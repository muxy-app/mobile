import MuxyMobile
import UIKit

final class TerminalSurfaceView: UIView, UIScrollViewDelegate {
    private let controller: TerminalController
    private let scrollView = UIScrollView()
    private let canvas: TerminalCanvasView
    private let input = TerminalInputView()
    private let accessoryBar = TerminalAccessoryBar()
    private var displayLink: DisplayLinkDriver?
    private var editMenu: UIEditMenuInteraction?
    private var theme: ThemePalette
    private var useNerdFont: Bool
    private var metrics: TerminalMetrics
    private var lines: [Line] = []
    private var columnCount = 0
    private var cursor: TerminalCursorMark?
    private var liveHistoryRows: UInt64 = 0
    private var displayedHistory: HistoryDocument?
    private var isRequestingHistory = false
    private var needsScreen = true
    private var selection: TerminalSelection? {
        didSet {
            canvas.selection = selection
            accessoryBar.setCanCopySelection(selection != nil)
        }
    }

    private var autoFocus = false

    init(controller: TerminalController, theme: ThemePalette, useNerdFont: Bool) {
        self.controller = controller
        self.theme = theme
        self.useNerdFont = useNerdFont
        metrics = TerminalMetrics(fontSize: TerminalFont.defaultSize, useNerdFont: useNerdFont, scale: UITraitCollection.current.displayScale)
        canvas = TerminalCanvasView(renderer: TerminalRenderer(metrics: metrics, theme: theme))
        super.init(frame: .zero)
        configureViews()
        configureInput()
        configureGestures()
        controller.display = self
        applyTheme()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    func update(theme: ThemePalette, useNerdFont: Bool, autoFocus: Bool) {
        self.autoFocus = autoFocus
        guard theme != self.theme || useNerdFont != self.useNerdFont else { return }
        self.theme = theme
        self.useNerdFont = useNerdFont
        applyFont()
        applyTheme()
    }

    func detachFromController() {
        guard controller.display === self else { return }
        controller.display = nil
        controller.onModifierStateChange = nil
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else {
            displayLink?.stop()
            displayLink = nil
            input.resignFirstResponder()
            return
        }
        let link = DisplayLinkDriver { [weak self] in self?.renderFrame() }
        link.start()
        displayLink = link
        screenNeedsRefresh()
        guard autoFocus else { return }
        input.becomeFirstResponder()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        positionCanvas()
        reportViewport()
        guard controller.isFollowing else { return }
        revealCursor(animated: false)
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        positionCanvas()
        reportHistoryTop()
        guard scrollView.isDragging || scrollView.isDecelerating else { return }
        if displayedHistory == nil, scrollView.isDragging, scrollView.contentOffset.y < -metrics.cellHeight * 2 {
            requestHistory()
        }
        if displayedHistory != nil, scrollView.contentOffset.y < metrics.cellHeight * Self.olderHistoryThresholdRows {
            loadOlderHistory()
        }
    }

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        controller.setFollowing(false)
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        guard !decelerate else { return }
        settleScrolling()
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        settleScrolling()
    }

    private static let olderHistoryThresholdRows: CGFloat = 40

    private func configureViews() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.delegate = self
        scrollView.alwaysBounceVertical = true
        scrollView.contentInsetAdjustmentBehavior = .never
        scrollView.indicatorStyle = .white
        scrollView.keyboardDismissMode = .none
        scrollView.delaysContentTouches = false
        scrollView.addSubview(canvas)
        addSubview(scrollView)
        addSubview(input)
        keyboardLayoutGuide.followsUndockedKeyboard = false
        NSLayoutConstraint.activate([
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: keyboardLayoutGuide.topAnchor),
        ])
        isAccessibilityElement = true
        accessibilityLabel = "Terminal"
    }

    private func configureInput() {
        input.delegate = self
        input.setAccessory(accessoryBar)
        accessoryBar.onKey = { [weak self] key in self?.press(TerminalKeyStroke(key)) }
        accessoryBar.onText = { [weak self] text in self?.sendFromAccessory(text) }
        accessoryBar.onPaste = { [weak self] in self?.pasteFromClipboard() }
        accessoryBar.onCopy = { [weak self] in self?.copySelection() }
        accessoryBar.onModifierToggle = { [weak self] armed in self?.controller.setModifierArmed(armed) }
        accessoryBar.onModifierChange = { [weak self] modifier in self?.controller.selectModifier(modifier) }
        accessoryBar.onKeyboardToggle = { [weak self] in self?.toggleSoftKeyboard() }
        controller.onModifierStateChange = { [weak self] modifier, armed in
            self?.accessoryBar.syncActiveModifier(modifier)
            self?.accessoryBar.syncModifierArmed(armed)
        }
    }

    private func configureGestures() {
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap))
        addGestureRecognizer(tap)

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        longPress.minimumPressDuration = 0.35
        addGestureRecognizer(longPress)

        let menu = UIEditMenuInteraction(delegate: self)
        addInteraction(menu)
        editMenu = menu
    }

    private func applyTheme() {
        canvas.renderer = TerminalRenderer(metrics: metrics, theme: theme)
        let background = UIColor(cgColor: canvas.renderer.backgroundColor)
        backgroundColor = background
        scrollView.backgroundColor = background
        accessoryBar.applyTheme(
            background: background,
            foreground: UIColor(rgb: theme.foreground)
        )
    }

    private func applyFont() {
        metrics = TerminalMetrics(fontSize: TerminalFont.defaultSize, useNerdFont: useNerdFont, scale: traitCollection.displayScale)
        canvas.renderer = TerminalRenderer(metrics: metrics, theme: theme)
        updateContentSize()
        setNeedsLayout()
    }

    private func positionCanvas() {
        canvas.frame = CGRect(origin: scrollView.contentOffset, size: scrollView.bounds.size)
        canvas.origin = scrollView.contentOffset
    }

    private func reportViewport() {
        guard let size = metrics.gridSize(fitting: bounds.size) else { return }
        controller.viewportDidChange(size)
    }

    private func renderFrame() {
        displayLink?.pause()
        guard needsScreen else { return }
        guard controller.mode == .live else { return }
        needsScreen = false
        if displayedHistory != nil {
            displayedHistory = nil
            selection = nil
        }
        guard let screen = controller.currentScreen() else { return }
        apply(screen)
    }

    private func apply(_ screen: Screen) {
        let rowCount = max(Int(screen.rows), screen.lines.count)
        var screenLines = screen.lines
        if screenLines.count < rowCount {
            screenLines += Array(repeating: Line(spans: []), count: rowCount - screenLines.count)
        }
        if columnCount != Int(screen.columns) {
            selection = nil
            columnCount = Int(screen.columns)
        }
        liveHistoryRows = screen.historyRows
        cursor = screen.cursor.visible ? TerminalCursorMark(
            row: Int(screen.cursor.row),
            column: min(Int(screen.cursor.column), max(columnCount - 1, 0)),
            shape: screen.cursor.shape
        ) : nil
        lines = screenLines
        canvas.update(lines: screenLines, columnCount: columnCount, cursor: cursor)
        updateContentSize()
        guard controller.isFollowing else { return }
        revealCursor(animated: false)
    }

    private func updateContentSize() {
        scrollView.contentSize = CGSize(
            width: CGFloat(columnCount) * metrics.cellWidth,
            height: CGFloat(lines.count) * metrics.cellHeight
        )
        positionCanvas()
    }

    private func revealCursor(animated: Bool) {
        guard displayedHistory == nil else { return }
        let target = cursorRevealingOffset()
        guard target != scrollView.contentOffset else { return }
        scrollView.setContentOffset(target, animated: animated)
    }

    private func cursorRevealingOffset() -> CGPoint {
        guard let cursor else {
            return TerminalScrollMath.bottomOffset(
                visibleSize: scrollView.bounds.size,
                contentSize: scrollView.contentSize,
                current: scrollView.contentOffset
            )
        }
        let rect = metrics.cellRect(row: cursor.row, column: cursor.column)
        return TerminalScrollMath.offset(
            revealing: rect.insetBy(dx: -metrics.cellWidth, dy: 0),
            visibleSize: scrollView.bounds.size,
            contentSize: scrollView.contentSize,
            current: scrollView.contentOffset
        )
    }

    private func settleScrolling() {
        guard displayedHistory == nil else {
            returnToLiveIfAtBottom()
            return
        }
        guard isCursorInView else { return }
        controller.setFollowing(true)
    }

    private func returnToLiveIfAtBottom() {
        guard isScrolledToBottom else { return }
        controller.returnToLive()
    }

    private var isScrolledToBottom: Bool {
        TerminalScrollMath.isAtBottom(
            offset: scrollView.contentOffset,
            visibleSize: scrollView.bounds.size,
            contentSize: scrollView.contentSize,
            tolerance: metrics.cellHeight / 2
        )
    }

    private var isCursorInView: Bool {
        guard let cursor else { return isScrolledToBottom }
        return TerminalScrollMath.isVisible(
            metrics.cellRect(row: cursor.row, column: cursor.column),
            offset: scrollView.contentOffset,
            visibleSize: scrollView.bounds.size,
            tolerance: CGSize(width: metrics.cellWidth / 2, height: metrics.cellHeight / 2)
        )
    }

    private func reportHistoryTop() {
        guard displayedHistory != nil else { return }
        controller.setAtHistoryTop(scrollView.contentOffset.y <= metrics.cellHeight / 2)
    }

    private func requestHistory() {
        guard !isRequestingHistory, liveHistoryRows > 0, controller.isLive else { return }
        isRequestingHistory = true
        Task { [weak self] in
            guard let self else { return }
            let document = await controller.enterHistory()
            isRequestingHistory = false
            guard let document else { return }
            show(document)
        }
    }

    private func show(_ history: HistoryDocument) {
        displayedHistory = history
        selection = nil
        let shift = CGFloat(history.historyRowCount) * metrics.cellHeight
        lines = history.lines
        cursor = nil
        canvas.update(lines: lines, columnCount: columnCount, cursor: nil)
        updateContentSize()
        scrollView.contentOffset.y += shift
        positionCanvas()
    }

    private func loadOlderHistory() {
        guard let history = displayedHistory, !history.isLoading, !history.reachedStart else { return }
        Task { [weak self] in
            guard let self else { return }
            let added = await controller.loadOlderHistory()
            guard added > 0, displayedHistory === history else { return }
            lines = history.lines
            canvas.update(lines: lines, columnCount: columnCount, cursor: nil)
            updateContentSize()
            scrollView.contentOffset.y += CGFloat(added) * metrics.cellHeight
            positionCanvas()
            if let current = selection {
                selection = TerminalSelection(
                    anchor: TerminalCellPosition(row: current.anchor.row + added, column: current.anchor.column),
                    head: TerminalCellPosition(row: current.head.row + added, column: current.head.column)
                )
            }
        }
    }

    private func press(_ stroke: TerminalKeyStroke) {
        selection = nil
        input.resetBuffer()
        controller.send(stroke)
    }

    private func sendFromAccessory(_ text: String) {
        selection = nil
        input.resetBuffer()
        controller.sendText(text)
    }

    private func pasteFromClipboard() {
        guard let text = UIPasteboard.general.string, !text.isEmpty else { return }
        selection = nil
        input.resetBuffer()
        controller.paste(text)
    }

    private func copySelection() {
        guard let selection else { return }
        UIPasteboard.general.string = selection.text(in: lines, columnCount: columnCount)
        self.selection = nil
    }

    private func toggleSoftKeyboard() {
        input.toggleSoftKeyboard()
        accessoryBar.setKeyboardVisible(!input.softKeyboardHidden)
    }

    private func cell(at point: CGPoint) -> TerminalCellPosition? {
        guard !lines.isEmpty, columnCount > 0 else { return nil }
        let location = scrollView.convert(point, from: self)
        let row = min(max(Int(location.y / metrics.cellHeight), 0), lines.count - 1)
        let column = min(max(Int(location.x / metrics.cellWidth), 0), columnCount - 1)
        return TerminalCellPosition(row: row, column: column)
    }

    @objc private func handleTap() {
        editMenu?.dismissMenu()
        selection = nil
        if !input.isFirstResponder {
            input.becomeFirstResponder()
        }
    }

    @objc private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: self)
        switch gesture.state {
        case .began:
            guard let position = cell(at: point) else { return }
            selection = TerminalSelection(anchor: position, head: position)
            UISelectionFeedbackGenerator().selectionChanged()
        case .changed:
            guard var current = selection, let position = cell(at: point) else { return }
            current.head = position
            selection = current
        case .ended:
            guard selection != nil else { return }
            editMenu?.presentEditMenu(with: UIEditMenuConfiguration(identifier: nil, sourcePoint: point))
        case .cancelled, .failed:
            selection = nil
        default:
            break
        }
    }
}

extension TerminalSurfaceView: TerminalDisplay {
    func screenNeedsRefresh() {
        needsScreen = true
        displayLink?.requestFrame()
    }
}

extension TerminalSurfaceView: TerminalInputViewDelegate {
    func inputView(_ inputView: TerminalInputView, didProduce effects: [TerminalInputEffect]) {
        selection = nil
        for effect in effects {
            switch effect {
            case let .text(text):
                controller.sendText(text)
            case let .backspaces(count):
                for _ in 0..<count {
                    controller.send(TerminalKeyStroke(.backspace))
                }
            }
        }
    }

    func inputView(_ inputView: TerminalInputView, didPress stroke: TerminalKeyStroke) {
        selection = nil
        controller.send(stroke)
    }

    func inputView(_ inputView: TerminalInputView, didChangeMarkedText markedText: String?) {
        canvas.markedText = markedText
    }

    func inputViewDidRequestPaste(_ inputView: TerminalInputView) {
        pasteFromClipboard()
    }

    func inputViewDidRequestCopy(_ inputView: TerminalInputView) {
        copySelection()
    }

    func inputViewCaretRect(_ inputView: TerminalInputView) -> CGRect {
        guard let cursor else { return .zero }
        let rect = metrics.cellRect(row: cursor.row, column: cursor.column)
        return inputView.convert(rect, from: scrollView)
    }
}

extension TerminalSurfaceView: UIEditMenuInteractionDelegate {
    func editMenuInteraction(
        _ interaction: UIEditMenuInteraction,
        menuFor configuration: UIEditMenuConfiguration,
        suggestedActions: [UIMenuElement]
    ) -> UIMenu? {
        guard selection != nil else { return nil }
        let copy = UIAction(title: "Copy", image: UIImage(systemName: "doc.on.doc")) { [weak self] _ in
            self?.copySelection()
        }
        return UIMenu(children: [copy])
    }
}
