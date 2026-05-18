import { Ionicons } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { useTokens } from '@/theme';
import type { ThemeTokens } from '@/theme';
import type { VCSDiffRow, VCSDiffRowKind } from '@/transport';

import { useGitDiff } from '../gitStore';
import { ErrorText, MutedText } from '../ui';

type Props = {
  projectId: string;
  filePath: string;
};

const MONO_FONT = Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' });
const LINE_NUMBER_WIDTH = 44;

export function FileDiffScreen({ projectId, filePath }: Props) {
  const tokens = useTokens();
  const { diff, loading, error, reload, loadFull } = useGitDiff(projectId, filePath);
  const [wrap, setWrap] = useState(false);

  const colors = useMemo(() => diffColors(tokens), [tokens]);

  const body = renderBody({
    diff,
    loading,
    error,
    reload,
    loadFull,
    wrap,
    colors,
    tokens,
    filePath,
  });

  return (
    <View style={styles.root}>
      <View style={[styles.summary, { borderBottomColor: tokens.border.subtle }]}>
        <Text style={[styles.path, { color: tokens.text.primary }]} numberOfLines={1}>
          {filePath}
        </Text>
        {diff && !diff.isBinary ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={wrap ? 'Disable word wrap' : 'Enable word wrap'}
            onPress={() => setWrap((v) => !v)}
            hitSlop={8}
            style={({ pressed }) => [styles.wrapButton, { opacity: pressed ? 0.5 : 1 }]}>
            <Ionicons
              name={wrap ? 'return-down-back' : 'swap-horizontal'}
              size={18}
              color={wrap ? tokens.accent.primary : tokens.text.muted}
            />
          </Pressable>
        ) : null}
        {diff ? (
          <View style={styles.stats}>
            <Text style={[styles.statText, { color: tokens.status.success }]}>+{diff.additions}</Text>
            <Text style={[styles.statText, { color: tokens.status.danger }]}>-{diff.deletions}</Text>
          </View>
        ) : null}
      </View>
      {body}
    </View>
  );
}

type RenderProps = {
  diff: ReturnType<typeof useGitDiff>['diff'];
  loading: boolean;
  error: string | null;
  reload: () => void;
  loadFull: () => void;
  wrap: boolean;
  colors: DiffColors;
  tokens: ThemeTokens;
  filePath: string;
};

function renderBody({ diff, loading, error, reload, loadFull, wrap, colors, tokens }: RenderProps) {
  if (!diff && loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={tokens.accent.primary} />
      </View>
    );
  }

  if (!diff && error) {
    return (
      <View style={styles.center}>
        <ErrorText>{error}</ErrorText>
      </View>
    );
  }

  if (!diff) {
    return (
      <View style={styles.center}>
        <MutedText>No diff available.</MutedText>
      </View>
    );
  }

  if (diff.isBinary) {
    return (
      <View style={styles.center}>
        <MutedText>Binary file — no preview.</MutedText>
      </View>
    );
  }

  if (diff.rows.length === 0) {
    return (
      <View style={styles.center}>
        <MutedText>No changes for this file.</MutedText>
      </View>
    );
  }

  const rows = diff.rows.map((row, index) => (
    <DiffRowView
      key={`${index}-${row.kind}-${row.oldLineNumber ?? ''}-${row.newLineNumber ?? ''}`}
      row={row}
      colors={colors}
      wrap={wrap}
    />
  ));

  return (
    <ScrollView
      style={styles.body}
      contentContainerStyle={styles.bodyContent}
      refreshControl={
        <RefreshControl refreshing={loading} onRefresh={reload} tintColor={tokens.text.muted} />
      }
      showsVerticalScrollIndicator={false}>
      {wrap ? (
        <View>{rows}</View>
      ) : (
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          <View>{rows}</View>
        </ScrollView>
      )}

      {diff.truncated ? (
        <View style={styles.truncated}>
          <MutedText>Showing a preview. Some lines were truncated.</MutedText>
          <Pressable
            accessibilityRole="button"
            onPress={loadFull}
            style={({ pressed }) => [
              styles.loadFullButton,
              {
                backgroundColor: tokens.accent.primary,
                opacity: pressed || loading ? 0.6 : 1,
              },
            ]}
            disabled={loading}>
            <Text style={[styles.loadFullText, { color: tokens.accent.contrast }]}>
              {loading ? 'Loading…' : 'Load full diff'}
            </Text>
          </Pressable>
        </View>
      ) : null}

      {error ? <ErrorText>{error}</ErrorText> : null}
    </ScrollView>
  );
}

type DiffColors = {
  additionBg: string;
  deletionBg: string;
  hunkBg: string;
  collapsedBg: string;
  gutterFg: string;
  gutterBorder: string;
  hunkFg: string;
  textFg: string;
};

function diffColors(tokens: ThemeTokens): DiffColors {
  const isDark = tokens.mode === 'dark';
  return {
    additionBg: isDark ? 'rgba(52, 211, 153, 0.14)' : 'rgba(22, 163, 74, 0.12)',
    deletionBg: isDark ? 'rgba(248, 113, 113, 0.16)' : 'rgba(220, 38, 38, 0.12)',
    hunkBg: tokens.surface.tertiary,
    collapsedBg: tokens.surface.secondary,
    gutterFg: tokens.text.muted,
    gutterBorder: tokens.border.subtle,
    hunkFg: tokens.text.secondary,
    textFg: tokens.text.primary,
  };
}

function DiffRowView({
  row,
  colors,
  wrap,
}: {
  row: VCSDiffRow;
  colors: DiffColors;
  wrap: boolean;
}) {
  if (row.kind === 'hunk') {
    return (
      <View style={[styles.row, { backgroundColor: colors.hunkBg }]}>
        <View style={[styles.gutter, { borderRightColor: colors.gutterBorder, width: LINE_NUMBER_WIDTH * 2 }]} />
        <Text style={[styles.lineText, wrap ? styles.lineTextWrap : null, { color: colors.hunkFg }]}>
          {row.text}
        </Text>
      </View>
    );
  }

  if (row.kind === 'collapsed') {
    return (
      <View style={[styles.row, { backgroundColor: colors.collapsedBg }]}>
        <View style={[styles.gutter, { borderRightColor: colors.gutterBorder, width: LINE_NUMBER_WIDTH * 2 }]} />
        <Text
          style={[
            styles.lineText,
            wrap ? styles.lineTextWrap : null,
            { color: colors.gutterFg, fontStyle: 'italic' },
          ]}>
          {row.text}
        </Text>
      </View>
    );
  }

  const bg = rowBackground(row.kind, colors);
  return (
    <View style={[styles.row, { backgroundColor: bg }]}>
      <Text
        style={[
          styles.lineNumber,
          { color: colors.gutterFg, borderRightColor: colors.gutterBorder, width: LINE_NUMBER_WIDTH },
        ]}>
        {formatLineNumber(row.oldLineNumber)}
      </Text>
      <Text
        style={[
          styles.lineNumber,
          { color: colors.gutterFg, borderRightColor: colors.gutterBorder, width: LINE_NUMBER_WIDTH },
        ]}>
        {formatLineNumber(row.newLineNumber)}
      </Text>
      <Text style={[styles.lineText, wrap ? styles.lineTextWrap : null, { color: colors.textFg }]}>
        {row.text}
      </Text>
    </View>
  );
}

function rowBackground(kind: VCSDiffRowKind, colors: DiffColors): string {
  if (kind === 'addition') return colors.additionBg;
  if (kind === 'deletion') return colors.deletionBg;
  return 'transparent';
}

function formatLineNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value);
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 12 },
  summary: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  path: { flex: 1, fontSize: 13, fontWeight: '600' },
  wrapButton: { padding: 4 },
  stats: { flexDirection: 'row', gap: 10 },
  statText: { fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  body: { flex: 1 },
  bodyContent: { paddingBottom: 32 },
  row: { flexDirection: 'row', alignItems: 'flex-start', minHeight: 20 },
  gutter: { borderRightWidth: StyleSheet.hairlineWidth },
  lineNumber: {
    fontFamily: MONO_FONT,
    fontSize: 11,
    textAlign: 'right',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRightWidth: StyleSheet.hairlineWidth,
    fontVariant: ['tabular-nums'],
  },
  lineText: {
    fontFamily: MONO_FONT,
    fontSize: 12,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  lineTextWrap: { flex: 1, flexShrink: 1 },
  truncated: { padding: 16, gap: 12, alignItems: 'flex-start' },
  loadFullButton: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
  },
  loadFullText: { fontSize: 13, fontWeight: '600' },
});
