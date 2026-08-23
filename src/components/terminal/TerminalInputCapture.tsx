import { forwardRef, useImperativeHandle, useMemo, useRef } from 'react';
import { StyleSheet, TextInput } from 'react-native';

import type { TerminalInputHandle, TerminalInputProps } from './TerminalInputCapture.types';

export const TerminalInput = forwardRef<TerminalInputHandle, TerminalInputProps>(
  function TerminalInput({ value, onChangeText, onFocus, onBlur }, ref) {
    const inputRef = useRef<TextInput>(null);
    const selection = useMemo(() => ({ start: value.length, end: value.length }), [value]);
    useImperativeHandle(ref, () => ({
      focus: () => inputRef.current?.focus(),
      blur: () => inputRef.current?.blur(),
    }));

    return (
      <TextInput
        ref={inputRef}
        value={value}
        selection={selection}
        onChangeText={onChangeText}
        onFocus={onFocus}
        onBlur={onBlur}
        multiline
        autoCorrect={false}
        autoCapitalize="none"
        autoComplete="off"
        spellCheck={false}
        caretHidden
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
