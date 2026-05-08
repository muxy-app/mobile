import { Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { GitSheet } from '@/components/git/GitSheet';
import { HeaderIconButton } from '@/components/HeaderIconButton';
import { TabKindPlaceholder } from '@/components/TabKindPlaceholder';
import { TerminalView } from '@/components/terminal/TerminalView';
import { WorkspaceTabStrip } from '@/components/WorkspaceTabStrip';
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

  const allTabs = workspace ? flattenTabs(workspace.root) : [];
  const focusedArea = workspace
    ? findArea(workspace.root, workspace.focusedAreaID) ?? null
    : null;
  const activeTabId = focusedArea?.activeTabID;
  const activeEntry = activeTabId ? allTabs.find((e) => e.tab.id === activeTabId) : undefined;
  const activeTab = activeEntry?.tab;

  const headerTitle = project?.name ?? 'Workspace';

  const onSelectTab = (tabId: string) => {
    if (!id) return;
    if (activeTabId === tabId) return;

    const target = allTabs.find((e) => e.tab.id === tabId);
    if (!target) return;

    useWorkspaceStore.getState().selectTabLocal(target.areaId, tabId);
    client
      .request('selectTab', {
        type: 'selectTab',
        value: { projectID: id, areaID: target.areaId, tabID: tabId },
      })
      .catch(() => {});
  };

  const headerGitButton = () => (
    <HeaderIconButton
      icon="git-branch-outline"
      accessibilityLabel="Git"
      onPress={() => id && setGitOpen(true)}
    />
  );

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
            tabs={allTabs.map((e) => e.tab)}
            activeTabId={activeTabId}
            onSelect={onSelectTab}
          />
          <View style={styles.body}>
            {activeTab ? (
              activeTab.kind === 'terminal' && activeTab.paneID ? (
                <TerminalView key={activeTab.paneID} paneId={activeTab.paneID} />
              ) : (
                <TabKindPlaceholder tab={activeTab} />
              )
            ) : (
              <Centered tokens={tokens}>
                <Text style={[styles.hint, { color: tokens.text.muted }]}>No active tab.</Text>
              </Centered>
            )}
          </View>
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
