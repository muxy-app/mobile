import { Redirect, Stack, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';

import { isBillingEnforced, useEntitlement } from '@/billing';
import { EntitlementFooter } from '@/components/billing/EntitlementFooter';
import { DeviceRow } from '@/components/DeviceRow';
import { HeaderIconButton } from '@/components/HeaderIconButton';
import { DEMO_DEVICE_ID } from '@/demo/demoBackend';
import { useDevicesStore, useSettingsStore, type DeviceEntry } from '@/state';
import { useTokens } from '@/theme';

export default function DevicesScreen() {
  const tokens = useTokens();
  const router = useRouter();

  const hasHydrated = useDevicesStore((s) => s.hasHydrated);
  const settingsHydrated = useSettingsStore((s) => s.hasHydrated);
  const hasOnboarded = useSettingsStore((s) => s.hasOnboarded);
  const demoMode = useSettingsStore((s) => s.demoMode);
  const devices = useDevicesStore((s) => s.devices);
  const setActiveDevice = useDevicesStore((s) => s.setActiveDevice);
  const removeDevice = useDevicesStore((s) => s.removeDevice);
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const connectionError = useDevicesStore((s) => s.connectionError);
  const entitlement = useEntitlement();

  const [pendingId, setPendingId] = useState<string | null>(null);
  const [errorByDevice, setErrorByDevice] = useState<Record<string, string>>({});

  const visibleDevices = useMemo(
    () => (demoMode ? devices.filter((d) => d.id === DEMO_DEVICE_ID) : devices.filter((d) => d.id !== DEMO_DEVICE_ID)),
    [demoMode, devices],
  );

  useEffect(() => {
    if (!pendingId) return;
    if (connectionPhase === 'connected') {
      const id = pendingId;
      setPendingId(null);
      setErrorByDevice((prev) => {
        if (!(id in prev)) return prev;
        const { [id]: _removed, ...rest } = prev;
        return rest;
      });
      router.push('/projects');
      return;
    }
    if (connectionPhase === 'unauthorized' || connectionPhase === 'disconnected') {
      const id = pendingId;
      const message = connectionError ?? (connectionPhase === 'unauthorized' ? 'Pairing revoked' : 'Couldn’t connect');
      setPendingId(null);
      setActiveDevice(null);
      setErrorByDevice((prev) => ({ ...prev, [id]: message }));
    }
  }, [pendingId, connectionPhase, connectionError, router, setActiveDevice]);

  const handleRepair = useCallback(
    (entry: DeviceEntry) => {
      router.push({
        pathname: '/add-device',
        params: {
          entryId: entry.id,
          host: entry.host,
          port: String(entry.port),
          label: entry.label,
        },
      });
    },
    [router],
  );

  if (!hasHydrated || !settingsHydrated) return null;
  if (!hasOnboarded) return <Redirect href="/onboarding" />;

  const handleSelect = (id: string) => {
    const entry = devices.find((d) => d.id === id);
    if (entry?.needsRepair) {
      handleRepair(entry);
      return;
    }
    if (isBillingEnforced() && entitlement.kind === 'expired') {
      router.push('/paywall');
      return;
    }
    setErrorByDevice((prev) => {
      if (!(id in prev)) return prev;
      const { [id]: _removed, ...rest } = prev;
      return rest;
    });
    setPendingId(id);
    setActiveDevice(id);
  };

  const handleLongPress = (entry: DeviceEntry) => {
    Alert.alert(entry.label, 'Remove this device?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: () => {
          removeDevice(entry.id);
        },
      },
    ]);
  };

  return (
    <View style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <Stack.Screen
        options={{
          title: 'Devices',
          headerLeft: () => (
            <HeaderIconButton
              icon="settings-outline"
              accessibilityLabel="Settings"
              onPress={() => router.push('/settings')}
            />
          ),
          headerRight: () => (
            <HeaderIconButton
              icon="add"
              accessibilityLabel="Add device"
              onPress={() => router.push('/add-device')}
            />
          ),
        }}
      />

      {visibleDevices.length === 0 ? (
        <View style={styles.center}>
          <Text style={[styles.emptyTitle, { color: tokens.text.primary }]}>No devices yet</Text>
          <Text style={[styles.emptyBody, { color: tokens.text.muted }]}>
            Tap the + icon to add your first Muxy desktop.
          </Text>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          {visibleDevices.map((d) => (
            <DeviceRow
              key={d.id}
              label={d.label}
              host={d.host}
              port={d.port}
              needsRepair={Boolean(d.needsRepair)}
              connecting={pendingId === d.id}
              errorMessage={pendingId === d.id ? null : errorByDevice[d.id] ?? null}
              onPress={() => handleSelect(d.id)}
              onLongPress={demoMode && d.id === DEMO_DEVICE_ID ? () => {} : () => handleLongPress(d)}
              onRepair={() => handleRepair(d)}
            />
          ))}
        </ScrollView>
      )}
      {isBillingEnforced() ? <EntitlementFooter /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  list: { padding: 16, gap: 8 },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 8,
  },
  emptyTitle: { fontSize: 22, fontWeight: '600' },
  emptyBody: { fontSize: 15, textAlign: 'center' },
});
