import QuartzCore
import SwiftTerm
import UIKit

@MainActor
private final class TerminalTouchDownGestureRecognizer: UIGestureRecognizer {
    private let onTouchDown: () -> Void

    init(onTouchDown: @escaping () -> Void) {
        self.onTouchDown = onTouchDown
        super.init(target: nil, action: nil)
    }

    override func touchesBegan(_: Set<UITouch>, with _: UIEvent) {
        onTouchDown()
        state = .failed
    }
}

@MainActor
final class TerminalViewportHostView: UIView, UIGestureRecognizerDelegate {
    let terminalView: FollowAwareTerminalView
    var onKeyboardOffsetChange: ((CGFloat) -> Void)?

    private let keyboardOcclusionView = UIView()
    private let keyboardGuideProbe = UIView()
    private lazy var viewportPanGesture = UIPanGestureRecognizer(target: self, action: #selector(handleViewportPan))
    private lazy var touchDownGesture = TerminalTouchDownGestureRecognizer { [weak self] in
        self?.stopMomentum()
    }
    private var terminalSize: CGSize?
    private var pendingTerminalSize: CGSize?
    private var terminalSizeConfirmationScheduled = false
    private var viewportState = TerminalViewportState()
    private var lineAccumulator: CGFloat = 0
    private var momentumDisplayLink: CADisplayLink?
    private var momentumVelocity: CGFloat = 0
    private var momentumTimestamp: CFTimeInterval = 0
    private var momentumLocation: CGPoint = .zero
    private var lastReportedKeyboardOffset: CGFloat = 0

    var isInteracting: Bool {
        switch viewportPanGesture.state {
        case .began, .changed:
            return true
        default:
            return momentumDisplayLink != nil
        }
    }

    init(terminalView: FollowAwareTerminalView) {
        self.terminalView = terminalView
        super.init(frame: .zero)
        clipsToBounds = true
        keyboardLayoutGuide.followsUndockedKeyboard = false
        keyboardLayoutGuide.usesBottomSafeArea = false
        terminalView.viewportHost = self
        terminalView.contentInsetAdjustmentBehavior = .never
        terminalView.panGestureRecognizer.isEnabled = false
        addSubview(terminalView)
        keyboardOcclusionView.isUserInteractionEnabled = false
        addSubview(keyboardOcclusionView)
        configureKeyboardGuideProbe()
        configureViewportPanGesture()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    deinit {
        momentumDisplayLink?.invalidate()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            stopMomentum()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0 else { return }
        syncKeyboardOffset()
        positionKeyboardOcclusion()
        guard lockTerminalSizeIfStable() else { return }
        positionTerminal()
    }

    func applyBackgroundColor(_ color: UIColor) {
        backgroundColor = color
        keyboardOcclusionView.backgroundColor = color
    }

    func refreshGesturePriorities() {
        let competingPans = terminalView.gestureRecognizers?
            .compactMap { $0 as? UIPanGestureRecognizer }
            .filter { $0 !== viewportPanGesture && $0 !== terminalView.panGestureRecognizer } ?? []
        for gesture in competingPans {
            gesture.require(toFail: viewportPanGesture)
        }
    }

    func cancelMomentum() {
        stopMomentum()
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === viewportPanGesture else { return true }
        guard !terminalView.hasActiveSelection else { return false }
        let velocity = viewportPanGesture.velocity(in: self)
        return abs(velocity.y) > abs(velocity.x)
    }

    private func configureKeyboardGuideProbe() {
        keyboardGuideProbe.isUserInteractionEnabled = false
        keyboardGuideProbe.translatesAutoresizingMaskIntoConstraints = false
        addSubview(keyboardGuideProbe)
        NSLayoutConstraint.activate([
            keyboardGuideProbe.leadingAnchor.constraint(equalTo: leadingAnchor),
            keyboardGuideProbe.trailingAnchor.constraint(equalTo: trailingAnchor),
            keyboardGuideProbe.topAnchor.constraint(equalTo: keyboardLayoutGuide.topAnchor),
            keyboardGuideProbe.heightAnchor.constraint(equalToConstant: 0),
        ])
    }

    private func configureViewportPanGesture() {
        viewportPanGesture.delegate = self
        viewportPanGesture.maximumNumberOfTouches = 1
        terminalView.addGestureRecognizer(touchDownGesture)
        terminalView.addGestureRecognizer(viewportPanGesture)
        refreshGesturePriorities()
    }

    private func lockTerminalSizeIfStable() -> Bool {
        if terminalSize != nil { return true }
        guard terminalDimensionsAreUsable(for: bounds.size) else { return false }
        pendingTerminalSize = bounds.size
        guard !terminalSizeConfirmationScheduled else { return false }
        terminalSizeConfirmationScheduled = true
        DispatchQueue.main.async { [weak self] in
            self?.confirmTerminalSize()
        }
        return false
    }

    private func confirmTerminalSize() {
        terminalSizeConfirmationScheduled = false
        guard terminalSize == nil, let pendingTerminalSize else { return }
        guard pendingTerminalSize == bounds.size, terminalDimensionsAreUsable(for: bounds.size) else {
            setNeedsLayout()
            return
        }
        terminalSize = pendingTerminalSize
        self.pendingTerminalSize = nil
        positionTerminal()
    }

    private func terminalDimensionsAreUsable(for size: CGSize) -> Bool {
        let terminal = terminalView.getTerminal()
        let optimalSize = terminalView.getOptimalFrameSize().size
        let columnWidth = optimalSize.width / CGFloat(max(terminal.cols, 1))
        let rowHeight = optimalSize.height / CGFloat(max(terminal.rows, 1))
        guard columnWidth > 0, rowHeight > 0 else { return false }
        return Int(size.width / columnWidth) >= 20 && Int(size.height / rowHeight) >= 4
    }

    private func syncKeyboardOffset() {
        let guideFrame = keyboardLayoutGuide.layoutFrame
        let nextOffset = guideFrame == .zero
            ? 0
            : min(bounds.height, max(0, bounds.maxY - guideFrame.minY))
        if abs(nextOffset - viewportState.keyboardOffset) > 0.5 {
            stopMomentum()
            viewportState.updateKeyboardOffset(nextOffset)
            reportKeyboardOffset(nextOffset)
        }
    }

    private func reportKeyboardOffset(_ offset: CGFloat) {
        guard abs(offset - lastReportedKeyboardOffset) > 0.5 else { return }
        lastReportedKeyboardOffset = offset
        DispatchQueue.main.async { [weak self] in
            guard let self, abs(self.lastReportedKeyboardOffset - offset) <= 0.5 else { return }
            self.onKeyboardOffsetChange?(offset)
        }
    }

    private func positionTerminal() {
        guard let terminalSize else { return }
        terminalView.frame = CGRect(
            x: 0,
            y: -viewportState.viewportOffset,
            width: terminalSize.width,
            height: terminalSize.height
        )
    }

    private func positionKeyboardOcclusion() {
        let height = viewportState.keyboardOffset
        keyboardOcclusionView.frame = CGRect(
            x: bounds.minX,
            y: bounds.maxY - height,
            width: bounds.width,
            height: height
        )
    }

    private func captureRenderedViewportOffset() {
        guard let renderedFrame = terminalView.layer.presentation()?.frame else { return }
        viewportState.captureRenderedOffset(-renderedFrame.minY)
        terminalView.layer.removeAllAnimations()
        positionTerminal()
    }

    @objc private func handleViewportPan(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            stopMomentum()
            captureRenderedViewportOffset()
            lineAccumulator = 0
        case .changed:
            let translation = gesture.translation(in: self)
            gesture.setTranslation(.zero, in: self)
            route(delta: -translation.y, location: gesture.location(in: self))
        case .ended:
            startMomentum(
                velocity: -gesture.velocity(in: self).y,
                location: gesture.location(in: self)
            )
        case .cancelled, .failed:
            stopMomentum()
        default:
            break
        }
    }

    @discardableResult
    private func route(delta: CGFloat, location: CGPoint) -> Bool {
        let previousViewportOffset = viewportState.viewportOffset
        let residual = viewportState.consume(delta)
        positionTerminal()
        let viewportMoved = viewportState.viewportOffset != previousViewportOffset
        guard residual != 0 else { return viewportMoved }
        let forwarded = forwardTerminalScroll(delta: residual)
        let terminal = terminalView.getTerminal()
        if terminal.mouseMode != .off {
            guard !forwarded else { return true }
            return routeLines(delta: residual, location: location, destination: .mouse) || viewportMoved
        }
        if terminal.isCurrentBufferAlternate {
            guard !forwarded else { return true }
            return routeLines(delta: residual, location: location, destination: .alternateBuffer) || viewportMoved
        }
        return scrollTerminal(by: residual) || forwarded || viewportMoved
    }

    private func forwardTerminalScroll(delta: CGFloat) -> Bool {
        guard let forwarding = terminalView.session as? any TerminalScrollForwarding else { return false }
        return forwarding.forwardTerminalScroll(
            deltaX: 0,
            deltaY: -Double(delta),
            precise: true
        )
    }

    private func scrollTerminal(by delta: CGFloat) -> Bool {
        let maxOffset = max(0, terminalView.contentSize.height - terminalView.bounds.height)
        let currentOffset = min(max(0, terminalView.contentOffset.y), maxOffset)
        let nextOffset = min(max(0, currentOffset + delta), maxOffset)
        guard nextOffset != currentOffset else { return false }
        terminalView.setContentOffset(
            CGPoint(x: terminalView.contentOffset.x, y: nextOffset),
            animated: false
        )
        return true
    }

    private enum LineDestination {
        case mouse
        case alternateBuffer
    }

    private func routeLines(delta: CGFloat, location: CGPoint, destination: LineDestination) -> Bool {
        let terminal = terminalView.getTerminal()
        let rowHeight = terminalView.bounds.height / CGFloat(max(terminal.rows, 1))
        guard rowHeight > 0 else { return false }
        lineAccumulator += delta
        let lines = Int(lineAccumulator / rowHeight)
        guard lines != 0 else { return true }
        lineAccumulator -= CGFloat(lines) * rowHeight
        switch destination {
        case .mouse:
            sendMouseWheel(lines: lines, location: location, terminal: terminal)
        case .alternateBuffer:
            sendArrowKeys(lines: lines, terminal: terminal)
        }
        return true
    }

    private func sendMouseWheel(lines: Int, location: CGPoint, terminal: Terminal) {
        let rowHeight = terminalView.bounds.height / CGFloat(max(terminal.rows, 1))
        let columnWidth = terminalView.bounds.width / CGFloat(max(terminal.cols, 1))
        guard rowHeight > 0, columnWidth > 0 else { return }
        let terminalLocation = terminalView.convert(location, from: self)
        let visibleX = terminalLocation.x - terminalView.bounds.minX
        let visibleY = terminalLocation.y - terminalView.bounds.minY
        let column = max(0, min(terminal.cols - 1, Int(visibleX / columnWidth)))
        let row = max(0, min(terminal.rows - 1, Int(visibleY / rowHeight)))
        let button = lines > 0 ? 5 : 4
        let flags = terminal.encodeButton(
            button: button,
            release: false,
            shift: false,
            meta: false,
            control: false
        )
        for _ in 0 ..< abs(lines) {
            terminal.sendEvent(buttonFlags: flags, x: column, y: row)
        }
    }

    private func sendArrowKeys(lines: Int, terminal: Terminal) {
        let sequence: String
        if lines > 0 {
            sequence = terminal.applicationCursor ? "\u{1B}OB" : "\u{1B}[B"
        } else {
            sequence = terminal.applicationCursor ? "\u{1B}OA" : "\u{1B}[A"
        }
        terminalView.send(txt: String(repeating: sequence, count: abs(lines)))
    }

    private func startMomentum(velocity: CGFloat, location: CGPoint) {
        guard abs(velocity) > 100 else { return }
        momentumVelocity = velocity
        momentumLocation = location
        momentumTimestamp = 0
        let displayLink = CADisplayLink(target: self, selector: #selector(stepMomentum))
        displayLink.add(to: .main, forMode: .common)
        momentumDisplayLink = displayLink
    }

    @objc private func stepMomentum(_ displayLink: CADisplayLink) {
        guard momentumTimestamp > 0 else {
            momentumTimestamp = displayLink.timestamp
            return
        }
        let elapsed = min(displayLink.timestamp - momentumTimestamp, 1.0 / 30.0)
        momentumTimestamp = displayLink.timestamp
        guard route(delta: momentumVelocity * elapsed, location: momentumLocation) else {
            stopMomentum()
            return
        }
        momentumVelocity *= CGFloat(pow(0.96, elapsed / (1.0 / 60.0)))
        if abs(momentumVelocity) < 30 {
            stopMomentum()
        }
    }

    private func stopMomentum() {
        momentumDisplayLink?.invalidate()
        momentumDisplayLink = nil
        momentumVelocity = 0
        momentumTimestamp = 0
    }
}
