import { Stack, useRouter } from 'expo-router';
import { ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import { HeaderIconButton } from '@/components/HeaderIconButton';
import { useSettingsStore } from '@/state';
import { useTheme } from '@/theme';

export default function SettingsScreen() {
  const { tokens } = useTheme();
  const router = useRouter();
  const useNerdFont = useSettingsStore((s) => s.useNerdFont);
  const setUseNerdFont = useSettingsStore((s) => s.setUseNerdFont);
  const demoMode = useSettingsStore((s) => s.demoMode);
  const setDemoMode = useSettingsStore((s) => s.setDemoMode);

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

      <Text style={[styles.sectionLabel, { color: tokens.text.muted }]}>Demo</Text>
      <View
        style={[styles.card, { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle }]}>
        <View style={styles.toggleRow}>
          <View style={styles.toggleText}>
            <Text style={[styles.rowLabel, { color: tokens.text.primary }]}>Demo Mode</Text>
            <Text style={[styles.rowHint, { color: tokens.text.muted }]}>
              Loads sample data so you can try the app without a desktop. Switching it off restores your real devices.
            </Text>
          </View>
          <Switch
            value={demoMode}
            onValueChange={setDemoMode}
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
  toggleRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  toggleText: { flex: 1, gap: 4 },
});
