import { Ionicons } from '@expo/vector-icons';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { useTokens } from '@/theme';
import type { Tab, TabKind } from '@/transport';

type Props = {
  tabs: Tab[];
  activeTabId: string | undefined;
  onSelect: (tabId: string) => void;
};

export function WorkspaceTabStrip({ tabs, activeTabId, onSelect }: Props) {
  const tokens = useTokens();
  return (
    <View style={[styles.bar, { borderBottomColor: tokens.border.subtle }]}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.row}>
        {tabs.map((tab) => {
          const active = tab.id === activeTabId;
          return (
            <Pressable
              key={tab.id}
              onPress={() => onSelect(tab.id)}
              disabled={active}
              style={({ pressed }) => [
                styles.tab,
                {
                  backgroundColor: active ? tokens.surface.tertiary : 'transparent',
                  borderColor: active ? tokens.accent.primary : tokens.border.subtle,
                  opacity: pressed ? 0.85 : 1,
                },
              ]}>
              <Ionicons
                name={iconForKind(tab.kind)}
                size={14}
                color={active ? tokens.accent.primary : tokens.text.muted}
              />
              <Text
                style={[
                  styles.label,
                  { color: active ? tokens.text.primary : tokens.text.secondary },
                ]}
                numberOfLines={1}>
                {tab.title || labelForKind(tab.kind)}
              </Text>
              {tab.isPinned ? (
                <Ionicons name="pin" size={12} color={tokens.text.muted} />
              ) : null}
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

function iconForKind(kind: TabKind): keyof typeof Ionicons.glyphMap {
  switch (kind) {
    case 'terminal':
      return 'terminal-outline';
    case 'vcs':
      return 'git-branch-outline';
    case 'editor':
      return 'document-text-outline';
    case 'diffViewer':
      return 'git-compare-outline';
  }
}

function labelForKind(kind: TabKind): string {
  switch (kind) {
    case 'terminal':
      return 'Terminal';
    case 'vcs':
      return 'VCS';
    case 'editor':
      return 'Editor';
    case 'diffViewer':
      return 'Diff';
  }
}

const styles = StyleSheet.create({
  bar: {
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  row: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    gap: 8,
  },
  tab: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 999,
    borderWidth: StyleSheet.hairlineWidth,
    maxWidth: 220,
  },
  label: {
    fontSize: 13,
    fontWeight: '500',
    flexShrink: 1,
  },
});
