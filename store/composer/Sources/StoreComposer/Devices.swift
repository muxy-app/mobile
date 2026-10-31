import SwiftUI

enum DeviceKind {
    case iPhone
    case iPhoneDuo
    case pixelPhone
    case iPad
    case pixelTablet

    var screenSize: CGSize {
        switch self {
        case .iPhone: CGSize(width: 1320, height: 2868)
        case .iPhoneDuo: CGSize(width: 2853, height: 2007)
        case .pixelPhone: CGSize(width: 1080, height: 2424)
        case .iPad: CGSize(width: 2064, height: 2752)
        case .pixelTablet: CGSize(width: 1600, height: 2560)
        }
    }

    var style: DeviceStyle {
        switch self {
        case .iPhone:
            DeviceStyle(
                frame: 0.013,
                bezel: 0.028,
                screenRadius: 0.135,
                metal: [0.42, 0.16, 0.30, 0.12, 0.38],
                camera: .none,
                buttons: [
                    DeviceButton(edge: .leading, position: 0.165, length: 0.040),
                    DeviceButton(edge: .leading, position: 0.235, length: 0.070),
                    DeviceButton(edge: .leading, position: 0.325, length: 0.070),
                    DeviceButton(edge: .trailing, position: 0.265, length: 0.105),
                    DeviceButton(edge: .trailing, position: 0.545, length: 0.060)
                ]
            )
        case .iPhoneDuo:
            DeviceStyle(
                frame: 0.011,
                bezel: 0.024,
                screenRadius: 0.075,
                metal: [0.42, 0.16, 0.30, 0.12, 0.38],
                camera: .none,
                buttons: [
                    DeviceButton(edge: .top, position: 0.72, length: 0.085),
                    DeviceButton(edge: .trailing, position: 0.12, length: 0.070),
                    DeviceButton(edge: .trailing, position: 0.20, length: 0.070)
                ]
            )
        case .pixelPhone:
            DeviceStyle(
                frame: 0.012,
                bezel: 0.026,
                screenRadius: 0.10,
                metal: [0.30, 0.13, 0.22, 0.10, 0.27],
                camera: .punchHole(diameter: 0.040, centerY: 0.0405),
                buttons: [
                    DeviceButton(edge: .trailing, position: 0.20, length: 0.065),
                    DeviceButton(edge: .trailing, position: 0.30, length: 0.120)
                ]
            )
        case .iPad:
            DeviceStyle(
                frame: 0.007,
                bezel: 0.036,
                screenRadius: 0.030,
                metal: [0.38, 0.17, 0.28, 0.13, 0.33],
                camera: .sideDot(diameter: 0.011),
                buttons: [
                    DeviceButton(edge: .top, position: 0.80, length: 0.080),
                    DeviceButton(edge: .trailing, position: 0.08, length: 0.050),
                    DeviceButton(edge: .trailing, position: 0.15, length: 0.050)
                ]
            )
        case .pixelTablet:
            DeviceStyle(
                frame: 0.006,
                bezel: 0.065,
                screenRadius: 0.022,
                metal: [0.30, 0.19, 0.25, 0.16, 0.28],
                camera: .sideDot(diameter: 0.014),
                buttons: [
                    DeviceButton(edge: .top, position: 0.70, length: 0.080),
                    DeviceButton(edge: .trailing, position: 0.10, length: 0.070)
                ]
            )
        }
    }
}

struct DeviceButton {
    enum Edge {
        case leading
        case trailing
        case top
    }

    let edge: Edge
    let position: CGFloat
    let length: CGFloat
}

enum DeviceCamera {
    case none
    case punchHole(diameter: CGFloat, centerY: CGFloat)
    case sideDot(diameter: CGFloat)
}

struct DeviceStyle {
    let frame: CGFloat
    let bezel: CGFloat
    let screenRadius: CGFloat
    let metal: [Double]
    let camera: DeviceCamera
    let buttons: [DeviceButton]
}

struct DeviceGeometry {
    let kind: DeviceKind
    let screenWidth: CGFloat

    var screenHeight: CGFloat { screenWidth * kind.screenSize.height / kind.screenSize.width }
    var shortSide: CGFloat { min(screenWidth, screenHeight) }
    var frameWidth: CGFloat { kind.style.frame * shortSide }
    var bezelWidth: CGFloat { kind.style.bezel * shortSide }
    var inset: CGFloat { frameWidth + bezelWidth }
    var size: CGSize { CGSize(width: screenWidth + inset * 2, height: screenHeight + inset * 2) }
    var screenScale: CGFloat { screenWidth / kind.screenSize.width }
    var screenRadius: CGFloat { kind.style.screenRadius * shortSide }
    var innerRadius: CGFloat { screenRadius + bezelWidth }
    var outerRadius: CGFloat { innerRadius + frameWidth }

    static func fitting(_ kind: DeviceKind, in box: CGSize) -> DeviceGeometry {
        let unit = DeviceGeometry(kind: kind, screenWidth: 1).size
        return DeviceGeometry(kind: kind, screenWidth: min(box.width / unit.width, box.height / unit.height))
    }

    func scaled(_ factor: CGFloat) -> DeviceGeometry {
        DeviceGeometry(kind: kind, screenWidth: screenWidth * factor)
    }
}

struct DeviceFrame<Screen: View>: View {
    let geometry: DeviceGeometry
    let screen: Screen

    private var style: DeviceStyle { geometry.kind.style }
    private var size: CGSize { geometry.size }
    private var buttonThickness: CGFloat { max(6, min(size.width, size.height) * 0.008) }

    var body: some View {
        ZStack {
            ForEach(Array(style.buttons.enumerated()), id: \.offset) { _, button in
                buttonView(button)
            }
            RoundedRectangle(cornerRadius: geometry.outerRadius, style: .continuous)
                .fill(LinearGradient(colors: style.metal.map { Color(white: $0) }, startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: size.width, height: size.height)
            RoundedRectangle(cornerRadius: geometry.outerRadius, style: .continuous)
                .strokeBorder(Color.white.opacity(0.22), lineWidth: max(1.5, min(size.width, size.height) * 0.002))
                .frame(width: size.width, height: size.height)
            RoundedRectangle(cornerRadius: geometry.innerRadius, style: .continuous)
                .fill(Color.black)
                .frame(width: size.width - geometry.frameWidth * 2, height: size.height - geometry.frameWidth * 2)
            screen
                .frame(width: geometry.kind.screenSize.width, height: geometry.kind.screenSize.height)
                .scaleEffect(geometry.screenScale)
                .frame(width: geometry.screenWidth, height: geometry.screenHeight)
                .clipShape(RoundedRectangle(cornerRadius: geometry.screenRadius, style: .continuous))
            camera
        }
        .frame(width: size.width, height: size.height)
    }

    @ViewBuilder
    private var camera: some View {
        switch style.camera {
        case .none:
            EmptyView()
        case let .punchHole(diameter, centerY):
            Circle()
                .fill(Color.black)
                .overlay(Circle().strokeBorder(Color(white: 0.18), lineWidth: 1))
                .frame(width: diameter * geometry.screenWidth, height: diameter * geometry.screenWidth)
                .position(x: size.width / 2, y: geometry.inset + centerY * geometry.screenHeight)
        case let .sideDot(diameter):
            Circle()
                .fill(RadialGradient(colors: [Color(white: 0.22), Color(white: 0.04)], center: .center, startRadius: 0, endRadius: diameter * geometry.shortSide / 2))
                .frame(width: diameter * geometry.shortSide, height: diameter * geometry.shortSide)
                .position(x: geometry.frameWidth + geometry.bezelWidth / 2, y: size.height / 2)
        }
    }

    private func buttonView(_ button: DeviceButton) -> some View {
        let metal = LinearGradient(colors: [Color(white: 0.45), Color(white: 0.18)], startPoint: .leading, endPoint: .trailing)
        let thickness = buttonThickness
        switch button.edge {
        case .leading:
            return RoundedRectangle(cornerRadius: thickness / 2, style: .continuous)
                .fill(metal)
                .frame(width: thickness, height: button.length * size.height)
                .position(x: -thickness * 0.1, y: (button.position + button.length / 2) * size.height)
        case .trailing:
            return RoundedRectangle(cornerRadius: thickness / 2, style: .continuous)
                .fill(metal)
                .frame(width: thickness, height: button.length * size.height)
                .position(x: size.width + thickness * 0.1, y: (button.position + button.length / 2) * size.height)
        case .top:
            return RoundedRectangle(cornerRadius: thickness / 2, style: .continuous)
                .fill(metal)
                .frame(width: button.length * size.width, height: thickness)
                .position(x: (button.position + button.length / 2) * size.width, y: -thickness * 0.1)
        }
    }
}
