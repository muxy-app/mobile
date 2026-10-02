import SwiftUI
import UIKit

struct FileTextEditor: UIViewRepresentable {
    @Binding var text: String
    let isEditable: Bool
    let wrapsLines: Bool
    let accessibilityLabel: String

    @Environment(\.appTheme) private var theme
    @ScaledMetric(relativeTo: .body) private var fontSize = 14

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text)
    }

    func makeUIView(context: Context) -> FileTextView {
        let view = FileTextView()
        view.isEditable = false
        view.delegate = context.coordinator
        view.autocapitalizationType = .none
        view.autocorrectionType = .no
        view.spellCheckingType = .no
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        view.keyboardDismissMode = .interactive
        view.textContainerInset = UIEdgeInsets(top: 16, left: 14, bottom: 16, right: 14)
        view.textContainer.lineFragmentPadding = 0
        view.alwaysBounceVertical = true
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        return view
    }

    func updateUIView(_ view: FileTextView, context: Context) {
        context.coordinator.text = $text
        let wasEditable = view.isEditable
        if view.text != text {
            let selection = view.selectedRange
            view.text = text
            let length = (text as NSString).length
            view.selectedRange = NSRange(location: min(selection.location, length), length: 0)
        }
        view.isEditable = isEditable
        view.isSelectable = true
        view.wrapsLines = wrapsLines
        view.accessibilityLabel = accessibilityLabel
        view.font = .monospacedSystemFont(ofSize: fontSize, weight: .regular)
        view.textColor = UIColor(theme.foreground)
        view.backgroundColor = UIColor(theme.background)
        view.tintColor = UIColor(theme.accent)
        view.keyboardAppearance = theme.isDark ? .dark : .light
        guard wasEditable != isEditable else { return }
        guard isEditable else {
            view.resignFirstResponder()
            return
        }
        DispatchQueue.main.async { [weak view] in
            guard let view, view.isEditable, view.window != nil else { return }
            view.becomeFirstResponder()
        }
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var text: Binding<String>

        init(text: Binding<String>) {
            self.text = text
        }

        func textViewDidChange(_ textView: UITextView) {
            text.wrappedValue = textView.text
        }
    }
}

final class FileTextView: UITextView {
    var wrapsLines = true {
        didSet {
            guard wrapsLines != oldValue else { return }
            updateTextContainer()
            if wrapsLines {
                setContentOffset(CGPoint(x: 0, y: contentOffset.y), animated: false)
            }
            setNeedsLayout()
        }
    }

    override func layoutSubviews() {
        updateTextContainer()
        super.layoutSubviews()
    }

    private func updateTextContainer() {
        let width = wrapsLines
            ? max(0, bounds.width - textContainerInset.left - textContainerInset.right)
            : CGFloat.greatestFiniteMagnitude
        let size = CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)
        guard textContainer.size != size || textContainer.widthTracksTextView != wrapsLines else { return }
        textContainer.widthTracksTextView = wrapsLines
        textContainer.size = size
    }
}
