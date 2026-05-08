import { useState } from 'react';
import { ScrollView, StyleSheet } from 'react-native';

import { KeyboardAwareScrollView } from 'react-native-keyboard-controller';

import { client } from '@/state';

import { useVCSStatus } from '../useVCSStatus';
import { ErrorText, Field, MutedText, PrimaryButton, Row } from '../ui';
import { useTokens } from '@/theme';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
};

export function CreatePRScreen({ projectId, setRoute }: Props) {
  const tokens = useTokens();
  const { status } = useVCSStatus(projectId);

  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [baseBranch, setBaseBranch] = useState('');
  const [draft, setDraft] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [createdUrl, setCreatedUrl] = useState<string | null>(null);

  const effectiveBase = baseBranch.trim() || status?.defaultBranch || '';

  const submit = async () => {
    if (!title.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const res = await client.request('vcsCreatePR', {
        type: 'vcsCreatePR',
        value: {
          projectID: projectId,
          title: title.trim(),
          body: body.trim(),
          baseBranch: baseBranch.trim() || undefined,
          draft,
        },
      });
      setCreatedUrl(res.value.url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create pull request');
    } finally {
      setSubmitting(false);
    }
  };

  if (createdUrl) {
    return (
      <ScrollView style={styles.root} contentContainerStyle={styles.content}>
        <Row
          icon="checkmark-circle"
          iconColor={tokens.status.success}
          title="Pull request created"
          subtitle={createdUrl}
          onPress={() =>
            import('expo-web-browser').then((wb) => wb.openBrowserAsync(createdUrl)).catch(() => {})
          }
        />
        <PrimaryButton label="Done" onPress={() => setRoute({ name: 'overview' })} />
      </ScrollView>
    );
  }

  return (
    <KeyboardAwareScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      bottomOffset={24}>
      <Field
        label="Title"
        value={title}
        onChangeText={setTitle}
        placeholder={status ? `${status.branch} → ${effectiveBase || 'default'}` : 'Pull request title'}
        autoFocus
        autoCapitalize="sentences"
      />
      <Field
        label="Description"
        value={body}
        onChangeText={setBody}
        placeholder="What does this change?"
        multiline
        autoCapitalize="sentences"
      />
      <Field
        label="Base branch"
        value={baseBranch}
        onChangeText={setBaseBranch}
        placeholder={status?.defaultBranch || 'main'}
      />
      <Row
        icon={draft ? 'checkbox' : 'square-outline'}
        iconColor={draft ? tokens.accent.primary : tokens.text.muted}
        title="Open as draft"
        onPress={() => setDraft((v) => !v)}
      />
      <PrimaryButton label="Create pull request" onPress={submit} loading={submitting} disabled={!title.trim()} />
      <MutedText>From {status?.branch ?? '…'} into {effectiveBase || 'default'}.</MutedText>
      {error ? <ErrorText>{error}</ErrorText> : null}
    </KeyboardAwareScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 16 },
});
