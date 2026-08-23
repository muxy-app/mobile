import ExpoModulesCore

public final class MuxyTerminalInputModule: Module {
  public func definition() -> ModuleDefinition {
    Name("MuxyTerminalInput")

    View(MuxyTerminalInputView.self) {
      Events("onTextChange", "onFocus", "onBlur", "onHardwareInput")

      Prop("value") { (view, value: String) in
        view.setValue(value)
      }

      AsyncFunction("focus") { view in
        view.focus()
      }

      AsyncFunction("blur") { view in
        view.blur()
      }
    }
  }
}
