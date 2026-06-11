import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { useTokens } from '@/theme';
import { useWorkspaceStore } from '@/state';
import type { Worktree } from '@/transport';

import { useGitStore, useGitWorktrees } from '../gitStore';
import { Divider, ErrorText, MutedText, PrimaryButton, Row, Section } from '../ui';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
  onClose: () => void;
};

export function WorktreesScreen({ projectId, setRoute, onClose }: Props) {
  const tokens = useTokens();
  const { worktrees, loading, error: loadError, reload } = useGitWorktrees(projectId);
  const selectWorktree = useGitStore((s) => s.selectWorktree);
  const activeWorktreeId = useWorkspaceStore((s) => s.workspace?.worktreeID);

  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const onSelect = async (wt: Worktree) => {
    setBusyId(wt.id);
    setActionError(null);
    try {
      await selectWorktree(projectId, wt.id);
      onClose();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to select worktree');
    } finally {
      setBusyId(null);
    }
  };

  const error = actionError ?? loadError;

  if (!worktrees && loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={tokens.accent.primary} />
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={loading} onRefresh={reload} tintColor={tokens.text.muted} />
      }
      showsVerticalScrollIndicator={false}>
      <PrimaryButton label="New worktree" onPress={() => setRoute({ name: 'newWorktree' })} />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Sync worktrees with desktop"
        onPress={reload}
        disabled={loading}
        style={({ pressed }) => [
          styles.syncButton,
          {
            backgroundColor: tokens.surface.secondary,
            borderColor: tokens.border.subtle,
            opacity: loading ? 0.5 : pressed ? 0.75 : 1,
          },
        ]}>
        {loading ? (
          <ActivityIndicator color={tokens.text.primary} size="small" />
        ) : (
          <Ionicons name="sync-outline" size={18} color={tokens.text.primary} />
        )}
        <MutedText>Sync from desktop</MutedText>
      </Pressable>

      {worktrees && worktrees.length > 0 ? (
        <Section>
          {worktrees.map((wt, i) => (
            <View key={wt.id}>
              {i > 0 ? <Divider /> : null}
              <Row
                title={wt.name}
                trailing={
                  busyId === wt.id ? (
                    <ActivityIndicator color={tokens.text.muted} size="small" />
                  ) : wt.id === activeWorktreeId ? (
                    <Ionicons name="checkmark" size={18} color={tokens.accent.primary} />
                  ) : null
                }
                onPress={() => onSelect(wt)}
                disabled={Boolean(busyId) && busyId !== wt.id}
              />
            </View>
          ))}
        </Section>
      ) : (
        <MutedText>No worktrees.</MutedText>
      )}

      {error ? <ErrorText>{error}</ErrorText> : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 16, paddingBottom: 32 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  syncButton: {
    minHeight: 44,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
});
