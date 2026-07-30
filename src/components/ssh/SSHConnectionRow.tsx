import { Ionicons } from '@expo/vector-icons';
import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { SSHConnection, SSHLiveSession } from '@/ssh';
import { useTokens } from '@/theme';

type Props = {
  connection: SSHConnection;
  session?: SSHLiveSession;
  onPress: () => void;
  onMore: () => void;
};

export const SSHConnectionRow = memo(function SSHConnectionRow({
  connection,
  session,
  onPress,
  onMore,
}: Props) {
  const tokens = useTokens();
  const status =
    session?.state === 'connecting'
      ? 'Connecting…'
      : session?.state === 'connected'
        ? 'Connected'
        : session?.state === 'failed' && session.error
          ? session.error
          : `${connection.username}@${connection.host}:${connection.port}`;
  const statusColor =
    session?.state === 'failed'
      ? tokens.status.danger
      : session?.state === 'connected'
        ? tokens.status.success
        : tokens.text.muted;

  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        {
          backgroundColor: tokens.surface.secondary,
          borderColor: tokens.border.subtle,
          opacity: pressed ? 0.85 : 1,
        },
      ]}>
      <View style={styles.body}>
        <Text
          style={[styles.name, { color: tokens.text.primary }]}
          numberOfLines={1}>
          {connection.name}
        </Text>
        <Text
          style={[styles.subtitle, { color: statusColor }]}
          numberOfLines={1}>
          {status}
        </Text>
      </View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Edit or remove ${connection.name}`}
        hitSlop={8}
        onPress={(event) => {
          event.stopPropagation();
          onMore();
        }}
        style={({ pressed }) => [
          styles.more,
          { opacity: pressed ? 0.5 : 1 },
        ]}>
        <Ionicons
          name="ellipsis-horizontal"
          size={20}
          color={tokens.text.muted}
        />
      </Pressable>
      <Ionicons
        name="chevron-forward"
        size={18}
        color={tokens.text.muted}
      />
    </Pressable>
  );
});

const styles = StyleSheet.create({
  row: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 14,
    borderRadius: 12,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
  },
  body: { flex: 1, gap: 2 },
  name: { fontSize: 16, fontWeight: '600' },
  subtitle: { fontSize: 13 },
  more: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
