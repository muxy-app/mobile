import { Redirect, Stack, useRouter } from 'expo-router';
import { useCallback } from 'react';
import {
  Alert,
  FlatList,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { HeaderIconButton } from '@/components/HeaderIconButton';
import {
  deleteSSHConnection,
  getSSHSupport,
  useSSHStore,
  type SSHConnection,
} from '@/ssh';
import { useTokens } from '@/theme';

import { SSHConnectionRow } from './SSHConnectionRow';

export function SSHConnectionsScreen() {
  const tokens = useTokens();
  const { push } = useRouter();
  const support = getSSHSupport();
  const hasHydrated = useSSHStore((state) => state.hasHydrated);
  const connections = useSSHStore((state) => state.connections);
  const sessions = useSSHStore((state) => state.sessions);

  const openConnection = useCallback(
    (connection: SSHConnection) => {
      push({
        pathname: '/ssh/[id]',
        params: { id: connection.id },
      });
    },
    [push],
  );

  const showActions = useCallback(
    (connection: SSHConnection) => {
      Alert.alert(connection.name, undefined, [
        {
          text: 'Edit',
          onPress: () =>
            push({
              pathname: '/ssh/add',
              params: { id: connection.id },
            }),
        },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: () => {
            Alert.alert(
              `Remove ${connection.name}?`,
              'Its credentials and trusted host key will be deleted from this device.',
              [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Remove',
                  style: 'destructive',
                  onPress: () => {
                    deleteSSHConnection(connection.id).catch((error) => {
                      Alert.alert(
                        'Couldn’t Remove Connection',
                        error instanceof Error
                          ? error.message
                          : 'The connection could not be removed.',
                      );
                    });
                  },
                },
              ],
            );
          },
        },
        { text: 'Cancel', style: 'cancel' },
      ]);
    },
    [push],
  );

  if (!hasHydrated) return null;
  if (support === 'hidden') return <Redirect href="/" />;

  if (support === 'disabled-expo-go') {
    return (
      <View
        style={[
          styles.unavailable,
          { backgroundColor: tokens.surface.primary },
        ]}>
        <Stack.Screen options={{ title: 'SSH' }} />
        <Text style={[styles.emptyTitle, { color: tokens.text.primary }]}>
          Development build required
        </Text>
        <Text style={[styles.emptyBody, { color: tokens.text.muted }]}>
          SSH uses a native module that is not included in Expo Go.
        </Text>
      </View>
    );
  }

  return (
    <View
      style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <Stack.Screen
        options={{
          title: 'SSH',
          headerRight: () => (
            <HeaderIconButton
              icon="add"
              accessibilityLabel="Add SSH connection"
              onPress={() => push('/ssh/add')}
            />
          ),
        }}
      />
      <FlatList
        data={connections}
        keyExtractor={(connection) => connection.id}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={
          connections.length === 0 ? styles.emptyList : styles.list
        }
        renderItem={({ item }) => (
          <SSHConnectionRow
            connection={item}
            session={sessions[item.id]}
            onPress={() => openConnection(item)}
            onMore={() => showActions(item)}
          />
        )}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text
              style={[styles.emptyTitle, { color: tokens.text.primary }]}>
              No SSH servers yet
            </Text>
            <Text style={[styles.emptyBody, { color: tokens.text.muted }]}>
              Tap the + icon to add your first server.
            </Text>
          </View>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  list: { padding: 16, gap: 8 },
  emptyList: { flexGrow: 1 },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 8,
  },
  unavailable: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 8,
  },
  emptyTitle: { fontSize: 22, fontWeight: '600', textAlign: 'center' },
  emptyBody: { fontSize: 15, textAlign: 'center' },
});
