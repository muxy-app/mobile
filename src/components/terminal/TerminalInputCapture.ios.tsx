import { requireNativeViewManager, requireOptionalNativeModule } from 'expo-modules-core';
import { forwardRef, useImperativeHandle, useRef, type Ref } from 'react';
import { type NativeSyntheticEvent, StyleSheet } from 'react-native';

import type { TerminalInputHandle, TerminalInputProps } from './TerminalInputCapture.types';
import { TerminalTextInput } from './TerminalTextInput';

const NATIVE_MODULE_NAME = 'MuxyTerminalInput';

type TextEvent = NativeSyntheticEvent<{ text: string }>;
type HardwareInputEvent = NativeSyntheticEvent<{ base64: string }>;

type NativeTerminalInputCommands = {
  focus: () => Promise<void>;
  blur: () => Promise<void>;
};

type NativeTerminalInputProps = {
  ref?: Ref<NativeTerminalInputCommands>;
  value: string;
  onTextChange: (event: TextEvent) => void;
  onFocus: () => void;
  onBlur: () => void;
  onHardwareInput: (event: HardwareInputEvent) => void;
  style: object;
};

function reportFailure(command: string, result: Promise<void> | undefined) {
  result?.catch((error: unknown) => {
    console.warn(`[terminal-input] ${command} failed: ${String(error)}`);
  });
}

function createNativeTerminalInput() {
  const NativeTerminalInput =
    requireNativeViewManager<NativeTerminalInputProps>(NATIVE_MODULE_NAME);

  return forwardRef<TerminalInputHandle, TerminalInputProps>(function TerminalInput(
    { value, onChangeText, onFocus, onBlur, onHardwareInput },
    ref,
  ) {
    const nativeRef = useRef<NativeTerminalInputCommands>(null);

    useImperativeHandle(ref, () => ({
      focus: () => reportFailure('focus', nativeRef.current?.focus()),
      blur: () => reportFailure('blur', nativeRef.current?.blur()),
    }));

    return (
      <NativeTerminalInput
        ref={nativeRef}
        value={value}
        onTextChange={(event) => onChangeText(event.nativeEvent.text)}
        onFocus={onFocus}
        onBlur={onBlur}
        onHardwareInput={(event) => onHardwareInput(event.nativeEvent.base64)}
        style={styles.hiddenInput}
      />
    );
  });
}

export function resolveTerminalInput() {
  if (requireOptionalNativeModule(NATIVE_MODULE_NAME)) return createNativeTerminalInput();
  console.warn(
    `[terminal-input] ${NATIVE_MODULE_NAME} is unavailable in this build, falling back to the managed text input. External keyboard shortcuts are disabled until you run a development build.`,
  );
  return TerminalTextInput;
}

export const TerminalInput = resolveTerminalInput();

const styles = StyleSheet.create({
  hiddenInput: {
    position: 'absolute',
    width: 1,
    height: 1,
    opacity: 0,
    top: 0,
    left: 0,
  },
});
