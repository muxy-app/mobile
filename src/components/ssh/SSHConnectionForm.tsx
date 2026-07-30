import { Redirect, Stack, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  KeyboardAwareScrollView,
  KeyboardController,
  KeyboardToolbar,
} from 'react-native-keyboard-controller';

import { HeaderIconButton } from '@/components/HeaderIconButton';
import { newEntryId } from '@/state';
import {
  buildSSHNativeConfig,
  getSSHSupport,
  readKnownHostFingerprint,
  readSSHCredential,
  saveSSHConnection,
  testConnection,
  useSSHStore,
  validateSSHConnectionInput,
  type SSHAuthType,
  type SSHConnection,
} from '@/ssh';
import { useTokens } from '@/theme';

import { useSSHHostKeyPrompt } from './useSSHHostKeyPrompt';

type Props = {
  connectionId?: string;
};

type Phase = 'idle' | 'loading' | 'testing' | 'saving';

export function SSHConnectionForm({ connectionId }: Props) {
  const tokens = useTokens();
  const hasHydrated = useSSHStore((state) => state.hasHydrated);
  const existing = useSSHStore((state) =>
    connectionId
      ? state.connections.find(
          (connection) => connection.id === connectionId,
        )
      : undefined,
  );
  const support = getSSHSupport();

  if (!hasHydrated) return null;
  if (support === 'hidden') return <Redirect href="/" />;

  if (support === 'disabled-expo-go') {
    return (
      <View
        style={[
          styles.missing,
          { backgroundColor: tokens.surface.primary },
        ]}>
        <Stack.Screen options={{ title: 'SSH' }} />
        <Text style={[styles.error, { color: tokens.text.primary }]}>
          SSH requires an iOS development build.
        </Text>
      </View>
    );
  }

  if (connectionId && !existing) {
    return (
      <View
        style={[
          styles.missing,
          { backgroundColor: tokens.surface.primary },
        ]}>
        <Stack.Screen options={{ title: 'SSH Connection' }} />
        <Text style={[styles.error, { color: tokens.status.danger }]}>
          This SSH connection no longer exists.
        </Text>
      </View>
    );
  }

  return (
    <HydratedSSHConnectionForm
      key={existing?.id ?? 'new'}
      existing={existing}
    />
  );
}

function HydratedSSHConnectionForm({
  existing,
}: {
  existing?: SSHConnection;
}) {
  const tokens = useTokens();
  const { back } = useRouter();
  const finalConnectionId = useRef(existing?.id ?? newEntryId()).current;
  const testConnectionId = useRef(newEntryId()).current;
  const trustedFingerprint = useRef<string | undefined>(undefined);
  const mountedRef = useRef(true);
  const operationIdRef = useRef(0);
  const operationInFlightRef = useRef(false);
  const savingRef = useRef(false);

  const [name, setName] = useState(existing?.name ?? '');
  const [host, setHost] = useState(existing?.host ?? '');
  const [port, setPort] = useState(String(existing?.port ?? 22));
  const [username, setUsername] = useState(existing?.username ?? '');
  const [authType, setAuthType] = useState<SSHAuthType>(
    existing?.authType ?? 'password',
  );
  const [password, setPassword] = useState('');
  const [privateKey, setPrivateKey] = useState('');
  const [passphrase, setPassphrase] = useState('');
  const [phase, setPhase] = useState<Phase>(
    existing ? 'loading' : 'idle',
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      operationIdRef.current += 1;
    };
  }, []);

  useEffect(() => {
    if (!existing) return;
    let active = true;
    readSSHCredential(existing.id)
      .then((credential) => {
        if (!active) return;
        if (!credential) {
          setError('The saved credentials could not be read.');
          setPhase('idle');
          return;
        }
        if (credential.type === 'password') {
          setPassword(credential.password);
        } else {
          setPrivateKey(credential.privateKey);
          setPassphrase(credential.passphrase ?? '');
        }
        setPhase('idle');
      })
      .catch((loadError) => {
        if (!active) return;
        setError(
          loadError instanceof Error
            ? loadError.message
            : 'The saved credentials could not be read.',
        );
        setPhase('idle');
      });
    return () => {
      active = false;
    };
  }, [existing]);

  const handlePromptError = useCallback((message: string) => {
    setError(message);
  }, []);
  const handleTrust = useCallback(async (fingerprint: string) => {
    trustedFingerprint.current = fingerprint;
  }, []);

  useSSHHostKeyPrompt({
    connectionId: testConnectionId,
    onTrust: handleTrust,
    onError: handlePromptError,
  });

  const busy = phase !== 'idle';

  const handleSave = useCallback(async () => {
    if (operationInFlightRef.current) return;
    KeyboardController.dismiss();
    setError(null);
    trustedFingerprint.current = undefined;

    const validation = validateSSHConnectionInput({
      name,
      host,
      port,
      username,
      authType,
      password,
      privateKey,
      passphrase,
    });
    if (!validation.valid) {
      setError(validation.message);
      return;
    }

    operationInFlightRef.current = true;
    const operationId = operationIdRef.current + 1;
    operationIdRef.current = operationId;
    const now = new Date().toISOString();
    const connection: SSHConnection = {
      id: finalConnectionId,
      name: validation.value.name,
      host: validation.value.host,
      port: validation.value.port,
      username: validation.value.username,
      authType: validation.value.authType,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };
    const endpointUnchanged =
      existing !== undefined &&
      existing.host === connection.host &&
      existing.port === connection.port;

    try {
      setPhase('testing');
      const knownFingerprint = endpointUnchanged
        ? (await readKnownHostFingerprint(finalConnectionId)) ?? undefined
        : undefined;
      const config = buildSSHNativeConfig(
        testConnectionId,
        connection,
        validation.value.credential,
        { cols: 80, rows: 24 },
        knownFingerprint,
      );
      await testConnection(config);
      if (
        !mountedRef.current ||
        operationIdRef.current !== operationId
      ) {
        return;
      }

      savingRef.current = true;
      setPhase('saving');
      await saveSSHConnection(
        connection,
        validation.value.credential,
        trustedFingerprint.current,
      );
      if (
        !mountedRef.current ||
        operationIdRef.current !== operationId
      ) {
        return;
      }
      back();
    } catch (saveError) {
      if (
        !mountedRef.current ||
        operationIdRef.current !== operationId
      ) {
        return;
      }
      setPhase('idle');
      setError(
        saveError instanceof Error
          ? saveError.message
          : 'Could not connect to the SSH server.',
      );
    } finally {
      if (operationIdRef.current === operationId) {
        operationInFlightRef.current = false;
        savingRef.current = false;
      }
    }
  }, [
    authType,
    back,
    existing,
    finalConnectionId,
    host,
    name,
    passphrase,
    password,
    port,
    privateKey,
    testConnectionId,
    username,
  ]);

  const handleClose = useCallback(() => {
    if (savingRef.current) return;
    operationIdRef.current += 1;
    back();
  }, [back]);

  return (
    <View
      style={[
        styles.root,
        { backgroundColor: tokens.surface.primary },
      ]}>
      <Stack.Screen
        options={{
          title: existing ? 'Edit SSH Connection' : 'Add SSH Connection',
          gestureEnabled: !busy,
          headerLeft:
            phase === 'saving'
              ? () => null
              : () => (
                  <HeaderIconButton
                    icon="close"
                    accessibilityLabel="Close"
                    onPress={handleClose}
                  />
                ),
        }}
      />
      <KeyboardAwareScrollView
        style={styles.scroll}
        bottomOffset={48}
        contentInsetAdjustmentBehavior="automatic"
        keyboardDismissMode={
          Platform.OS === 'ios' ? 'interactive' : 'on-drag'
        }
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.content}>
        <Section title="Server">
          <Field
            label="Name"
            value={name}
            onChangeText={setName}
            placeholder="Production server"
            editable={!busy}
            autoCapitalize="words"
            autoCorrect={false}
          />
          <Divider />
          <Field
            label="Host"
            value={host}
            onChangeText={setHost}
            placeholder="server.example.com"
            editable={!busy}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType={Platform.OS === 'ios' ? 'url' : 'default'}
          />
          <Divider />
          <Field
            label="Port"
            value={port}
            onChangeText={setPort}
            placeholder="22"
            editable={!busy}
            keyboardType="number-pad"
          />
          <Divider />
          <Field
            label="Username"
            value={username}
            onChangeText={setUsername}
            placeholder="deploy"
            editable={!busy}
            autoCapitalize="none"
            autoCorrect={false}
          />
        </Section>

        <View style={styles.section}>
          <Text
            style={[styles.sectionLabel, { color: tokens.text.muted }]}>
            Authentication
          </Text>
          <View
            style={[
              styles.segment,
              {
                backgroundColor: tokens.surface.secondary,
                borderColor: tokens.border.subtle,
              },
            ]}>
            <AuthOption
              label="Password"
              selected={authType === 'password'}
              disabled={busy}
              onPress={() => setAuthType('password')}
            />
            <AuthOption
              label="Private Key"
              selected={authType === 'privateKey'}
              disabled={busy}
              onPress={() => setAuthType('privateKey')}
            />
          </View>
          <View
            style={[
              styles.card,
              {
                backgroundColor: tokens.surface.secondary,
                borderColor: tokens.border.subtle,
              },
            ]}>
            {authType === 'password' ? (
              <Field
                label="Password"
                value={password}
                onChangeText={setPassword}
                placeholder="Password"
                editable={!busy}
                secureTextEntry
                autoCapitalize="none"
                autoCorrect={false}
              />
            ) : (
              <>
                <Field
                  label="Private Key"
                  value={privateKey}
                  onChangeText={setPrivateKey}
                  placeholder="-----BEGIN OPENSSH PRIVATE KEY-----"
                  editable={!busy}
                  multiline
                  autoCapitalize="none"
                  autoCorrect={false}
                  style={styles.privateKey}
                />
                <Divider />
                <Field
                  label="Passphrase"
                  value={passphrase}
                  onChangeText={setPassphrase}
                  placeholder="Optional"
                  editable={!busy}
                  secureTextEntry
                  autoCapitalize="none"
                  autoCorrect={false}
                />
              </>
            )}
          </View>
        </View>

        {busy ? (
          <View style={styles.status}>
            <ActivityIndicator
              size="small"
              color={tokens.accent.primary}
            />
            <Text
              style={[styles.statusText, { color: tokens.text.secondary }]}>
              {phase === 'loading'
                ? 'Loading credentials…'
                : phase === 'testing'
                  ? 'Testing connection…'
                  : 'Saving securely…'}
            </Text>
          </View>
        ) : null}

        {error ? (
          <Text style={[styles.error, { color: tokens.status.danger }]}>
            {error}
          </Text>
        ) : null}

        <Pressable
          accessibilityRole="button"
          disabled={busy}
          onPress={() => {
            void handleSave();
          }}
          style={({ pressed }) => [
            styles.cta,
            {
              backgroundColor: tokens.accent.primary,
              opacity: busy ? 0.6 : pressed ? 0.85 : 1,
            },
          ]}>
          <Text
            style={[styles.ctaLabel, { color: tokens.accent.contrast }]}>
            {existing ? 'Save' : 'Add'}
          </Text>
        </Pressable>

        <Text style={[styles.hint, { color: tokens.text.muted }]}>
          The connection is tested before credentials are saved.
        </Text>
      </KeyboardAwareScrollView>
      <KeyboardToolbar />
    </View>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  const tokens = useTokens();
  return (
    <View style={styles.section}>
      <Text style={[styles.sectionLabel, { color: tokens.text.muted }]}>
        {title}
      </Text>
      <View
        style={[
          styles.card,
          {
            backgroundColor: tokens.surface.secondary,
            borderColor: tokens.border.subtle,
          },
        ]}>
        {children}
      </View>
    </View>
  );
}

function Field({
  label,
  style,
  ...input
}: {
  label: string;
} & React.ComponentProps<typeof TextInput>) {
  const tokens = useTokens();
  return (
    <View style={styles.field}>
      <Text style={[styles.fieldLabel, { color: tokens.text.muted }]}>
        {label}
      </Text>
      <TextInput
        {...input}
        style={[styles.fieldInput, { color: tokens.text.primary }, style]}
        placeholderTextColor={tokens.text.muted}
      />
    </View>
  );
}

function Divider() {
  const tokens = useTokens();
  return (
    <View
      style={[styles.divider, { backgroundColor: tokens.border.subtle }]}
    />
  );
}

function AuthOption({
  label,
  selected,
  disabled,
  onPress,
}: {
  label: string;
  selected: boolean;
  disabled: boolean;
  onPress: () => void;
}) {
  const tokens = useTokens();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.segmentOption,
        {
          backgroundColor: selected
            ? tokens.accent.primary
            : 'transparent',
          opacity: pressed ? 0.8 : 1,
        },
      ]}>
      <Text
        style={[
          styles.segmentLabel,
          {
            color: selected
              ? tokens.accent.contrast
              : tokens.text.secondary,
          },
        ]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  scroll: { flex: 1 },
  missing: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  content: { padding: 16, gap: 16 },
  section: { gap: 6 },
  sectionLabel: {
    paddingHorizontal: 4,
    fontSize: 12,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  card: {
    borderRadius: 12,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  field: { paddingHorizontal: 16, paddingVertical: 10, gap: 4 },
  fieldLabel: {
    fontSize: 12,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  fieldInput: { fontSize: 16, paddingVertical: 4 },
  privateKey: {
    minHeight: 112,
    textAlignVertical: 'top',
    fontFamily: Platform.select({
      ios: 'Menlo',
      android: 'monospace',
    }),
    fontSize: 13,
  },
  divider: { height: StyleSheet.hairlineWidth },
  segment: {
    flexDirection: 'row',
    padding: 3,
    borderRadius: 10,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
  },
  segmentOption: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 8,
    borderRadius: 8,
    borderCurve: 'continuous',
  },
  segmentLabel: { fontSize: 14, fontWeight: '600' },
  status: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 4,
  },
  statusText: { fontSize: 14 },
  error: { fontSize: 14, paddingHorizontal: 4, textAlign: 'center' },
  cta: {
    paddingVertical: 14,
    borderRadius: 12,
    borderCurve: 'continuous',
    alignItems: 'center',
  },
  ctaLabel: { fontSize: 16, fontWeight: '600' },
  hint: { fontSize: 13, textAlign: 'center', paddingHorizontal: 16 },
});
