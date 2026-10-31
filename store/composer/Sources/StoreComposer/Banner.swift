import AppKit
import SwiftUI

enum BannerKind: String, CaseIterable {
    case header = "app-store-header"
    case searchResults = "app-store-search"

    var canvas: CGSize {
        switch self {
        case .header: CGSize(width: 3840, height: 1646)
        case .searchResults: CGSize(width: 3840, height: 2560)
        }
    }

    var layout: BannerLayout {
        switch self {
        case .header:
            BannerLayout(headlineTop: 0.085, titleSize: 0.082, deviceTop: 0.27, tabletWidth: 0.62, phoneWidth: 0.34, phoneOverlap: 0.0)
        case .searchResults:
            BannerLayout(headlineTop: 0.075, titleSize: 0.068, deviceTop: 0.24, tabletWidth: 0.66, phoneWidth: 0.36, phoneOverlap: 0.28)
        }
    }
}

struct BannerLayout {
    let headlineTop: CGFloat
    let titleSize: CGFloat
    let deviceTop: CGFloat
    let tabletWidth: CGFloat
    let phoneWidth: CGFloat
    let phoneOverlap: CGFloat
}

struct BannerShots {
    let tablet: NSImage
    let leadingPhone: NSImage
    let trailingPhone: NSImage
}

struct Banner: View {
    let kind: BannerKind
    let shots: BannerShots

    private var canvas: CGSize { kind.canvas }
    private var layout: BannerLayout { kind.layout }
    private var unit: CGFloat { canvas.height }

    var body: some View {
        let tablet = DeviceGeometry(kind: .iPad, screenWidth: layout.tabletWidth * unit)
        let phone = DeviceGeometry(kind: .iPhone, screenWidth: layout.phoneWidth * unit)
        let tabletTop = layout.deviceTop * unit
        let phoneTop = tabletTop + tablet.size.height * 0.16
        let phoneSpread = tablet.size.width / 2 + phone.size.width * (0.5 - layout.phoneOverlap)
        ZStack(alignment: .topLeading) {
            Backdrop(canvas: canvas, index: 0)
            headline
                .frame(width: canvas.width)
                .offset(y: layout.headlineTop * unit)
            glow(size: CGSize(width: tablet.size.width * 1.6, height: tablet.size.height * 0.8))
                .offset(x: (canvas.width - tablet.size.width * 1.6) / 2, y: tabletTop + tablet.size.height * 0.1)
            device(shots.tablet, geometry: tablet)
                .offset(x: (canvas.width - tablet.size.width) / 2, y: tabletTop)
            device(shots.leadingPhone, geometry: phone)
                .offset(x: canvas.width / 2 - phoneSpread - phone.size.width / 2, y: phoneTop)
            device(shots.trailingPhone, geometry: phone)
                .offset(x: canvas.width / 2 + phoneSpread - phone.size.width / 2, y: phoneTop)
        }
        .frame(width: canvas.width, height: canvas.height, alignment: .topLeading)
        .clipped()
    }

    private var headline: some View {
        let size = layout.titleSize * unit
        return HStack(spacing: size * 0.28) {
            Text("Your Mac terminal.").foregroundStyle(Brand.text)
            Text("In your pocket.").foregroundStyle(Brand.gradient)
        }
        .font(.system(size: size, weight: .bold))
        .tracking(-size * 0.02)
        .lineLimit(1)
    }

    private func device(_ image: NSImage, geometry: DeviceGeometry) -> some View {
        DeviceFrame(geometry: geometry, screen: Image(nsImage: image).resizable().interpolation(.high))
            .shadow(color: .black.opacity(0.6), radius: geometry.size.width * 0.07, x: 0, y: geometry.size.width * 0.04)
    }

    private func glow(size: CGSize) -> some View {
        Ellipse()
            .fill(Brand.glow)
            .frame(width: size.width, height: size.height)
            .blur(radius: size.height * 0.25)
            .opacity(0.35)
    }
}
