import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { useTokens } from '@/theme';

import { useGitBranches, useGitStore } from '../gitStore';
import { Divider, ErrorText, MutedText, PrimaryButton, Row, Section } from '../ui';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
};

export function BranchesScreen({ projectId, setRoute }: Props) {
  const tokens = useTokens();
  const { branches: data, loading, error: loadError, reload } = useGitBranches(projectId);
  const switchBranch = useGitStore((s) => s.switchBranch);

  const [switching, setSwitching] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const onSwitch = async (branch: string) => {
    if (!data || branch === data.current) return;
    setSwitching(branch);
    setActionError(null);
    try {
      await switchBranch(projectId, branch);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to switch branch');
    } finally {
      setSwitching(null);
    }
  };

  const error = actionError ?? loadError;

  if (!data && loading) {
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
      <PrimaryButton label="New branch" onPress={() => setRoute({ name: 'newBranch' })} />

      {data && data.locals.length > 0 ? (
        <Section title="Local">
          {data.locals.map((branch, i) => {
            const isCurrent = branch === data.current;
            const isDefault = branch === data.defaultBranch;
            const switchingThis = switching === branch;
            return (
              <View key={branch}>
                {i > 0 ? <Divider /> : null}
                <Row
                  icon={isCurrent ? 'checkmark' : 'git-branch-outline'}
                  iconColor={isCurrent ? tokens.accent.primary : tokens.text.muted}
                  title={branch}
                  subtitle={isDefault ? 'default' : undefined}
                  trailing={
                    switchingThis ? (
                      <ActivityIndicator color={tokens.text.muted} size="small" />
                    ) : isCurrent ? null : (
                      <Ionicons name="swap-horizontal" size={18} color={tokens.text.muted} />
                    )
                  }
                  onPress={isCurrent ? undefined : () => onSwitch(branch)}
                  disabled={Boolean(switching) && !switchingThis}
                />
              </View>
            );
          })}
        </Section>
      ) : (
        <MutedText>No branches.</MutedText>
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
