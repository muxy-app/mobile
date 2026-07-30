import { Redirect, Stack, useLocalSearchParams } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';

import { SSHTerminal } from '@/components/ssh/SSHTerminal';
import { getSSHSupport, useSSHStore } from '@/ssh';
import { useTokens } from '@/theme';

export default function SSHTerminalRoute() {
  const tokens = useTokens();
  const { id } = useLocalSearchParams<{ id: string }>();
  const hasHydrated = useSSHStore((state) => state.hasHydrated);
  const connection = useSSHStore((state) =>
    state.connections.find((candidate) => candidate.id === id),
  );

  if (!hasHydrated) return null;
  if (getSSHSupport() === 'hidden') return <Redirect href="/" />;

  if (getSSHSupport() !== 'available' || !connection) {
    return (
      <View
        style={[
          styles.missing,
          { backgroundColor: tokens.surface.primary },
        ]}>
        <Stack.Screen options={{ title: 'SSH' }} />
        <Text style={[styles.title, { color: tokens.text.primary }]}>
          SSH connection unavailable
        </Text>
        <Text style={[styles.body, { color: tokens.text.muted }]}>
          This connection cannot be opened in the current build.
        </Text>
      </View>
    );
  }

  return (
    <>
      <Stack.Screen options={{ title: connection.name }} />
      <SSHTerminal connection={connection} />
    </>
  );
}

const styles = StyleSheet.create({
  missing: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 8,
  },
  title: { fontSize: 20, fontWeight: '600', textAlign: 'center' },
  body: { fontSize: 14, textAlign: 'center' },
});
