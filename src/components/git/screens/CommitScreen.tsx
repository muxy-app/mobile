import { useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-controller';

import { useTokens } from '@/theme';

import { useGitStatus, useGitStore } from '../gitStore';
import {
  Divider,
  ErrorText,
  Field,
  PrimaryButton,
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

export function CommitScreen({ projectId, setRoute }: Props) {
  const tokens = useTokens();
  const { status, loading, error: loadError } = useGitStatus(projectId);
  const commit = useGitStore((s) => s.commit);

  const [message, setMessage] = useState('');
  const [stageAll, setStageAll] = useState(true);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!status && loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={tokens.accent.primary} />
      </View>
    );
  }

  if (!status) {
    return (
      <View style={styles.center}>
        <ErrorText>{loadError ?? 'Could not load changes'}</ErrorText>
      </View>
    );
  }

  const allFiles = [
    ...status.stagedFiles.map((f) => ({ ...f, staged: true })),
    ...status.changedFiles.map((f) => ({ ...f, staged: false })),
  ];
  const totalStaged = status.stagedFiles.length;
  const totalUnstaged = status.changedFiles.length;
  const canCommit =
    message.trim().length > 0 && (stageAll ? allFiles.length > 0 : totalStaged > 0);

  const submit = async () => {
    setSubmitting(true);
    setSubmitError(null);
    try {
      await commit(projectId, message.trim(), stageAll);
      setRoute({ name: 'overview' });
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to commit');
      setSubmitting(false);
    }
  };

  return (
    <KeyboardAwareScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      bottomOffset={24}>
      <Field
        label="Commit message"
        value={message}
        onChangeText={setMessage}
        placeholder="Describe your change"
        multiline
        autoFocus
        autoCapitalize="sentences"
      />

      <Row
        icon={stageAll ? 'checkbox' : 'square-outline'}
        iconColor={stageAll ? tokens.accent.primary : tokens.text.muted}
        title="Stage all changes"
        subtitle={
          stageAll
            ? `Will stage ${totalStaged + totalUnstaged} file${totalStaged + totalUnstaged === 1 ? '' : 's'}`
            : `Commit ${totalStaged} already-staged file${totalStaged === 1 ? '' : 's'}`
        }
        onPress={() => setStageAll((v) => !v)}
      />

      <PrimaryButton
        label="Commit"
        onPress={submit}
        loading={submitting}
        disabled={!canCommit}
      />

      {submitError ? <ErrorText>{submitError}</ErrorText> : null}

      {allFiles.length > 0 ? (
        <Section title="Changes">
          {allFiles.map((f, i) => {
            const meta = tokensStatusForFile(f.status, tokens);
            return (
              <View key={`${f.staged}-${f.path}`}>
                {i > 0 ? <Divider /> : null}
                <Row
                  title={fileNameOf(f.path)}
                  subtitle={f.path}
                  trailing={
                    <View style={styles.fileTrailing}>
                      {!f.staged ? (
                        <Text style={[styles.unstaged, { color: tokens.text.muted }]}>unstaged</Text>
                      ) : null}
                      <StatusPill label={meta.label} color={meta.color} textColor={tokens.accent.contrast} />
                    </View>
                  }
                />
              </View>
            );
          })}
        </Section>
      ) : null}
    </KeyboardAwareScrollView>
  );
}

function fileNameOf(path: string): string {
  const idx = path.lastIndexOf('/');
  return idx >= 0 ? path.slice(idx + 1) : path;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 16, paddingBottom: 32 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  fileTrailing: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  unstaged: { fontSize: 11, fontStyle: 'italic' },
});
