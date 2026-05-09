import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Alert, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { useTokens, type ThemeTokens } from '@/theme';
import type { VCSMergeMethod, VCSPRChecks, VCSPRMergeStateStatus } from '@/transport';

import { useGitStatus, useGitStore } from '../gitStore';
import {
  Divider,
  ErrorText,
  GhostButton,
  MutedText,
  PrimaryButton,
  Row,
  Section,
  StatusPill,
} from '../ui';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
};

const MERGE_METHODS: { method: VCSMergeMethod; label: string }[] = [
  { method: 'squash', label: 'Squash and merge' },
  { method: 'merge', label: 'Create merge commit' },
  { method: 'rebase', label: 'Rebase and merge' },
];

export function PullRequestScreen({ projectId, setRoute }: Props) {
  const tokens = useTokens();
  const { status, loading, reload } = useGitStatus(projectId);
  const mergePullRequest = useGitStore((s) => s.mergePullRequest);

  const pr = status?.pullRequest ?? null;

  const [merging, setMerging] = useState<VCSMergeMethod | null>(null);
  const [deleteBranch, setDeleteBranch] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const onMerge = (method: VCSMergeMethod) => {
    if (!pr) return;
    Alert.alert(
      'Merge pull request',
      `${methodLabel(method)} #${pr.number} into ${pr.baseBranch}?` +
        (deleteBranch ? '\n\nThe head branch will be deleted.' : ''),
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Merge',
          style: 'default',
          onPress: async () => {
            setMerging(method);
            setError(null);
            try {
              await mergePullRequest(projectId, { number: pr.number, method, deleteBranch });
              setRoute({ name: 'overview' });
            } catch (err) {
              setError(err instanceof Error ? err.message : 'Failed to merge pull request');
            } finally {
              setMerging(null);
            }
          },
        },
      ],
    );
  };

  if (!pr) {
    return (
      <View style={styles.center}>
        <MutedText>No pull request for this branch.</MutedText>
      </View>
    );
  }

  const checks = pr.checks ?? { status: 'none' as const, passing: 0, failing: 0, pending: 0, total: 0 };
  const mergeStateStatus = pr.mergeStateStatus ?? 'UNKNOWN';
  const mergeable = pr.mergeable ?? null;
  const stateInfo = stateMeta(pr.state, pr.isDraft, tokens);
  const canMerge = pr.state === 'OPEN' && !pr.isDraft && mergeable !== false;
  const mergeBlockedReason = mergeStateMessage(mergeStateStatus, mergeable);

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
          styles.headerCard,
          { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle },
        ]}>
        <View style={styles.headerTop}>
          <Ionicons name="git-pull-request" size={20} color={tokens.accent.primary} />
          <Text style={[styles.headerTitle, { color: tokens.text.primary }]} numberOfLines={1}>
            #{pr.number}
          </Text>
          <StatusPill label={stateInfo.label} color={stateInfo.color} textColor={tokens.accent.contrast} />
        </View>
        <Text style={[styles.headerBranches, { color: tokens.text.muted }]} numberOfLines={1}>
          {status?.branch ?? '…'} → {pr.baseBranch}
        </Text>
      </View>

      <ChecksCard checks={checks} tokens={tokens} />

      <Section title="Merge state">
        <Row
          icon={mergeStateIcon(mergeStateStatus, mergeable)}
          iconColor={mergeStateColor(mergeStateStatus, mergeable, tokens)}
          title={mergeStateTitle(mergeStateStatus, mergeable)}
          subtitle={mergeBlockedReason ?? undefined}
        />
        <Divider />
        <Row
          icon={deleteBranch ? 'checkbox' : 'square-outline'}
          iconColor={deleteBranch ? tokens.accent.primary : tokens.text.muted}
          title="Delete branch after merge"
          onPress={() => setDeleteBranch((v) => !v)}
        />
      </Section>

      {pr.state === 'OPEN' ? (
        <View style={styles.mergeActions}>
          {MERGE_METHODS.map((m) => (
            <PrimaryButton
              key={m.method}
              label={m.label}
              onPress={() => onMerge(m.method)}
              loading={merging === m.method}
              disabled={!canMerge || merging !== null}
            />
          ))}
        </View>
      ) : null}

      <GhostButton
        label="Open in browser"
        onPress={() => {
          import('expo-web-browser').then((wb) => wb.openBrowserAsync(pr.url)).catch(() => {});
        }}
      />

      {error ? <ErrorText>{error}</ErrorText> : null}
    </ScrollView>
  );
}

function ChecksCard({ checks, tokens }: { checks: VCSPRChecks; tokens: ThemeTokens }) {
  if (checks.total === 0) {
    return (
      <Section title="Checks">
        <Row icon="ellipse-outline" iconColor={tokens.text.muted} title="No checks reported" />
      </Section>
    );
  }
  const summary = checksSummary(checks);
  return (
    <Section title="Checks">
      <Row
        icon={checksIcon(checks.status)}
        iconColor={checksColor(checks.status, tokens)}
        title={summary.title}
        subtitle={summary.subtitle}
      />
      <Divider />
      <View style={styles.checksGrid}>
        <ChecksMetric label="Passing" count={checks.passing} color={tokens.status.success} tokens={tokens} />
        <ChecksMetric label="Failing" count={checks.failing} color={tokens.status.danger} tokens={tokens} />
        <ChecksMetric label="Pending" count={checks.pending} color={tokens.status.warning} tokens={tokens} />
      </View>
    </Section>
  );
}

function ChecksMetric({
  label,
  count,
  color,
  tokens,
}: {
  label: string;
  count: number;
  color: string;
  tokens: ThemeTokens;
}) {
  return (
    <View style={styles.metric}>
      <Text style={[styles.metricCount, { color }]}>{count}</Text>
      <Text style={[styles.metricLabel, { color: tokens.text.muted }]}>{label}</Text>
    </View>
  );
}

function checksSummary(checks: VCSPRChecks): { title: string; subtitle: string } {
  switch (checks.status) {
    case 'success':
      return { title: 'All checks passed', subtitle: `${checks.passing}/${checks.total} successful` };
    case 'failure':
      return { title: 'Checks failed', subtitle: `${checks.failing} failing of ${checks.total}` };
    case 'pending':
      return { title: 'Checks running', subtitle: `${checks.pending} pending of ${checks.total}` };
    default:
      return { title: 'No checks', subtitle: '' };
  }
}

function checksIcon(status: VCSPRChecks['status']): React.ComponentProps<typeof Ionicons>['name'] {
  switch (status) {
    case 'success': return 'checkmark-circle';
    case 'failure': return 'close-circle';
    case 'pending': return 'time';
    default: return 'ellipse-outline';
  }
}

function checksColor(status: VCSPRChecks['status'], tokens: ThemeTokens): string {
  switch (status) {
    case 'success': return tokens.status.success;
    case 'failure': return tokens.status.danger;
    case 'pending': return tokens.status.warning;
    default: return tokens.text.muted;
  }
}

function stateMeta(state: string, isDraft: boolean, tokens: ThemeTokens): { label: string; color: string } {
  if (isDraft) return { label: 'Draft', color: tokens.text.muted };
  switch (state.toUpperCase()) {
    case 'OPEN': return { label: 'Open', color: tokens.status.success };
    case 'MERGED': return { label: 'Merged', color: tokens.accent.primary };
    case 'CLOSED': return { label: 'Closed', color: tokens.status.danger };
    default: return { label: state, color: tokens.text.muted };
  }
}

function mergeStateTitle(status: VCSPRMergeStateStatus, mergeable: boolean | null): string {
  if (mergeable === false) return 'Has conflicts';
  switch (status) {
    case 'CLEAN': return 'Ready to merge';
    case 'HAS_HOOKS': return 'Mergeable with hooks';
    case 'UNSTABLE': return 'Mergeable with failing checks';
    case 'BEHIND': return 'Branch is out of date';
    case 'BLOCKED': return 'Merge blocked';
    case 'DIRTY': return 'Has conflicts';
    case 'DRAFT': return 'Draft pull request';
    default: return 'Merge state unknown';
  }
}

function mergeStateMessage(status: VCSPRMergeStateStatus, mergeable: boolean | null): string | null {
  if (mergeable === false) return 'Resolve conflicts before merging.';
  switch (status) {
    case 'BEHIND': return 'Update the branch to merge the latest base.';
    case 'BLOCKED': return 'Required reviews or status checks are not satisfied.';
    case 'DIRTY': return 'Resolve conflicts before merging.';
    case 'DRAFT': return 'Mark the PR ready for review to enable merging.';
    case 'UNSTABLE': return 'Some checks are failing but merging is allowed.';
    default: return null;
  }
}

function mergeStateIcon(
  status: VCSPRMergeStateStatus,
  mergeable: boolean | null,
): React.ComponentProps<typeof Ionicons>['name'] {
  if (mergeable === false) return 'alert-circle';
  switch (status) {
    case 'CLEAN': return 'checkmark-circle';
    case 'HAS_HOOKS':
    case 'UNSTABLE': return 'alert-circle-outline';
    case 'BEHIND': return 'arrow-down-circle-outline';
    case 'BLOCKED': return 'lock-closed-outline';
    case 'DIRTY': return 'alert-circle';
    case 'DRAFT': return 'document-outline';
    default: return 'help-circle-outline';
  }
}

function mergeStateColor(
  status: VCSPRMergeStateStatus,
  mergeable: boolean | null,
  tokens: ThemeTokens,
): string {
  if (mergeable === false) return tokens.status.danger;
  switch (status) {
    case 'CLEAN': return tokens.status.success;
    case 'UNSTABLE':
    case 'HAS_HOOKS':
    case 'BEHIND': return tokens.status.warning;
    case 'BLOCKED':
    case 'DIRTY': return tokens.status.danger;
    default: return tokens.text.muted;
  }
}

function methodLabel(method: VCSMergeMethod): string {
  switch (method) {
    case 'merge': return 'Create merge commit for';
    case 'squash': return 'Squash and merge';
    case 'rebase': return 'Rebase and merge';
  }
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 16, paddingBottom: 32 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 12 },
  headerCard: {
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    padding: 14,
    gap: 6,
  },
  headerTop: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  headerTitle: { fontSize: 18, fontWeight: '600', flex: 1 },
  headerBranches: { fontSize: 13 },
  checksGrid: { flexDirection: 'row', paddingHorizontal: 14, paddingVertical: 12 },
  metric: { flex: 1, alignItems: 'center', gap: 2 },
  metricCount: { fontSize: 22, fontWeight: '700' },
  metricLabel: { fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5 },
  mergeActions: { gap: 8 },
});
