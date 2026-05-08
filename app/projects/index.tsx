import { FlashList } from '@shopify/flash-list';
import { Stack, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';

import { ProjectRow } from '@/components/ProjectRow';
import { useDevicesStore, useProjects, useProjectsStore } from '@/state';
import { useTokens } from '@/theme';

export default function ProjectsScreen() {
  const tokens = useTokens();
  const router = useRouter();

  const activeDevice = useDevicesStore((s) => {
    const id = s.activeDeviceId;
    return id ? s.devices.find((d) => d.id === id) ?? null : null;
  });
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const setActiveDevice = useDevicesStore((s) => s.setActiveDevice);

  const projects = useProjectsStore((s) => s.projects);
  const fetchPhase = useProjectsStore((s) => s.fetchPhase);
  const fetchError = useProjectsStore((s) => s.fetchError);

  const { refresh } = useProjects();
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (!activeDevice) router.replace('/');
  }, [activeDevice, router]);

  useEffect(() => {
    return () => {
      setActiveDevice(null);
    };
  }, [setActiveDevice]);

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await refresh();
    } finally {
      setRefreshing(false);
    }
  }, [refresh]);

  const headerTitle = activeDevice?.label ?? 'Projects';

  const isInitialLoading = fetchPhase === 'loading' && projects.length === 0;
  const isConnecting =
    connectionPhase === 'connecting' || connectionPhase === 'authenticating' || connectionPhase === 'reconnecting';

  const renderEmpty = () => {
    if (isConnecting && projects.length === 0) {
      return (
        <Centered tokens={tokens}>
          <ActivityIndicator color={tokens.accent.primary} />
          <Hint tokens={tokens}>Connecting…</Hint>
        </Centered>
      );
    }
    if (isInitialLoading) {
      return (
        <Centered tokens={tokens}>
          <ActivityIndicator color={tokens.accent.primary} />
        </Centered>
      );
    }
    if (fetchPhase === 'error') {
      return (
        <Centered tokens={tokens}>
          <Text style={[styles.errorTitle, { color: tokens.text.primary }]}>Couldn’t load projects</Text>
          <Text style={[styles.errorBody, { color: tokens.text.muted }]}>{fetchError ?? 'Unknown error'}</Text>
          <Pressable
            onPress={handleRefresh}
            style={({ pressed }) => [
              styles.retry,
              { backgroundColor: tokens.accent.primary, opacity: pressed ? 0.85 : 1 },
            ]}>
            <Text style={[styles.retryLabel, { color: tokens.accent.contrast }]}>Try again</Text>
          </Pressable>
        </Centered>
      );
    }
    return (
      <Centered tokens={tokens}>
        <Text style={[styles.emptyTitle, { color: tokens.text.primary }]}>No projects yet</Text>
        <Text style={[styles.emptyBody, { color: tokens.text.muted }]}>Create one in Muxy on your Mac.</Text>
      </Centered>
    );
  };

  return (
    <View style={[styles.root, { backgroundColor: tokens.surface.primary }]}>
      <Stack.Screen options={{ title: headerTitle }} />
      <FlashList
        data={projects}
        keyExtractor={(p) => p.id}
        renderItem={({ item }) => (
          <ProjectRow project={item} onPress={() => router.push({ pathname: '/projects/[id]', params: { id: item.id } })} />
        )}
        contentContainerStyle={styles.list}
        ItemSeparatorComponent={() => <View style={{ height: 8 }} />}
        ListEmptyComponent={renderEmpty()}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={tokens.text.muted}
            colors={[tokens.accent.primary]}
          />
        }
      />
    </View>
  );
}

function Centered({ children, tokens }: { children: React.ReactNode; tokens: ReturnType<typeof useTokens> }) {
  return <View style={[styles.center, { backgroundColor: tokens.surface.primary }]}>{children}</View>;
}

function Hint({ children, tokens }: { children: React.ReactNode; tokens: ReturnType<typeof useTokens> }) {
  return <Text style={[styles.hint, { color: tokens.text.muted }]}>{children}</Text>;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  list: { padding: 16 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32, gap: 12, paddingTop: 80 },
  hint: { fontSize: 14 },
  emptyTitle: { fontSize: 22, fontWeight: '600' },
  emptyBody: { fontSize: 15, textAlign: 'center' },
  errorTitle: { fontSize: 18, fontWeight: '600' },
  errorBody: { fontSize: 14, textAlign: 'center' },
  retry: { paddingHorizontal: 18, paddingVertical: 10, borderRadius: 999 },
  retryLabel: { fontSize: 14, fontWeight: '600' },
});
