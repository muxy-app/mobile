import { Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import PagerView from 'react-native-pager-view';

import { GitSheet } from '@/components/git/GitSheet';
import { HeaderIconButton } from '@/components/HeaderIconButton';
import { TabKindPlaceholder } from '@/components/TabKindPlaceholder';
import { buildTerminalTheme } from '@/components/terminal/buildTerminalTheme';
import { TerminalView } from '@/components/terminal/TerminalView';
import { WorkspaceTabStrip, type WorkspaceTabStripHandle } from '@/components/WorkspaceTabStrip';
import {
  client,
  findArea,
  flattenTabs,
  useDevicesStore,
  useProjectsStore,
  useWorkspace,
  useWorkspaceStore,
} from '@/state';
import { useTokens } from '@/theme';
import type { Tab } from '@/transport';

export default function WorkspaceScreen() {
  const tokens = useTokens();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [gitOpen, setGitOpen] = useState(false);

  const project = useProjectsStore((s) => s.projects.find((p) => p.id === id));
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const fetchPhase = useWorkspaceStore((s) => s.fetchPhase);
  const fetchError = useWorkspaceStore((s) => s.fetchError);

  const lastTheme = useDevicesStore((s) => s.lastAppliedTheme);
  const activePairing = useDevicesStore((s) => {
    const did = s.activeDeviceId;
    if (!did) return null;
    return s.devices.find((d) => d.id === did)?.pairing ?? null;
  });
  const terminalBg = useMemo(() => {
    const device = activePairing
      ? {
          themeFg: activePairing.themeFg,
          themeBg: activePairing.themeBg,
          themePalette: activePairing.themePalette,
        }
      : lastTheme;
    return buildTerminalTheme(device, tokens).background;
  }, [activePairing, lastTheme, tokens]);

  useWorkspace(id);

  const allTabs = workspace ? flattenTabs(workspace.root) : [];
  const focusedArea = workspace
    ? findArea(workspace.root, workspace.focusedAreaID) ?? null
    : null;
  const activeTabId = focusedArea?.activeTabID;
  const activeIndex = activeTabId ? allTabs.findIndex((e) => e.tab.id === activeTabId) : -1;

  const headerTitle = project?.name ?? 'Workspace';

  const pagerRef = useRef<PagerView>(null);
  const stripRef = useRef<WorkspaceTabStripHandle>(null);
  const lastSyncedIndexRef = useRef(activeIndex);
  const [pagerScrollEnabled, setPagerScrollEnabled] = useState(true);

  useEffect(() => {
    if (activeIndex < 0) return;
    if (activeIndex === lastSyncedIndexRef.current) return;
    lastSyncedIndexRef.current = activeIndex;
    pagerRef.current?.setPage(activeIndex);
    stripRef.current?.scrollToIndex(activeIndex, true);
  }, [activeIndex]);

  const selectTabAt = (index: number) => {
    if (!id) return;
    const target = allTabs[index];
    if (!target) return;
    if (target.tab.id === activeTabId) return;
    lastSyncedIndexRef.current = index;
    useWorkspaceStore.getState().selectTabLocal(target.areaId, target.tab.id);
    client
      .request('selectTab', {
        type: 'selectTab',
        value: { projectID: id, areaID: target.areaId, tabID: target.tab.id },
      })
      .catch(() => {});
  };

  const onSelectTab = (tabId: string) => {
    const idx = allTabs.findIndex((e) => e.tab.id === tabId);
    if (idx >= 0) selectTabAt(idx);
  };

  const headerGitButton = () => (
    <HeaderIconButton
      icon="git-branch-outline"
      accessibilityLabel="Git"
      onPress={() => id && setGitOpen(true)}
    />
  );

  const initialPage = activeIndex >= 0 ? activeIndex : 0;

  return (
    <View style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <Stack.Screen options={{ title: headerTitle, headerRight: headerGitButton }} />
      {id ? <GitSheet visible={gitOpen} onClose={() => setGitOpen(false)} projectId={id} /> : null}

      {!workspace ? (
        <Centered tokens={tokens}>
          {fetchPhase === 'error' ? (
            <Text style={[styles.errorBody, { color: tokens.status.danger }]}>
              {fetchError ?? 'Couldn’t load workspace'}
            </Text>
          ) : connectionPhase !== 'connected' || fetchPhase === 'loading' ? (
            <>
              <ActivityIndicator color={tokens.accent.primary} />
              <Text style={[styles.hint, { color: tokens.text.muted }]}>
                {connectionPhase === 'connected' ? 'Loading workspace…' : 'Connecting…'}
              </Text>
            </>
          ) : null}
        </Centered>
      ) : allTabs.length === 0 ? (
        <Centered tokens={tokens}>
          <Text style={[styles.title, { color: tokens.text.primary }]}>No tabs</Text>
          <Text style={[styles.hint, { color: tokens.text.muted }]}>
            Open Muxy on your Mac and create a tab in this project.
          </Text>
        </Centered>
      ) : (
        <>
          <WorkspaceTabStrip
            ref={stripRef}
            tabs={allTabs.map((e) => e.tab)}
            activeTabId={activeTabId}
            onSelect={onSelectTab}
          />
          <PagerView
            key={allTabs.map((e) => e.tab.id).join('|')}
            ref={pagerRef}
            style={styles.body}
            initialPage={initialPage}
            offscreenPageLimit={1}
            scrollEnabled={pagerScrollEnabled}
            onPageScroll={(e) => {
              const { position, offset } = e.nativeEvent;
              stripRef.current?.scrollToIndex(position + offset, false);
            }}
            onPageSelected={(e) => selectTabAt(e.nativeEvent.position)}>
            {allTabs.map((entry, index) => {
              const isActive = index === activeIndex;
              return (
                <View key={entry.tab.id} style={styles.page}>
                  {entry.tab.kind === 'terminal' && entry.tab.paneID ? (
                    isActive ? (
                      <TerminalView paneId={entry.tab.paneID} onPagerScrollEnabled={setPagerScrollEnabled} />
                    ) : (
                      <TerminalPagePlaceholder tab={entry.tab} background={terminalBg} />
                    )
                  ) : (
                    <TabKindPlaceholder tab={entry.tab} />
                  )}
                </View>
              );
            })}
          </PagerView>
        </>
      )}
    </View>
  );
}

function Centered({ children, tokens }: { children: React.ReactNode; tokens: ReturnType<typeof useTokens> }) {
  return <View style={[styles.center, { backgroundColor: tokens.surface.primary }]}>{children}</View>;
}

function TerminalPagePlaceholder({ tab, background }: { tab: Tab; background: string }) {
  const tokens = useTokens();
  return (
    <View style={[styles.terminalPlaceholder, { backgroundColor: background }]}>
      <ActivityIndicator color={tokens.text.muted} />
      {tab.title ? (
        <Text style={[styles.terminalPlaceholderLabel, { color: tokens.text.muted }]} numberOfLines={1}>
          {tab.title}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  body: { flex: 1 },
  page: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32, gap: 10 },
  title: { fontSize: 20, fontWeight: '600' },
  hint: { fontSize: 14, textAlign: 'center' },
  errorBody: { fontSize: 14, textAlign: 'center' },
  terminalPlaceholder: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  terminalPlaceholderLabel: { fontSize: 13, fontWeight: '500' },
});
