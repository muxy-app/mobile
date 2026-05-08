import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { useBillingStore } from '@/billing';
import { HeaderIconButton } from '@/components/HeaderIconButton';
import {
  getOrCreateInstallToken,
  pairWithDevice,
  PairingError,
  resolveDeviceName,
  useDevicesStore,
  type DeviceEntry,
  type PairingPhase,
} from '@/state';
import { useTokens } from '@/theme';

type Phase = 'idle' | PairingPhase | 'success' | 'error';

const DEFAULT_PORT = '4865';

export default function AddDeviceScreen() {
  const tokens = useTokens();
  const router = useRouter();
  const params = useLocalSearchParams<{ entryId?: string; host?: string; port?: string; label?: string }>();
  const isRepair = Boolean(params.entryId);

  const ensureInstallDeviceID = useDevicesStore((s) => s.ensureInstallDeviceID);
  const upsertDevice = useDevicesStore((s) => s.upsertDevice);
  const setActiveDevice = useDevicesStore((s) => s.setActiveDevice);
  const setNeedsRepair = useDevicesStore((s) => s.setNeedsRepair);
  const existingEntry = useDevicesStore((s) =>
    params.entryId ? s.devices.find((d) => d.id === params.entryId) : null,
  );

  const [label, setLabel] = useState(params.label ?? '');
  const [host, setHost] = useState(params.host ?? '');
  const [port, setPort] = useState(params.port ?? DEFAULT_PORT);
  const [phase, setPhase] = useState<Phase>('idle');
  const [error, setError] = useState<string | null>(null);

  const busy = phase === 'connecting' || phase === 'authenticating' || phase === 'awaiting-approval';

  const onPair = async () => {
    setError(null);
    const trimmedHost = host.trim();
    const portNum = parseInt(port, 10);
    if (!trimmedHost) {
      setError('Enter your Mac’s host or IP.');
      return;
    }
    if (!Number.isFinite(portNum) || portNum < 1 || portNum > 65_535) {
      setError('Port must be between 1 and 65535.');
      return;
    }

    const installDeviceID = ensureInstallDeviceID();
    const token = await getOrCreateInstallToken();

    try {
      setPhase('connecting');
      const result = await pairWithDevice({
        host: trimmedHost,
        port: portNum,
        installDeviceID,
        installDeviceName: resolveDeviceName(),
        token,
        onPhase: (p) => setPhase(p),
      });

      const finalEntryId = isRepair && existingEntry ? existingEntry.id : result.entryId;
      const entry: DeviceEntry = {
        id: finalEntryId,
        label: label.trim() || trimmedHost,
        host: trimmedHost,
        port: portNum,
        pairedAt: existingEntry?.pairedAt ?? new Date().toISOString(),
        pairing: result.pairing,
      };

      upsertDevice(entry);
      setNeedsRepair(finalEntryId, false);
      setActiveDevice(finalEntryId);

      await useBillingStore.getState().startTrialIfAbsent();

      setPhase('success');
      router.back();
    } catch (err) {
      const message =
        err instanceof PairingError
          ? err.kind === 'denied'
            ? 'Pairing was denied on the Mac.'
            : err.kind === 'timeout'
              ? 'Pairing timed out. Try again and approve faster.'
              : err.kind === 'connect'
                ? 'Could not reach Muxy. Check the host and port and that the Mobile server is enabled.'
                : err.message
          : err instanceof Error
            ? err.message
            : 'Something went wrong.';
      setPhase('error');
      setError(message);
    }
  };

  const phaseHint = (() => {
    switch (phase) {
      case 'connecting':
        return 'Connecting to Muxy…';
      case 'authenticating':
        return 'Checking saved credentials…';
      case 'awaiting-approval':
        return 'Open Muxy on your Mac and approve this device.';
      case 'success':
        return 'Paired ✓';
      default:
        return null;
    }
  })();

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <Stack.Screen
        options={{
          title: isRepair ? 'Re-pair device' : 'Add device',
          headerLeft: () => (
            <HeaderIconButton icon="close" accessibilityLabel="Close" onPress={() => router.back()} />
          ),
        }}
      />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View
          style={[styles.card, { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle }]}>
          <Field
            label="Name"
            value={label}
            onChangeText={setLabel}
            placeholder="Work Mac"
            editable={!busy}
            autoCapitalize="words"
            autoCorrect={false}
          />
          <Divider />
          <Field
            label="Host"
            value={host}
            onChangeText={setHost}
            placeholder="192.168.1.10 or your-mac.local"
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
            placeholder={DEFAULT_PORT}
            editable={!busy}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="number-pad"
          />
        </View>

        {phaseHint ? (
          <View style={styles.statusRow}>
            {busy ? <ActivityIndicator color={tokens.accent.primary} /> : null}
            <Text style={[styles.statusText, { color: tokens.text.secondary }]}>{phaseHint}</Text>
          </View>
        ) : null}

        {error ? <Text style={[styles.error, { color: tokens.status.danger }]}>{error}</Text> : null}

        <Pressable
          accessibilityRole="button"
          disabled={busy}
          onPress={onPair}
          style={({ pressed }) => [
            styles.cta,
            {
              backgroundColor: tokens.accent.primary,
              opacity: busy ? 0.6 : pressed ? 0.85 : 1,
            },
          ]}>
          <Text style={[styles.ctaLabel, { color: tokens.accent.contrast }]}>
            {phase === 'error' ? 'Try again' : 'Pair'}
          </Text>
        </Pressable>

        <Text style={[styles.hint, { color: tokens.text.muted }]}>
          On your Mac, open Muxy › Settings › Mobile and toggle the server on.
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Field({
  label,
  ...input
}: {
  label: string;
} & React.ComponentProps<typeof TextInput>) {
  const tokens = useTokens();
  return (
    <View style={styles.field}>
      <Text style={[styles.fieldLabel, { color: tokens.text.muted }]}>{label}</Text>
      <TextInput
        {...input}
        style={[styles.fieldInput, { color: tokens.text.primary }]}
        placeholderTextColor={tokens.text.muted}
      />
    </View>
  );
}

function Divider() {
  const tokens = useTokens();
  return <View style={[styles.divider, { backgroundColor: tokens.border.subtle }]} />;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  content: { padding: 16, gap: 12 },
  card: {
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  field: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 4,
  },
  fieldLabel: {
    fontSize: 12,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  fieldInput: {
    fontSize: 16,
    paddingVertical: 4,
  },
  divider: { height: StyleSheet.hairlineWidth },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 4 },
  statusText: { fontSize: 14 },
  error: { fontSize: 14, paddingHorizontal: 4 },
  cta: {
    marginTop: 4,
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
  },
  ctaLabel: { fontSize: 16, fontWeight: '600' },
  hint: {
    fontSize: 13,
    textAlign: 'center',
    paddingHorizontal: 16,
    marginTop: 8,
  },
});
