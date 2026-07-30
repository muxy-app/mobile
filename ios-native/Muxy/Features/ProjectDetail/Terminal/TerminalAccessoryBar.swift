import Combine
import SwiftUI
import UIKit

@MainActor
final class TerminalAccessoryModel: ObservableObject {
    @Published var modifierArmed: Bool = false
    @Published var activeModifier: TerminalModifier = .ctrl
    @Published var keyboardVisible: Bool = true
    @Published var canCopySelection: Bool = false
    @Published var foreground: SwiftUI.Color = .primary

    var onKey: ((String) -> Void)?
    var onModifierToggle: ((Bool) -> Void)?
    var onModifierChange: ((TerminalModifier) -> Void)?
    var onKeyboardToggle: (() -> Void)?
    var onPaste: (() -> Void)?
    var onCopy: (() -> Void)?

    func setModifierArmed(_ armed: Bool) {
        guard modifierArmed != armed else { return }
        modifierArmed = armed
        onModifierToggle?(armed)
    }

    func toggleModifier() {
        setModifierArmed(!modifierArmed)
    }

    func selectModifier(_ modifier: TerminalModifier) {
        guard activeModifier != modifier else { return }
        activeModifier = modifier
        onModifierChange?(modifier)
        if modifierArmed { setModifierArmed(false) }
    }

    func syncModifierArmed(_ armed: Bool) {
        guard modifierArmed != armed else { return }
        modifierArmed = armed
    }

    func syncActiveModifier(_ modifier: TerminalModifier) {
        guard activeModifier != modifier else { return }
        activeModifier = modifier
    }
}

final class TerminalAccessoryBar: UIInputView {
    var onKey: ((String) -> Void)? {
        get { model.onKey }
        set { model.onKey = newValue }
    }

    var onModifierToggle: ((Bool) -> Void)? {
        get { model.onModifierToggle }
        set { model.onModifierToggle = newValue }
    }

    var onModifierChange: ((TerminalModifier) -> Void)? {
        get { model.onModifierChange }
        set { model.onModifierChange = newValue }
    }

    var onKeyboardToggle: (() -> Void)? {
        get { model.onKeyboardToggle }
        set { model.onKeyboardToggle = newValue }
    }

    var onPaste: (() -> Void)? {
        get { model.onPaste }
        set { model.onPaste = newValue }
    }

    var onCopy: (() -> Void)? {
        get { model.onCopy }
        set { model.onCopy = newValue }
    }

    var modifierArmed: Bool { model.modifierArmed }
    var activeModifier: TerminalModifier { model.activeModifier }
    var canCopySelection: Bool { model.canCopySelection }

    func setKeyboardVisible(_ visible: Bool) {
        model.keyboardVisible = visible
    }

    func setCanCopySelection(_ enabled: Bool) {
        model.canCopySelection = enabled
    }

    func applyTheme(background: UIColor, foreground: UIColor) {
        backgroundColor = background
        backdrop.backgroundColor = background
        model.foreground = SwiftUI.Color(foreground)
    }

    func syncModifierArmed(_ armed: Bool) {
        model.syncModifierArmed(armed)
    }

    func syncActiveModifier(_ modifier: TerminalModifier) {
        model.syncActiveModifier(modifier)
    }

    private let model = TerminalAccessoryModel()
    private let hostingController: UIHostingController<TerminalAccessoryView>
    private let backdrop = UIView()

    init() {
        hostingController = UIHostingController(rootView: TerminalAccessoryView(model: model))
        super.init(
            frame: CGRect(x: 0, y: 0, width: 0, height: 72),
            inputViewStyle: .default
        )
        autoresizingMask = [.flexibleWidth]
        allowsSelfSizing = true
        setupHostingView()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupHostingView() {
        backdrop.frame = bounds
        backdrop.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        backdrop.isUserInteractionEnabled = false
        addSubview(backdrop)

        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        hostingController.view.backgroundColor = .clear
        hostingController.sizingOptions = .preferredContentSize
        addSubview(hostingController.view)
        NSLayoutConstraint.activate([
            hostingController.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            hostingController.view.topAnchor.constraint(equalTo: topAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: bottomAnchor),
            heightAnchor.constraint(equalToConstant: 72),
        ])
    }
}

struct TerminalAccessoryView: View {
    @ObservedObject var model: TerminalAccessoryModel

    private var fg: SwiftUI.Color { model.foreground }

    var body: some View {
        HStack(spacing: 10) {
            keyPill
            Spacer(minLength: 6)
            keyboardButton
            DPadControl(tint: fg) { payload in
                model.onKey?(payload)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }

    private var keyPill: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                key("esc", payload: "\u{1B}")
                modifierKey
                key("tab", payload: "\t")
                actionIcon("doc.on.clipboard", label: "Paste", action: { model.onPaste?() })
                actionIcon("doc.on.doc", label: "Copy", enabled: model.canCopySelection, action: { model.onCopy?() })
                key("~", payload: "~")
                key("|", payload: "|")
                key("/", payload: "/")
                key("-", payload: "-")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
        .frame(height: 44)
        .glassEffect(.regular, in: Capsule())
    }

    private func key(_ title: String, payload: String) -> some View {
        Button {
            model.onKey?(payload)
        } label: {
            Text(title)
                .font(.system(size: 14, weight: .medium, design: .monospaced))
                .foregroundStyle(fg)
                .frame(minWidth: 32)
                .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }

    private func actionIcon(
        _ systemName: String,
        label: String,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(fg)
                .frame(width: 32, height: 28)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
        .accessibilityLabel(label)
    }

    private var modifierKey: some View {
        ModifierKeyButton(
            active: model.activeModifier,
            armed: model.modifierArmed,
            fg: fg,
            onTap: { model.toggleModifier() },
            onSelect: { model.selectModifier($0) }
        )
    }

    private var keyboardButton: some View {
        Button {
            model.onKeyboardToggle?()
        } label: {
            Image(systemName: model.keyboardVisible ? "keyboard.chevron.compact.down" : "keyboard")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(fg)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: Circle())
    }
}

struct ModifierKeyButton: UIViewRepresentable {
    let active: TerminalModifier
    let armed: Bool
    let fg: SwiftUI.Color
    let onTap: () -> Void
    let onSelect: (TerminalModifier) -> Void

    func makeUIView(context _: Context) -> ModifierKeyHostView {
        let view = ModifierKeyHostView()
        view.configure(
            active: active,
            armed: armed,
            fg: UIColor(fg),
            onTap: onTap,
            onSelect: onSelect
        )
        return view
    }

    func updateUIView(_ uiView: ModifierKeyHostView, context _: Context) {
        uiView.configure(
            active: active,
            armed: armed,
            fg: UIColor(fg),
            onTap: onTap,
            onSelect: onSelect
        )
    }
}

final class ModifierKeyHostView: UIView {
    private let label = UILabel()
    private let chevron = UIImageView()
    private let stack = UIStackView()
    private let background = UIView()

    private var activeModifier: TerminalModifier = .ctrl
    private var armed: Bool = false
    private var fgColor: UIColor = .white
    private var onTap: (() -> Void)?
    private var onSelect: ((TerminalModifier) -> Void)?

    private var pickerView: ModifierPickerView?
    private var didCommitSelection = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupViews()
        setupGestures()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupViews() {
        setContentHuggingPriority(.required, for: .horizontal)
        setContentHuggingPriority(.required, for: .vertical)
        setContentCompressionResistancePriority(.required, for: .horizontal)
        setContentCompressionResistancePriority(.required, for: .vertical)

        background.translatesAutoresizingMaskIntoConstraints = false
        background.isUserInteractionEnabled = false
        background.layer.cornerCurve = .continuous
        addSubview(background)

        label.font = UIFont.monospacedSystemFont(ofSize: 14, weight: .medium)
        chevron.image = UIImage(systemName: "chevron.up", withConfiguration: UIImage.SymbolConfiguration(pointSize: 9, weight: .semibold))
        chevron.contentMode = .scaleAspectFit
        chevron.alpha = 0.6

        stack.axis = .horizontal
        stack.spacing = 4
        stack.alignment = .center
        stack.isUserInteractionEnabled = false
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.addArrangedSubview(label)
        stack.addArrangedSubview(chevron)
        addSubview(stack)

        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: centerYAnchor),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            background.topAnchor.constraint(equalTo: topAnchor),
            background.bottomAnchor.constraint(equalTo: bottomAnchor),
            background.leadingAnchor.constraint(equalTo: leadingAnchor),
            background.trailingAnchor.constraint(equalTo: trailingAnchor),
        ])
    }

    override var intrinsicContentSize: CGSize {
        let stackSize = stack.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        let width = max(stackSize.width + 16, 52)
        let height = max(stackSize.height + 8, 32)
        return CGSize(width: width, height: height)
    }

    private func setupGestures() {
        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
        longPress.minimumPressDuration = 0.3
        longPress.allowableMovement = .greatestFiniteMagnitude
        addGestureRecognizer(longPress)

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap))
        tap.require(toFail: longPress)
        addGestureRecognizer(tap)
    }

    func configure(
        active: TerminalModifier,
        armed: Bool,
        fg: UIColor,
        onTap: @escaping () -> Void,
        onSelect: @escaping (TerminalModifier) -> Void
    ) {
        activeModifier = active
        self.armed = armed
        fgColor = fg
        self.onTap = onTap
        self.onSelect = onSelect
        refreshAppearance()
    }

    private func refreshAppearance() {
        label.text = activeModifier.title
        let fill = fgColor.resolvedColor(with: traitCollection)
        let textColor = armed ? contrastingColor(for: fill) : fill
        label.textColor = textColor
        chevron.tintColor = textColor
        background.backgroundColor = armed ? fill : .clear
        background.layer.cornerRadius = background.bounds.height / 2
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func contrastingColor(for color: UIColor) -> UIColor {
        var white: CGFloat = 0
        color.getWhite(&white, alpha: nil)
        return white > 0.5 ? .black : .white
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard traitCollection.hasDifferentColorAppearance(comparedTo: previousTraitCollection) else { return }
        refreshAppearance()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        background.layer.cornerRadius = background.bounds.height / 2
    }

    @objc
    private func handleTap() {
        onTap?()
    }

    @objc
    private func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        switch gesture.state {
        case .began:
            presentPicker()
        case .changed:
            guard let pickerView else { return }
            let location = gesture.location(in: pickerView)
            pickerView.updateHover(at: location)
        case .ended:
            commitSelectionIfNeeded()
            dismissPicker()
        case .cancelled,
             .failed:
            dismissPicker()
        default:
            break
        }
    }

    private func presentPicker() {
        guard pickerView == nil,
              let window
        else { return }

        didCommitSelection = false
        let picker = ModifierPickerView(active: activeModifier, fg: fgColor)
        picker.translatesAutoresizingMaskIntoConstraints = false
        window.addSubview(picker)

        let buttonFrame = convert(bounds, to: window)
        let pickerSize = picker.intrinsicContentSize
        var originX = buttonFrame.midX - pickerSize.width / 2
        let minX: CGFloat = 8
        let maxX = window.bounds.width - pickerSize.width - 8
        originX = min(max(originX, minX), maxX)
        let originY = buttonFrame.minY - pickerSize.height - 8

        NSLayoutConstraint.activate([
            picker.leadingAnchor.constraint(equalTo: window.leadingAnchor, constant: originX),
            picker.topAnchor.constraint(equalTo: window.topAnchor, constant: originY),
            picker.widthAnchor.constraint(equalToConstant: pickerSize.width),
            picker.heightAnchor.constraint(equalToConstant: pickerSize.height),
        ])

        picker.alpha = 0
        picker.transform = CGAffineTransform(scaleX: 0.9, y: 0.9)
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut]) {
            picker.alpha = 1
            picker.transform = .identity
        }

        pickerView = picker
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func commitSelectionIfNeeded() {
        guard let pickerView,
              let selection = pickerView.currentHoveredModifier,
              selection != activeModifier
        else { return }
        didCommitSelection = true
        onSelect?(selection)
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private func dismissPicker() {
        guard let picker = pickerView else { return }
        pickerView = nil
        UIView.animate(
            withDuration: 0.15,
            delay: 0,
            options: [.curveEaseIn],
            animations: {
                picker.alpha = 0
                picker.transform = CGAffineTransform(scaleX: 0.9, y: 0.9)
            },
            completion: { _ in picker.removeFromSuperview() }
        )
    }
}

final class ModifierPickerView: UIView {
    private let rowHeight: CGFloat = 44
    private let horizontalPadding: CGFloat = 16
    private let verticalPadding: CGFloat = 6
    private let pickerWidth: CGFloat = 180
    private let arrowHeight: CGFloat = 8

    private let active: TerminalModifier
    private let fgColor: UIColor
    private let containerView = UIView()
    private var rowViews: [ModifierPickerRow] = []
    private(set) var currentHoveredModifier: TerminalModifier?

    init(active: TerminalModifier, fg: UIColor) {
        self.active = active
        fgColor = fg
        super.init(frame: .zero)
        backgroundColor = .clear
        isOpaque = false
        setupViews()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var intrinsicContentSize: CGSize {
        let rows = CGFloat(TerminalModifier.allCases.count)
        let height = rows * rowHeight + verticalPadding * 2 + arrowHeight
        return CGSize(width: pickerWidth, height: height)
    }

    private func setupViews() {
        containerView.translatesAutoresizingMaskIntoConstraints = false
        containerView.backgroundColor = UIColor.black.withAlphaComponent(0.7)
        containerView.layer.cornerRadius = 18
        containerView.layer.cornerCurve = .continuous
        containerView.layer.borderWidth = 0.5
        containerView.layer.borderColor = fgColor.withAlphaComponent(0.12).cgColor
        addSubview(containerView)

        let blur = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
        blur.translatesAutoresizingMaskIntoConstraints = false
        blur.layer.cornerRadius = 18
        blur.layer.cornerCurve = .continuous
        blur.clipsToBounds = true
        containerView.insertSubview(blur, at: 0)

        let rows = TerminalModifier.allCases
        var previousAnchor: NSLayoutYAxisAnchor = containerView.topAnchor
        var topInset: CGFloat = verticalPadding
        for (index, modifier) in rows.enumerated() {
            let row = ModifierPickerRow(modifier: modifier, fg: fgColor, disabled: modifier == active)
            row.translatesAutoresizingMaskIntoConstraints = false
            containerView.addSubview(row)
            rowViews.append(row)

            NSLayoutConstraint.activate([
                row.topAnchor.constraint(equalTo: previousAnchor, constant: topInset),
                row.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
                row.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
                row.heightAnchor.constraint(equalToConstant: rowHeight),
            ])

            if index < rows.count - 1 {
                let divider = UIView()
                divider.translatesAutoresizingMaskIntoConstraints = false
                divider.backgroundColor = fgColor.withAlphaComponent(0.08)
                containerView.addSubview(divider)
                NSLayoutConstraint.activate([
                    divider.topAnchor.constraint(equalTo: row.bottomAnchor),
                    divider.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: horizontalPadding),
                    divider.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -horizontalPadding),
                    divider.heightAnchor.constraint(equalToConstant: 0.5),
                ])
            }

            previousAnchor = row.bottomAnchor
            topInset = 0
        }

        NSLayoutConstraint.activate([
            containerView.topAnchor.constraint(equalTo: topAnchor),
            containerView.leadingAnchor.constraint(equalTo: leadingAnchor),
            containerView.trailingAnchor.constraint(equalTo: trailingAnchor),
            containerView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -arrowHeight),
            blur.topAnchor.constraint(equalTo: containerView.topAnchor),
            blur.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            blur.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
            blur.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
        ])
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        let arrowWidth: CGFloat = 18
        let midX = rect.midX
        let topY = rect.maxY - arrowHeight
        ctx.beginPath()
        ctx.move(to: CGPoint(x: midX - arrowWidth / 2, y: topY))
        ctx.addLine(to: CGPoint(x: midX + arrowWidth / 2, y: topY))
        ctx.addLine(to: CGPoint(x: midX, y: rect.maxY))
        ctx.closePath()
        ctx.setFillColor(UIColor.black.withAlphaComponent(0.7).cgColor)
        ctx.fillPath()
    }

    func updateHover(at location: CGPoint) {
        var hovered: TerminalModifier?
        for row in rowViews {
            let frameInSelf = row.convert(row.bounds, to: self)
            if frameInSelf.contains(location), !row.isDisabled {
                hovered = row.modifier
                row.setHighlighted(true)
            } else {
                row.setHighlighted(false)
            }
        }
        currentHoveredModifier = hovered
    }
}

final class ModifierPickerRow: UIView {
    let modifier: TerminalModifier
    let isDisabled: Bool
    private let glyphLabel = UILabel()
    private let titleLabel = UILabel()
    private let highlight = UIView()

    init(modifier: TerminalModifier, fg: UIColor, disabled: Bool) {
        self.modifier = modifier
        isDisabled = disabled
        super.init(frame: .zero)

        highlight.translatesAutoresizingMaskIntoConstraints = false
        highlight.backgroundColor = fg.withAlphaComponent(0.18)
        highlight.alpha = 0
        highlight.isUserInteractionEnabled = false
        addSubview(highlight)

        glyphLabel.font = UIFont.systemFont(ofSize: 15, weight: .semibold)
        glyphLabel.textAlignment = .center
        glyphLabel.text = modifier.glyph
        glyphLabel.textColor = disabled ? fg.withAlphaComponent(0.4) : fg
        glyphLabel.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = UIFont.monospacedSystemFont(ofSize: 14, weight: .medium)
        titleLabel.text = modifier.displayName.lowercased()
        titleLabel.textColor = disabled ? fg.withAlphaComponent(0.4) : fg
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        addSubview(glyphLabel)
        addSubview(titleLabel)

        NSLayoutConstraint.activate([
            highlight.topAnchor.constraint(equalTo: topAnchor),
            highlight.bottomAnchor.constraint(equalTo: bottomAnchor),
            highlight.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            highlight.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            glyphLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            glyphLabel.centerYAnchor.constraint(equalTo: centerYAnchor),
            glyphLabel.widthAnchor.constraint(equalToConstant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: glyphLabel.trailingAnchor, constant: 14),
            titleLabel.centerYAnchor.constraint(equalTo: centerYAnchor),
        ])
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        highlight.layer.cornerRadius = 10
        highlight.layer.cornerCurve = .continuous
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func setHighlighted(_ active: Bool) {
        let target: CGFloat = active && !isDisabled ? 1 : 0
        guard highlight.alpha != target else { return }
        UIView.animate(withDuration: 0.08) { self.highlight.alpha = target }
    }
}
