import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { client, useDevicesStore } from '@/state';
import { useTokens } from '@/theme';
import type { VCSBranches } from '@/transport';

import { Divider, ErrorText, MutedText, PrimaryButton, Row, Section } from '../ui';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
};

export function BranchesScreen({ projectId, setRoute }: Props) {
  const tokens = useTokens();
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);

  const [data, setData] = useState<VCSBranches | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [switching, setSwitching] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await client.request('vcsListBranches', {
        type: 'vcsListBranches',
        value: { projectID: projectId },
      });
      setData(res.value);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load branches');
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    if (connectionPhase !== 'connected') return;
    load();
  }, [connectionPhase, load]);

  const onSwitch = async (branch: string) => {
    if (!data || branch === data.current) return;
    setSwitching(branch);
    try {
      await client.request('vcsSwitchBranch', {
        type: 'vcsSwitchBranch',
        value: { projectID: projectId, branch },
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to switch branch');
    } finally {
      setSwitching(null);
    }
  };

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
        <RefreshControl refreshing={loading} onRefresh={load} tintColor={tokens.text.muted} />
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
