import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

import { useTokens } from '@/theme';
import type { Tab, TabKind } from '@/transport';

const COPY: Record<TabKind, { title: string; body: string; icon: keyof typeof Ionicons.glyphMap }> = {
  terminal: {
    title: 'Terminal',
    body: 'Live terminal rendering arrives in the next phase.',
    icon: 'terminal-outline',
  },
  vcs: {
    title: 'Version control',
    body: 'Git status, commits, and PR creation will land in a later phase.',
    icon: 'git-branch-outline',
  },
  editor: {
    title: 'Editor',
    body: 'Editor mirroring isn’t in scope for the mobile app yet.',
    icon: 'document-text-outline',
  },
  diffViewer: {
    title: 'Diff viewer',
    body: 'Diff viewing isn’t in scope for the mobile app yet.',
    icon: 'git-compare-outline',
  },
};

export function TabKindPlaceholder({ tab }: { tab: Tab }) {
  const tokens = useTokens();
  const copy = COPY[tab.kind];
  return (
    <View style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <View
        style={[
          styles.iconRing,
          { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle },
        ]}>
        <Ionicons name={copy.icon} size={28} color={tokens.text.secondary} />
      </View>
      <Text style={[styles.title, { color: tokens.text.primary }]}>{copy.title}</Text>
      {tab.title ? (
        <Text style={[styles.subtitle, { color: tokens.text.secondary }]} numberOfLines={2}>
          {tab.title}
        </Text>
      ) : null}
      <Text style={[styles.body, { color: tokens.text.muted }]}>{copy.body}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32, gap: 12 },
  iconRing: {
    width: 64,
    height: 64,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  title: { fontSize: 18, fontWeight: '600' },
  subtitle: { fontSize: 14, textAlign: 'center' },
  body: { fontSize: 13, textAlign: 'center' },
});
