import SwiftUI

nonisolated enum RowPosition: Equatable {
    case single
    case first
    case middle
    case last

    static func of(index: Int, count: Int) -> RowPosition {
        if count == 1 { return .single }
        if index == 0 { return .first }
        if index == count - 1 { return .last }
        return .middle
    }

    var topRadius: CGFloat {
        self == .single || self == .first ? SurfaceCorner.card : SurfaceCorner.segment
    }

    var bottomRadius: CGFloat {
        self == .single || self == .last ? SurfaceCorner.card : SurfaceCorner.segment
    }

    var shape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(
            topLeadingRadius: topRadius,
            bottomLeadingRadius: bottomRadius,
            bottomTrailingRadius: bottomRadius,
            topTrailingRadius: topRadius,
            style: .continuous
        )
    }
}
