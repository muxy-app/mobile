import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, NativeEventEmitter, NativeModules, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';

import { GitSheet } from '@/components/git/GitSheet';
import { HeaderIconButton } from '@/components/HeaderIconButton';
import { SwipeArrowOverlay, type SwipeArrowOverlayHandle } from '@/components/SwipeArrowOverlay';
import { TabKindPlaceholder } from '@/components/TabKindPlaceholder';
import { TerminalView } from '@/components/terminal/TerminalView';
import { WorkspaceTabStrip, type WorkspaceTabStripHandle } from '@/components/WorkspaceTabStrip';
import {
  client,
  closeTerminalTab,
  createTerminalTab,
  findArea,
  flattenTabs,
  tabShortcutToIndex,
  useDevicesStore,
  useProjectsStore,
  useWorkspace,
  useWorkspaceStore,
} from '@/state';
import { useTokens } from '@/theme';

type MuxyMenuCommandEvent = {
  type: 'newTab' | 'selectTab';
  index?: number;
};

const muxyMenuCommands = NativeModules.MuxyMenuCommands
  ? new NativeEventEmitter(NativeModules.MuxyMenuCommands)
  : null;

export default function WorkspaceScreen() {
  const tokens = useTokens();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [gitOpen, setGitOpen] = useState(false);
  const [creatingTerminal, setCreatingTerminal] = useState(false);
  const creatingTerminalRef = useRef(false);
  const [tabActionError, setTabActionError] = useState<string | null>(null);
  const [closingTabId, setClosingTabId] = useState<string | null>(null);

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
  const tabCount = allTabs.length;

  const stripRef = useRef<WorkspaceTabStripHandle>(null);
  const arrowRef = useRef<SwipeArrowOverlayHandle>(null);

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
    if (idx !== activeIndex) {
      arrowRef.current?.flash(idx > activeIndex ? 'next' : 'prev');
      Haptics.selectionAsync();
    }
    selectTabAt(idx);
  };

  const selectTabShortcut = useCallback(
    (digit: number) => {
      const idx = tabShortcutToIndex(digit, tabCount);
      if (idx === null) return;
      if (idx !== activeIndex) {
        arrowRef.current?.flash(idx > activeIndex ? 'next' : 'prev');
        Haptics.selectionAsync();
      }
      selectTabAt(idx);
    },
    [activeIndex, selectTabAt, tabCount],
  );

  const handleCreateTerminal = useCallback(async () => {
    if (!id || creatingTerminalRef.current) return;
    creatingTerminalRef.current = true;
    setCreatingTerminal(true);
    setTabActionError(null);
    try {
      await createTerminalTab({
        projectId: id,
        workspace: useWorkspaceStore.getState().workspace,
        request: client.request.bind(client),
        setWorkspace: useWorkspaceStore.getState().setWorkspace,
      });
      Haptics.selectionAsync();
    } catch {
      setTabActionError('Couldn’t create terminal');
    } finally {
      creatingTerminalRef.current = false;
      setCreatingTerminal(false);
    }
  }, [id]);

  const handleCloseTab = useCallback(
    async (areaId: string, tabId: string) => {
      if (!id || closingTabId) return;
      setClosingTabId(tabId);
      setTabActionError(null);
      try {
        await closeTerminalTab({
          projectId: id,
          areaId,
          tabId,
          request: client.request.bind(client),
          setWorkspace: useWorkspaceStore.getState().setWorkspace,
        });
        Haptics.selectionAsync();
      } catch {
        setTabActionError('Couldn’t close tab');
      } finally {
        setClosingTabId(null);
      }
    },
    [id, closingTabId],
  );

  useEffect(() => {
    if (Platform.OS !== 'ios' || !muxyMenuCommands) return;
    const sub = muxyMenuCommands.addListener('MuxyMenuCommand', (event: MuxyMenuCommandEvent) => {
      if (event.type === 'newTab') {
        handleCreateTerminal();
        return;
      }
      if (event.type === 'selectTab' && typeof event.index === 'number') {
        selectTabShortcut(event.index + 1);
      }
    });
    return () => sub.remove();
  }, [handleCreateTerminal, selectTabShortcut]);

  const headerGitButton = () => (
    <HeaderIconButton
      icon="git-branch-outline"
      accessibilityLabel="Git"
      onPress={() => id && setGitOpen(true)}
    />
  );

  const swipeGesture = useMemo(() => {
    const canPrev = activeIndex > 0;
    const canNext = activeIndex >= 0 && activeIndex < tabCount - 1;
    const goToNeighbor = (delta: number) => {
      if (activeIndex < 0) return;
      const next = activeIndex + delta;
      if (next < 0 || next >= tabCount) return;
      Haptics.selectionAsync();
      selectTabAt(next);
    };
    let crossedThreshold = false;
    return Gesture.Pan()
      .activeOffsetX([-25, 25])
      .failOffsetY([-15, 15])
      .onBegin(() => {
        crossedThreshold = false;
      })
      .onUpdate((e) => {
        arrowRef.current?.setDragOffset(e.translationX, canPrev, canNext);
        const reached = Math.abs(e.translationX) >= 40;
        const directionAllowed = e.translationX > 0 ? canPrev : canNext;
        if (reached && directionAllowed && !crossedThreshold) {
          crossedThreshold = true;
          Haptics.selectionAsync();
        } else if (!reached && crossedThreshold) {
          crossedThreshold = false;
        }
      })
      .onEnd((e) => {
        arrowRef.current?.releaseDrag();
        const dx = e.translationX;
        const vx = e.velocityX;
        if (dx <= -40 || vx <= -500) {
          goToNeighbor(1);
        } else if (dx >= 40 || vx >= 500) {
          goToNeighbor(-1);
        }
      })
      .onFinalize(() => {
        arrowRef.current?.releaseDrag();
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
            Create a terminal to get started in this project.
          </Text>
          <Pressable
            onPress={handleCreateTerminal}
            disabled={creatingTerminal}
            accessibilityRole="button"
            accessibilityLabel="New terminal"
            style={({ pressed }) => [
              styles.emptyButton,
              {
                backgroundColor: tokens.surface.secondary,
                borderColor: tokens.border.subtle,
                opacity: creatingTerminal ? 0.45 : pressed ? 0.75 : 1,
              },
            ]}>
            <Ionicons name="add" size={18} color={tokens.text.primary} />
            <Text style={[styles.emptyButtonLabel, { color: tokens.text.primary }]}>
              New terminal
            </Text>
          </Pressable>
          {tabActionError ? (
            <Text style={[styles.errorBody, { color: tokens.status.danger }]}>
              {tabActionError}
            </Text>
          ) : null}
        </Centered>
      ) : (
        <>
          <WorkspaceTabStrip
            ref={stripRef}
            tabs={allTabs}
            activeTabId={activeTabId}
            onSelect={onSelectTab}
            onCreateTerminal={handleCreateTerminal}
            onCloseTab={handleCloseTab}
            creatingTerminal={creatingTerminal}
            closingTabId={closingTabId}
          />
          {tabActionError ? (
            <Text
              style={[
                styles.inlineError,
                { color: tokens.status.danger, borderBottomColor: tokens.border.subtle },
              ]}>
              {tabActionError}
            </Text>
          ) : null}
          <GestureDetector gesture={swipeGesture}>
            <View style={styles.body}>
              {activeEntry ? (
                activeEntry.tab.kind === 'terminal' && activeEntry.tab.paneID ? (
                  <TerminalView
                    key={activeEntry.tab.id}
                    paneId={activeEntry.tab.paneID}
                    onNewTerminal={handleCreateTerminal}
                    onSelectTabShortcut={selectTabShortcut}
                  />
                ) : (
                  <TabKindPlaceholder tab={activeEntry.tab} />
                )
              ) : null}
              <SwipeArrowOverlay ref={arrowRef} />
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
  emptyButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 999,
    borderWidth: StyleSheet.hairlineWidth,
    marginTop: 4,
  },
  emptyButtonLabel: { fontSize: 14, fontWeight: '600' },
  errorBody: { fontSize: 14, textAlign: 'center' },
  inlineError: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    fontSize: 13,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
});
