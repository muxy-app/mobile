import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { client } from '@/state';
import { useTokens } from '@/theme';

import { useVCSStatus } from '../useVCSStatus';
import {
  ActionGrid,
  Divider,
  ErrorText,
  MutedText,
  Row,
  Section,
  StatusPill,
  tokensStatusForFile,
} from '../ui';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
};

export function OverviewScreen({ projectId, setRoute }: Props) {
  const tokens = useTokens();
  const { status, loading, error, reload } = useVCSStatus(projectId);

  const [pulling, setPulling] = useState(false);
  const [pushing, setPushing] = useState(false);

  const onPull = async () => {
    setPulling(true);
    try {
      await client.request('vcsPull', { type: 'vcsPull', value: { projectID: projectId } });
      await reload();
    } catch {
    } finally {
      setPulling(false);
    }
  };

  const onPush = async () => {
    setPushing(true);
    try {
      await client.request('vcsPush', { type: 'vcsPush', value: { projectID: projectId } });
      await reload();
    } catch {
    } finally {
      setPushing(false);
    }
  };

  if (!status && loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={tokens.accent.primary} />
      </View>
    );
  }

  if (!status && error) {
    return (
      <View style={styles.center}>
        <ErrorText>{error}</ErrorText>
      </View>
    );
  }

  if (!status) {
    return (
      <View style={styles.center}>
        <MutedText>No git information.</MutedText>
      </View>
    );
  }

  const totalChanges = status.stagedFiles.length + status.changedFiles.length;
  const hasPR = !!status.pullRequest;

  return (
    <ScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={loading} onRefresh={reload} tintColor={tokens.text.muted} />
      }
      showsVerticalScrollIndicator={false}>
      <View
        style={[
          styles.branchCard,
          { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle },
        ]}>
        <View style={styles.branchHeader}>
          <Ionicons name="git-branch-outline" size={20} color={tokens.accent.primary} />
          <Text style={[styles.branchName, { color: tokens.text.primary }]} numberOfLines={1}>
            {status.branch}
          </Text>
        </View>
        <View style={styles.branchMeta}>
          {status.hasUpstream ? (
            <View style={styles.metaRow}>
              <Ionicons name="arrow-down" size={14} color={tokens.text.muted} />
              <Text style={[styles.metaText, { color: tokens.text.muted }]}>
                {status.behindCount}
              </Text>
              <Ionicons
                name="arrow-up"
                size={14}
                color={tokens.text.muted}
                style={{ marginLeft: 10 }}
              />
              <Text style={[styles.metaText, { color: tokens.text.muted }]}>
                {status.aheadCount}
              </Text>
            </View>
          ) : (
            <Text style={[styles.metaText, { color: tokens.text.muted }]}>No upstream</Text>
          )}
        </View>
      </View>

      <ActionGrid
        actions={[
          {
            icon: 'arrow-down-outline',
            label: 'Pull',
            onPress: onPull,
            loading: pulling,
            disabled: !status.hasUpstream,
          },
          {
            icon: 'arrow-up-outline',
            label: 'Push',
            onPress: onPush,
            loading: pushing,
            disabled: status.aheadCount === 0,
            badge: status.aheadCount > 0 ? String(status.aheadCount) : undefined,
          },
          {
            icon: 'checkmark-circle-outline',
            label: 'Commit',
            onPress: () => setRoute({ name: 'commit' }),
            disabled: totalChanges === 0,
            badge: totalChanges > 0 ? String(totalChanges) : undefined,
          },
          {
            icon: 'git-pull-request-outline',
            label: hasPR ? 'PR' : 'New PR',
            onPress: () => setRoute({ name: 'createPR' }),
            disabled: hasPR,
          },
        ]}
      />

      {hasPR && status.pullRequest ? (
        <Section title="Pull request">
          <Row
            icon="git-pull-request"
            iconColor={tokens.accent.primary}
            title={`#${status.pullRequest.number} → ${status.pullRequest.baseBranch}`}
            subtitle={`${status.pullRequest.state}${status.pullRequest.isDraft ? ' • draft' : ''}`}
            trailing={<Ionicons name="open-outline" size={18} color={tokens.text.muted} />}
            onPress={() => {
              const url = status.pullRequest?.url;
              if (url) {
                import('expo-web-browser').then((wb) => wb.openBrowserAsync(url)).catch(() => {});
              }
            }}
          />
        </Section>
      ) : null}

      <Section title="Manage">
        <Row
          icon="git-branch-outline"
          title="Branches"
          subtitle={status.branch}
          trailing={<Ionicons name="chevron-forward" size={18} color={tokens.text.muted} />}
          onPress={() => setRoute({ name: 'branches' })}
        />
        <Divider />
        <Row
          icon="folder-open-outline"
          title="Worktrees"
          trailing={<Ionicons name="chevron-forward" size={18} color={tokens.text.muted} />}
          onPress={() => setRoute({ name: 'worktrees' })}
        />
      </Section>

      {totalChanges > 0 ? (
        <Section title={`Changes (${totalChanges})`}>
          {status.stagedFiles.map((f, i) => {
            const meta = tokensStatusForFile(f.status, tokens);
            return (
              <View key={`s-${f.path}`}>
                {i > 0 ? <Divider /> : null}
                <Row
                  title={fileNameOf(f.path)}
                  subtitle={f.path}
                  trailing={<StatusPill label={meta.label} color={meta.color} textColor={tokens.accent.contrast} />}
                />
              </View>
            );
          })}
          {status.stagedFiles.length > 0 && status.changedFiles.length > 0 ? <Divider /> : null}
          {status.changedFiles.map((f, i) => {
            const meta = tokensStatusForFile(f.status, tokens);
            return (
              <View key={`c-${f.path}`}>
                {i > 0 ? <Divider /> : null}
                <Row
                  title={fileNameOf(f.path)}
                  subtitle={f.path}
                  trailing={<StatusPill label={meta.label} color={meta.color} textColor={tokens.accent.contrast} />}
                />
              </View>
            );
          })}
        </Section>
      ) : (
        <Section>
          <View style={styles.cleanRow}>
            <Ionicons name="checkmark-circle" size={18} color={tokens.status.success} />
            <Text style={[styles.cleanLabel, { color: tokens.text.muted }]}>Working tree clean</Text>
          </View>
        </Section>
      )}

      {error ? <ErrorText>{error}</ErrorText> : null}
    </ScrollView>
  );
}

function fileNameOf(path: string): string {
  const idx = path.lastIndexOf('/');
  return idx >= 0 ? path.slice(idx + 1) : path;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 16, paddingBottom: 32 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 12 },
  branchCard: {
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    padding: 14,
    gap: 8,
  },
  branchHeader: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  branchName: { fontSize: 17, fontWeight: '600', flex: 1 },
  branchMeta: { flexDirection: 'row', alignItems: 'center' },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  metaText: { fontSize: 13 },
  cleanRow: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 14 },
  cleanLabel: { fontSize: 14 },
});
