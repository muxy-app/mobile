import { useEffect, useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { useTokens } from '@/theme';

import { validateEntryName } from './fileManager';

type Props = {
  visible: boolean;
  title: string;
  actionLabel: string;
  initialValue?: string;
  onCancel: () => void;
  onSubmit: (name: string) => void;
};

export function NamePrompt({
  visible,
  title,
  actionLabel,
  initialValue = '',
  onCancel,
  onSubmit,
}: Props) {
  const tokens = useTokens();
  const inputRef = useRef<TextInput>(null);
  const valueRef = useRef(initialValue);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;
    valueRef.current = initialValue;
    setError(null);
    const timer = setTimeout(() => inputRef.current?.focus(), 120);
    return () => clearTimeout(timer);
  }, [visible, initialValue]);

  const submit = () => {
    const nextError = validateEntryName(valueRef.current);
    if (nextError) {
      setError(nextError);
      return;
    }
    onSubmit(valueRef.current.trim());
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onCancel}
      statusBarTranslucent>
      <KeyboardAvoidingView
        style={styles.backdrop}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onCancel} />
        <View
          style={[
            styles.dialog,
            { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle },
          ]}>
          <Text style={[styles.title, { color: tokens.text.primary }]}>{title}</Text>
          <TextInput
            key={visible ? `${title}:${initialValue}` : 'hidden'}
            ref={inputRef}
            defaultValue={initialValue}
            onChangeText={(value) => {
              valueRef.current = value;
              if (error) setError(null);
            }}
            onSubmitEditing={submit}
            returnKeyType="done"
            selectTextOnFocus
            autoCapitalize="none"
            autoCorrect={false}
            accessibilityLabel={title}
            style={[
              styles.input,
              {
                color: tokens.text.primary,
                backgroundColor: tokens.surface.primary,
                borderColor: error ? tokens.status.danger : tokens.border.strong,
              },
            ]}
          />
          {error ? <Text style={[styles.error, { color: tokens.status.danger }]}>{error}</Text> : null}
          <View style={styles.actions}>
            <Pressable
              accessibilityRole="button"
              onPress={onCancel}
              style={({ pressed }) => [styles.button, { opacity: pressed ? 0.6 : 1 }]}>
              <Text style={[styles.cancelLabel, { color: tokens.text.secondary }]}>Cancel</Text>
            </Pressable>
            <Pressable
              accessibilityRole="button"
              onPress={submit}
              style={({ pressed }) => [
                styles.button,
                styles.primaryButton,
                { backgroundColor: tokens.accent.primary, opacity: pressed ? 0.8 : 1 },
              ]}>
              <Text style={[styles.primaryLabel, { color: tokens.accent.contrast }]}>
                {actionLabel}
              </Text>
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 28,
    backgroundColor: 'rgba(0,0,0,0.48)',
  },
  dialog: {
    width: '100%',
    maxWidth: 360,
    gap: 12,
    padding: 18,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 18,
    borderCurve: 'continuous',
  },
  title: { fontSize: 17, fontWeight: '600' },
  input: {
    minHeight: 44,
    paddingHorizontal: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 10,
    borderCurve: 'continuous',
    fontSize: 15,
  },
  error: { marginTop: -5, fontSize: 12 },
  actions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8 },
  button: {
    minWidth: 74,
    minHeight: 40,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
    borderRadius: 10,
    borderCurve: 'continuous',
  },
  primaryButton: { minWidth: 82 },
  cancelLabel: { fontSize: 14, fontWeight: '500' },
  primaryLabel: { fontSize: 14, fontWeight: '600' },
});
