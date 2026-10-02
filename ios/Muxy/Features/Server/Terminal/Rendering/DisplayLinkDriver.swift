import QuartzCore

final class DisplayLinkDriver {
    private var link: CADisplayLink?
    private let onFrame: () -> Void

    init(onFrame: @escaping () -> Void) {
        self.onFrame = onFrame
    }

    deinit {
        link?.invalidate()
    }

    func start() {
        guard link == nil else { return }
        let link = CADisplayLink(target: DisplayLinkTarget(driver: self), selector: #selector(DisplayLinkTarget.step))
        link.isPaused = true
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    func stop() {
        link?.invalidate()
        link = nil
    }

    func requestFrame() {
        link?.isPaused = false
    }

    func pause() {
        link?.isPaused = true
    }

    fileprivate func step() {
        onFrame()
    }
}

private final class DisplayLinkTarget: NSObject {
    private weak var driver: DisplayLinkDriver?

    init(driver: DisplayLinkDriver) {
        self.driver = driver
    }

    @objc func step(_ link: CADisplayLink) {
        driver?.step()
    }
}
