import { Stack, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';

import { GitSheet } from '@/components/git/GitSheet';
import { HeaderIconButton } from '@/components/HeaderIconButton';
import { TabKindPlaceholder } from '@/components/TabKindPlaceholder';
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

export default function WorkspaceScreen() {
  const tokens = useTokens();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [gitOpen, setGitOpen] = useState(false);

  const project = useProjectsStore((s) => s.projects.find((p) => p.id === id));
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const fetchPhase = useWorkspaceStore((s) => s.fetchPhase);
  const fetchError = useWorkspaceStore((s) => s.fetchError);

  useWorkspace(id);

  const allTabs = useMemo(() => (workspace ? flattenTabs(workspace.root) : []), [workspace]);
  const focusedArea = workspace
    ? findArea(workspace.root, workspace.focusedAreaID) ?? null
    : null;
  const activeTabId = focusedArea?.activeTabID;
  const activeIndex = activeTabId ? allTabs.findIndex((e) => e.tab.id === activeTabId) : -1;
  const activeEntry = activeIndex >= 0 ? allTabs[activeIndex] : undefined;

  const headerTitle = project?.name ?? 'Workspace';

  const stripRef = useRef<WorkspaceTabStripHandle>(null);

  useEffect(() => {
    if (activeIndex < 0) return;
    stripRef.current?.scrollToIndex(activeIndex, true);
  }, [activeIndex]);

  const selectTabAt = useCallback(
    (index: number) => {
      if (!id) return;
      const target = allTabs[index];
      if (!target) return;
      if (target.tab.id === activeTabId) return;
      useWorkspaceStore.getState().selectTabLocal(target.areaId, target.tab.id);
      client
        .request('selectTab', {
          type: 'selectTab',
          value: { projectID: id, areaID: target.areaId, tabID: target.tab.id },
        })
        .catch(() => {});
    },
    [id, allTabs, activeTabId],
  );

  const onSelectTab = (tabId: string) => {
    const idx = allTabs.findIndex((e) => e.tab.id === tabId);
    if (idx < 0) return;
    selectTabAt(idx);
  };

  const headerGitButton = () => (
    <HeaderIconButton
      icon="git-branch-outline"
      accessibilityLabel="Git"
      onPress={() => id && setGitOpen(true)}
    />
  );

  const tabCount = allTabs.length;
  const swipeGesture = useMemo(() => {
    const goToNeighbor = (delta: number) => {
      if (activeIndex < 0) return;
      const next = activeIndex + delta;
      if (next < 0 || next >= tabCount) return;
      selectTabAt(next);
    };
    return Gesture.Pan()
      .activeOffsetX([-25, 25])
      .failOffsetY([-15, 15])
      .onEnd((e) => {
        const dx = e.translationX;
        const vx = e.velocityX;
        if (dx <= -40 || vx <= -500) {
          goToNeighbor(1);
        } else if (dx >= 40 || vx >= 500) {
          goToNeighbor(-1);
        }
      })
      .runOnJS(true);
  }, [tabCount, activeIndex, selectTabAt]);

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
          <GestureDetector gesture={swipeGesture}>
            <View style={styles.body}>
              {activeEntry ? (
                activeEntry.tab.kind === 'terminal' && activeEntry.tab.paneID ? (
                  <TerminalView key={activeEntry.tab.id} paneId={activeEntry.tab.paneID} />
                ) : (
                  <TabKindPlaceholder tab={activeEntry.tab} />
                )
              ) : null}
            </View>
          </GestureDetector>
        </>
      )}
    </View>
  );
}

function Centered({ children, tokens }: { children: React.ReactNode; tokens: ReturnType<typeof useTokens> }) {
  return <View style={[styles.center, { backgroundColor: tokens.surface.primary }]}>{children}</View>;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  body: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32, gap: 10 },
  title: { fontSize: 20, fontWeight: '600' },
  hint: { fontSize: 14, textAlign: 'center' },
  errorBody: { fontSize: 14, textAlign: 'center' },
});
