import { Stack, useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import { HeaderIconButton } from '@/components/HeaderIconButton';
import { useSettingsStore } from '@/state';
import { useTheme, type ThemeSource } from '@/theme';

const SOURCES: ThemeSource[] = ['device', 'system', 'light', 'dark'];

export default function SettingsScreen() {
  const { tokens, source, setSource, mode, isDeviceTheme } = useTheme();
  const router = useRouter();
  const useNerdFont = useSettingsStore((s) => s.useNerdFont);
  const setUseNerdFont = useSettingsStore((s) => s.setUseNerdFont);

  const hint =
    source === 'device'
      ? isDeviceTheme
        ? `Following the connected device. Active mode: ${mode}.`
        : 'Will follow the device once one is connected and reports a theme.'
      : `Active mode: ${mode}.`;

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: tokens.surface.primary }]}
      contentContainerStyle={styles.content}>
      <Stack.Screen
        options={{
          title: 'Settings',
          headerLeft: () => (
            <HeaderIconButton icon="close" accessibilityLabel="Close" onPress={() => router.back()} />
          ),
        }}
      />

      <Text style={[styles.sectionLabel, { color: tokens.text.muted }]}>Appearance</Text>
      <View
        style={[styles.card, { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle }]}>
        <Text style={[styles.rowLabel, { color: tokens.text.primary }]}>Theme source</Text>
        <Text style={[styles.rowHint, { color: tokens.text.muted }]}>{hint}</Text>
        <View style={styles.segmented}>
          {SOURCES.map((s) => {
            const selected = source === s;
            return (
              <Pressable
                key={s}
                accessibilityRole="button"
                accessibilityState={{ selected }}
                onPress={() => setSource(s)}
                style={[
                  styles.segment,
                  {
                    backgroundColor: selected ? tokens.accent.primary : tokens.surface.tertiary,
                    borderColor: tokens.border.subtle,
                  },
                ]}>
                <Text
                  style={[
                    styles.segmentLabel,
                    { color: selected ? tokens.accent.contrast : tokens.text.secondary },
                  ]}>
                  {s}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <Text style={[styles.sectionLabel, { color: tokens.text.muted }]}>Terminal</Text>
      <View
        style={[styles.card, { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle }]}>
        <View style={styles.toggleRow}>
          <View style={styles.toggleText}>
            <Text style={[styles.rowLabel, { color: tokens.text.primary }]}>Use Nerd Font</Text>
            <Text style={[styles.rowHint, { color: tokens.text.muted }]}>
              JetBrains Mono with powerline and icon glyphs.
            </Text>
          </View>
          <Switch
            value={useNerdFont}
            onValueChange={setUseNerdFont}
            trackColor={{ true: tokens.accent.primary, false: tokens.surface.tertiary }}
            thumbColor={tokens.surface.primary}
          />
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 8 },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    paddingHorizontal: 4,
  },
  card: { borderRadius: 12, borderWidth: StyleSheet.hairlineWidth, padding: 16, gap: 12 },
  rowLabel: { fontSize: 16, fontWeight: '500' },
  rowHint: { fontSize: 13 },
  segmented: { flexDirection: 'row', gap: 8 },
  segment: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
  },
  segmentLabel: { fontSize: 14, fontWeight: '500', textTransform: 'capitalize' },
  toggleRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  toggleText: { flex: 1, gap: 4 },
});
