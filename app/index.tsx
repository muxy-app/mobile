import { Redirect, Stack, useRouter } from 'expo-router';
import { Alert, ScrollView, StyleSheet, Text, View } from 'react-native';

import { useEntitlement } from '@/billing';
import { EntitlementFooter } from '@/components/billing/EntitlementFooter';
import { DeviceRow } from '@/components/DeviceRow';
import { HeaderIconButton } from '@/components/HeaderIconButton';
import { useDevicesStore, useSettingsStore, type DeviceEntry } from '@/state';
import { useTokens } from '@/theme';

export default function DevicesScreen() {
  const tokens = useTokens();
  const router = useRouter();

  const hasHydrated = useDevicesStore((s) => s.hasHydrated);
  const settingsHydrated = useSettingsStore((s) => s.hasHydrated);
  const hasOnboarded = useSettingsStore((s) => s.hasOnboarded);
  const devices = useDevicesStore((s) => s.devices);
  const setActiveDevice = useDevicesStore((s) => s.setActiveDevice);
  const removeDevice = useDevicesStore((s) => s.removeDevice);
  const entitlement = useEntitlement();

  if (!hasHydrated || !settingsHydrated) return null;
  if (!hasOnboarded) return <Redirect href="/onboarding" />;

  const handleSelect = (id: string) => {
    if (entitlement.kind === 'expired') {
      router.push('/paywall');
      return;
    }
    setActiveDevice(id);
    router.push('/projects');
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

  const handleRepair = (entry: DeviceEntry) => {
    router.push({
      pathname: '/add-device',
      params: {
        entryId: entry.id,
        host: entry.host,
        port: String(entry.port),
        label: entry.label,
      },
    });
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

      {devices.length === 0 ? (
        <View style={styles.center}>
          <Text style={[styles.emptyTitle, { color: tokens.text.primary }]}>No devices yet</Text>
          <Text style={[styles.emptyBody, { color: tokens.text.muted }]}>
            Tap the + icon to add your first Muxy desktop.
          </Text>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.list}>
          {devices.map((d) => (
            <DeviceRow
              key={d.id}
              label={d.label}
              host={d.host}
              port={d.port}
              needsRepair={Boolean(d.needsRepair)}
              onPress={() => handleSelect(d.id)}
              onLongPress={() => handleLongPress(d)}
              onRepair={() => handleRepair(d)}
            />
          ))}
        </ScrollView>
      )}
      <EntitlementFooter />
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
