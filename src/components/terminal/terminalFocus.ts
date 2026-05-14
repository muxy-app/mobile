type FocusableInput = {
  focus: () => void;
};

export function scheduleTerminalInputFocus(
  input: FocusableInput | null,
  delayMs = 50,
): () => void {
  if (!input) return () => {};
  const timeout = setTimeout(() => input.focus(), delayMs);
  return () => clearTimeout(timeout);
}
