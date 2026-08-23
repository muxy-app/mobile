export type TerminalInputHandle = {
  focus: () => void;
  blur: () => void;
};

export type TerminalInputProps = {
  value: string;
  onChangeText: (text: string) => void;
  onFocus: () => void;
  onBlur: () => void;
  onHardwareInput: (base64: string) => void;
};
