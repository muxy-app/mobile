import { Ionicons } from '@expo/vector-icons';
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { useTokens } from '@/theme';
import type { Tab, TabKind } from '@/transport';

type Props = {
  tabs: Tab[];
  activeTabId: string | undefined;
  onSelect: (tabId: string) => void;
  onCreateTerminal: () => void;
  creatingTerminal?: boolean;
};

export type WorkspaceTabStripHandle = {
  scrollToIndex: (fractionalIndex: number, animated: boolean) => void;
};

export const WorkspaceTabStrip = forwardRef<WorkspaceTabStripHandle, Props>(function WorkspaceTabStrip(
  { tabs, activeTabId, onSelect, onCreateTerminal, creatingTerminal = false },
  ref,
) {
  const tokens = useTokens();
  const scrollRef = useRef<ScrollView>(null);
  const tabLayoutsRef = useRef<Record<string, { x: number; width: number }>>({});
  const viewportWidthRef = useRef(0);
  const [previewTabId, setPreviewTabId] = useState<string | null>(null);

  const onTabLayout = (id: string, e: LayoutChangeEvent) => {
    const { x, width } = e.nativeEvent.layout;
    tabLayoutsRef.current[id] = { x, width };
  };

  const tabsRef = useRef(tabs);
  tabsRef.current = tabs;

  useImperativeHandle(
    ref,
    () => ({
      scrollToIndex: (fractionalIndex, animated) => {
        const currentTabs = tabsRef.current;
        const nearest = Math.max(0, Math.min(currentTabs.length - 1, Math.round(fractionalIndex)));
        const nearestTab = currentTabs[nearest];
        setPreviewTabId(nearestTab ? nearestTab.id : null);

        const vw = viewportWidthRef.current;
        if (vw === 0) return;
        const lo = Math.floor(fractionalIndex);
        const hi = Math.ceil(fractionalIndex);
        const loTab = currentTabs[lo];
        if (!loTab) return;
        const loLayout = tabLayoutsRef.current[loTab.id];
        if (!loLayout) return;
        const loCenter = loLayout.x + loLayout.width / 2;
        let center = loCenter;
        const hiTab = currentTabs[hi];
        if (hiTab && hi !== lo) {
          const hiLayout = tabLayoutsRef.current[hiTab.id];
          if (hiLayout) {
            const hiCenter = hiLayout.x + hiLayout.width / 2;
            const t = fractionalIndex - lo;
            center = loCenter + (hiCenter - loCenter) * t;
          }
        }
        scrollRef.current?.scrollTo({ x: Math.max(0, center - vw / 2), animated });
      },
    }),
    [],
  );

  useEffect(() => {
    setPreviewTabId(null);
  }, [activeTabId]);

  const visiblyActiveId = previewTabId ?? activeTabId;

  return (
    <View style={[styles.bar, { borderBottomColor: tokens.border.subtle }]}>
      <ScrollView
        ref={scrollRef}
        horizontal
        showsHorizontalScrollIndicator={false}
        onLayout={(e) => {
          viewportWidthRef.current = e.nativeEvent.layout.width;
        }}
        contentContainerStyle={styles.row}>
        {tabs.map((tab) => {
          const active = tab.id === visiblyActiveId;
          return (
            <Pressable
              key={tab.id}
              onPress={() => onSelect(tab.id)}
              disabled={tab.id === activeTabId}
              onLayout={(e) => onTabLayout(tab.id, e)}
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
        <Pressable
          onPress={onCreateTerminal}
          disabled={creatingTerminal}
          accessibilityRole="button"
          accessibilityLabel="New terminal"
          style={({ pressed }) => [
            styles.addButton,
            {
              backgroundColor: tokens.surface.secondary,
              borderColor: tokens.border.subtle,
              opacity: creatingTerminal ? 0.45 : pressed ? 0.75 : 1,
            },
          ]}>
          <Ionicons name="add" size={18} color={tokens.text.secondary} />
        </Pressable>
      </ScrollView>
    </View>
  );
});

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
  addButton: {
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
  },
  label: {
    fontSize: 13,
    fontWeight: '500',
    flexShrink: 1,
  },
});
