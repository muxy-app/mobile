import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { KeyboardAwareScrollView } from 'react-native-keyboard-controller';

import { client } from '@/state';
import { useTokens } from '@/theme';

import { ErrorText, Field, MutedText, PrimaryButton } from '../ui';
import type { GitRoute } from '../GitScreens';

type Props = {
  projectId: string;
  setRoute: (r: GitRoute) => void;
};

export function NewWorktreeScreen({ projectId, setRoute }: Props) {
  const tokens = useTokens();
  const [name, setName] = useState('');
  const [branch, setBranch] = useState('');
  const [createBranch, setCreateBranch] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    const trimmedName = name.trim();
    const trimmedBranch = branch.trim();
    if (!trimmedName || !trimmedBranch) return;
    setSubmitting(true);
    setError(null);
    try {
      await client.request('vcsAddWorktree', {
        type: 'vcsAddWorktree',
        value: {
          projectID: projectId,
          name: trimmedName,
          branch: trimmedBranch,
          createBranch,
        },
      });
      setRoute({ name: 'worktrees' });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create worktree');
      setSubmitting(false);
    }
  };

  return (
    <KeyboardAwareScrollView
      style={styles.root}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      bottomOffset={24}>
      <Field label="Worktree name" value={name} onChangeText={setName} placeholder="my-feature" autoFocus />
      <Field label="Branch" value={branch} onChangeText={setBranch} placeholder="feature/awesome" />

      <Pressable
        onPress={() => setCreateBranch((v) => !v)}
        style={({ pressed }) => [
          styles.toggle,
          {
            backgroundColor: tokens.surface.secondary,
            borderColor: tokens.border.subtle,
            opacity: pressed ? 0.85 : 1,
          },
        ]}>
        <View
          style={[
            styles.checkbox,
            {
              borderColor: createBranch ? tokens.accent.primary : tokens.border.strong,
              backgroundColor: createBranch ? tokens.accent.primary : 'transparent',
            },
          ]}>
          {createBranch ? (
            <View style={[styles.checkInner, { borderColor: tokens.accent.contrast }]} />
          ) : null}
        </View>
        <View style={styles.toggleBody}>
          <Text style={[styles.toggleLabel, { color: tokens.text.primary }]}>
            Create new branch
          </Text>
          <Text style={[styles.toggleHint, { color: tokens.text.muted }]}>
            {createBranch ? 'Will create the branch from current HEAD.' : 'Will check out an existing branch.'}
          </Text>
        </View>
      </Pressable>

      <PrimaryButton
        label="Create worktree"
        onPress={submit}
        loading={submitting}
        disabled={!name.trim() || !branch.trim()}
      />

      <MutedText>The worktree directory is created next to the primary checkout.</MutedText>

      {error ? <ErrorText>{error}</ErrorText> : null}
    </KeyboardAwareScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 16 },
  toggle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 14,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkInner: {
    width: 10,
    height: 6,
    borderLeftWidth: 2,
    borderBottomWidth: 2,
    transform: [{ rotate: '-45deg' }],
    marginTop: -2,
  },
  toggleBody: { flex: 1, gap: 2 },
  toggleLabel: { fontSize: 15, fontWeight: '500' },
  toggleHint: { fontSize: 12 },
});
