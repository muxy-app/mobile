import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { useTokens } from '@/theme';
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
  const removeWorktree = useGitStore((s) => s.removeWorktree);

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

  const onRemove = (wt: Worktree) => {
    Alert.alert('Remove worktree', `Remove "${wt.name}"?`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: async () => {
          setBusyId(wt.id);
          setActionError(null);
          try {
            await removeWorktree(projectId, wt.id);
          } catch (err) {
            setActionError(err instanceof Error ? err.message : 'Failed to remove worktree');
          } finally {
            setBusyId(null);
          }
        },
      },
    ]);
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

      {worktrees && worktrees.length > 0 ? (
        <Section>
          {worktrees.map((wt, i) => (
            <View key={wt.id}>
              {i > 0 ? <Divider /> : null}
              <Row
                icon={wt.isPrimary ? 'star' : 'git-branch-outline'}
                iconColor={wt.isPrimary ? tokens.accent.primary : tokens.text.muted}
                title={wt.branch || wt.name}
                subtitle={wt.path}
                trailing={
                  busyId === wt.id ? (
                    <ActivityIndicator color={tokens.text.muted} size="small" />
                  ) : wt.canBeRemoved ? (
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel="Remove worktree"
                      hitSlop={10}
                      onPress={() => onRemove(wt)}>
                      <Ionicons name="trash-outline" size={18} color={tokens.status.danger} />
                    </Pressable>
                  ) : (
                    <Ionicons name="chevron-forward" size={18} color={tokens.text.muted} />
                  )
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
});
