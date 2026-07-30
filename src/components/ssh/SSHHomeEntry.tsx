import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { getSSHSupport } from '@/ssh';
import { useTokens } from '@/theme';

export function SSHHomeEntry() {
  const tokens = useTokens();
  const { push } = useRouter();
  const support = getSSHSupport();

  if (support === 'hidden') return null;

  const disabled = support === 'disabled-expo-go';
  const subtitle = disabled
    ? 'Requires a development build'
    : 'Connect directly to saved servers';

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={() => push('/ssh')}
      style={({ pressed }) => [
        styles.row,
        {
          backgroundColor: tokens.surface.secondary,
          borderColor: tokens.border.subtle,
          opacity: disabled ? 0.55 : pressed ? 0.85 : 1,
        },
      ]}>
      <View
        style={[
          styles.icon,
          { backgroundColor: tokens.surface.tertiary },
        ]}>
        <Ionicons
          name="terminal-outline"
          size={20}
          color={tokens.text.primary}
        />
      </View>
      <View style={styles.body}>
        <Text style={[styles.title, { color: tokens.text.primary }]}>
          SSH Servers
        </Text>
        <Text style={[styles.subtitle, { color: tokens.text.muted }]}>
          {subtitle}
        </Text>
      </View>
      {disabled ? null : (
        <Ionicons
          name="chevron-forward"
          size={18}
          color={tokens.text.muted}
        />
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 14,
    borderRadius: 12,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
  },
  icon: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 10,
    borderCurve: 'continuous',
  },
  body: { flex: 1, gap: 2 },
  title: { fontSize: 16, fontWeight: '600' },
  subtitle: { fontSize: 13 },
});
