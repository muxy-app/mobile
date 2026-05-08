import { useState } from 'react';
import { StyleSheet } from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-controller';

import { client } from '@/state';

import { ErrorText, Field, PrimaryButton } from '../ui';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
};

export function NewBranchScreen({ projectId, setRoute }: Props) {
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    setSubmitting(true);
    setError(null);
    try {
      await client.request('vcsCreateBranch', {
        type: 'vcsCreateBranch',
        value: { projectID: projectId, name: trimmed },
      });
      setRoute({ name: 'branches' });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create branch');
      setSubmitting(false);
    }
  };

  return (
    <KeyboardAwareScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      bottomOffset={24}>
      <Field label="Branch name" value={name} onChangeText={setName} placeholder="feature/awesome" autoFocus />
      <PrimaryButton
        label="Create branch"
        onPress={submit}
        loading={submitting}
        disabled={!name.trim()}
      />
      {error ? <ErrorText>{error}</ErrorText> : null}
    </KeyboardAwareScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 16 },
});
