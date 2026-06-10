import SwiftUI
import UIKit

extension View {
    func themedWindowBackground(_ color: Color) -> some View {
        background(WindowBackgroundApplier(color: color))
    }
}

private struct WindowBackgroundApplier: UIViewRepresentable {
    let color: Color

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        let resolved = UIColor(color)
        DispatchQueue.main.async {
            guard let window = view.window else { return }
            window.backgroundColor = resolved
            window.rootViewController?.view.backgroundColor = resolved
        }
    }
}
