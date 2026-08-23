import { requireNativeViewManager } from 'expo-modules-core';
import { forwardRef, type Ref } from 'react';
import { type NativeSyntheticEvent, StyleSheet } from 'react-native';

import type { TerminalInputHandle, TerminalInputProps } from './TerminalInputCapture.types';

type TextEvent = NativeSyntheticEvent<{ text: string }>;
type HardwareInputEvent = NativeSyntheticEvent<{ base64: string }>;
type NativeTerminalInputProps = {
  ref?: Ref<TerminalInputHandle>;
  value: string;
  onTextChange: (event: TextEvent) => void;
  onFocus: () => void;
  onBlur: () => void;
  onHardwareInput: (event: HardwareInputEvent) => void;
  style: object;
};

const NativeTerminalInput =
  requireNativeViewManager<NativeTerminalInputProps>('MuxyTerminalInput');

export const TerminalInput = forwardRef<TerminalInputHandle, TerminalInputProps>(
  function TerminalInput({ value, onChangeText, onFocus, onBlur, onHardwareInput }, ref) {
    return (
      <NativeTerminalInput
        ref={ref}
        value={value}
        onTextChange={(event) => onChangeText(event.nativeEvent.text)}
        onFocus={onFocus}
        onBlur={onBlur}
        onHardwareInput={(event) => onHardwareInput(event.nativeEvent.base64)}
        style={styles.hiddenInput}
      />
    );
  },
);

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
