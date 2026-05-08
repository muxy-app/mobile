import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { useTokens } from '@/theme';

type Props = {
  label: string;
  host: string;
  port: number;
  needsRepair: boolean;
  onPress: () => void;
  onLongPress: () => void;
  onRepair?: () => void;
};

export function DeviceRow({ label, host, port, needsRepair, onPress, onLongPress, onRepair }: Props) {
  const tokens = useTokens();

  const subtitle = needsRepair ? 'Pairing revoked — re-pair to reconnect' : `${host}:${port}`;

  return (
    <Pressable
      onPress={onPress}
      onLongPress={onLongPress}
      style={({ pressed }) => [
        styles.row,
        {
          backgroundColor: tokens.surface.secondary,
          borderColor: tokens.border.subtle,
          opacity: pressed ? 0.85 : 1,
        },
      ]}>
      <View style={styles.body}>
        <Text style={[styles.label, { color: tokens.text.primary }]} numberOfLines={1}>
          {label}
        </Text>
        <Text
          style={[
            styles.subtitle,
            { color: needsRepair ? tokens.status.danger : tokens.text.muted },
          ]}
          numberOfLines={1}>
          {subtitle}
        </Text>
      </View>
      {needsRepair && onRepair ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Re-pair"
          onPress={onRepair}
          hitSlop={8}
          style={[styles.repair, { backgroundColor: tokens.accent.primary }]}>
          <Text style={[styles.repairLabel, { color: tokens.accent.contrast }]}>Re-pair</Text>
        </Pressable>
      ) : (
        <Ionicons name="chevron-forward" size={18} color={tokens.text.muted} />
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 14,
    paddingVertical: 14,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
  },
  body: { flex: 1, gap: 2 },
  label: { fontSize: 16, fontWeight: '600' },
  subtitle: { fontSize: 13 },
  repair: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999 },
  repairLabel: { fontSize: 13, fontWeight: '600' },
});
