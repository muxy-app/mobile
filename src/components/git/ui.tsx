import { Ionicons } from '@expo/vector-icons';
import type { ComponentProps, ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { useTokens, type ThemeTokens } from '@/theme';

type IconName = ComponentProps<typeof Ionicons>['name'];

export function Section({ title, children }: { title?: string; children: ReactNode }) {
  const tokens = useTokens();
  return (
    <View style={styles.section}>
      {title ? (
        <Text style={[styles.sectionLabel, { color: tokens.text.muted }]}>{title}</Text>
      ) : null}
      <View
        style={[
          styles.card,
          { backgroundColor: tokens.surface.secondary, borderColor: tokens.border.subtle },
        ]}>
        {children}
      </View>
    </View>
  );
}

export function Divider() {
  const tokens = useTokens();
  return <View style={[styles.divider, { backgroundColor: tokens.border.subtle }]} />;
}

export function Row({
  icon,
  iconColor,
  title,
  subtitle,
  trailing,
  onPress,
  destructive,
  disabled,
}: {
  icon?: IconName;
  iconColor?: string;
  title: string;
  subtitle?: string;
  trailing?: ReactNode;
  onPress?: () => void;
  destructive?: boolean;
  disabled?: boolean;
}) {
  const tokens = useTokens();
  const titleColor = destructive ? tokens.status.danger : tokens.text.primary;
  const inner = (
    <>
      {icon ? (
        <Ionicons name={icon} size={18} color={iconColor ?? tokens.text.muted} style={styles.rowIcon} />
      ) : null}
      <View style={styles.rowBody}>
        <Text style={[styles.rowTitle, { color: titleColor }]} numberOfLines={1}>
          {title}
        </Text>
        {subtitle ? (
          <Text style={[styles.rowSubtitle, { color: tokens.text.muted }]} numberOfLines={1}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {trailing ? <View style={styles.rowTrailing}>{trailing}</View> : null}
    </>
  );
  if (!onPress) {
    return <View style={[styles.row, disabled ? { opacity: 0.5 } : null]}>{inner}</View>;
  }
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.row,
        pressed ? { backgroundColor: tokens.surface.tertiary } : null,
        disabled ? { opacity: 0.5 } : null,
      ]}>
      {inner}
    </Pressable>
  );
}

export function ActionGrid({
  actions,
}: {
  actions: { icon: IconName; label: string; onPress: () => void; loading?: boolean; disabled?: boolean; badge?: string }[];
}) {
  const tokens = useTokens();
  return (
    <View style={styles.grid}>
      {actions.map((a) => (
        <Pressable
          key={a.label}
          onPress={a.onPress}
          disabled={a.disabled || a.loading}
          style={({ pressed }) => [
            styles.gridItem,
            {
              backgroundColor: tokens.surface.secondary,
              borderColor: tokens.border.subtle,
              opacity: a.disabled ? 0.5 : pressed ? 0.85 : 1,
            },
          ]}>
          {a.loading ? (
            <ActivityIndicator color={tokens.text.primary} size="small" />
          ) : (
            <Ionicons name={a.icon} size={20} color={tokens.text.primary} />
          )}
          <Text style={[styles.gridLabel, { color: tokens.text.primary }]} numberOfLines={1}>
            {a.label}
          </Text>
          {a.badge ? (
            <View style={[styles.badge, { backgroundColor: tokens.accent.primary }]}>
              <Text style={[styles.badgeLabel, { color: tokens.accent.contrast }]} numberOfLines={1}>
                {a.badge}
              </Text>
            </View>
          ) : null}
        </Pressable>
      ))}
    </View>
  );
}

export function PrimaryButton({
  label,
  onPress,
  loading,
  disabled,
  destructive,
}: {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  destructive?: boolean;
}) {
  const tokens = useTokens();
  const bg = destructive ? tokens.status.danger : tokens.accent.primary;
  const fg = destructive ? '#FFFFFF' : tokens.accent.contrast;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled || loading}
      style={({ pressed }) => [
        styles.primary,
        { backgroundColor: bg, opacity: disabled ? 0.5 : pressed ? 0.85 : 1 },
      ]}>
      {loading ? (
        <ActivityIndicator color={fg} />
      ) : (
        <Text style={[styles.primaryLabel, { color: fg }]}>{label}</Text>
      )}
    </Pressable>
  );
}

export function GhostButton({
  label,
  onPress,
  disabled,
  destructive,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  destructive?: boolean;
}) {
  const tokens = useTokens();
  const color = destructive ? tokens.status.danger : tokens.text.primary;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.ghost,
        { borderColor: tokens.border.strong, opacity: disabled ? 0.5 : pressed ? 0.7 : 1 },
      ]}>
      <Text style={[styles.ghostLabel, { color }]}>{label}</Text>
    </Pressable>
  );
}

export function Field({
  label,
  value,
  onChangeText,
  placeholder,
  multiline,
  autoFocus,
  autoCapitalize = 'none',
}: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  placeholder?: string;
  multiline?: boolean;
  autoFocus?: boolean;
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
}) {
  const tokens = useTokens();
  return (
    <View style={styles.fieldWrap}>
      <Text style={[styles.fieldLabel, { color: tokens.text.muted }]}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={tokens.text.muted}
        autoFocus={autoFocus}
        autoCapitalize={autoCapitalize}
        autoCorrect={false}
        multiline={multiline}
        style={[
          styles.field,
          multiline ? styles.fieldMultiline : null,
          {
            color: tokens.text.primary,
            backgroundColor: tokens.surface.secondary,
            borderColor: tokens.border.subtle,
          },
        ]}
      />
    </View>
  );
}

export function StatusPill({
  label,
  color,
  textColor,
}: {
  label: string;
  color: string;
  textColor: string;
}) {
  return (
    <View style={[styles.pill, { backgroundColor: color }]}>
      <Text style={[styles.pillLabel, { color: textColor }]}>{label}</Text>
    </View>
  );
}

export function ErrorText({ children }: { children: ReactNode }) {
  const tokens = useTokens();
  return <Text style={[styles.error, { color: tokens.status.danger }]}>{children}</Text>;
}

export function MutedText({ children }: { children: ReactNode }) {
  const tokens = useTokens();
  return <Text style={[styles.muted, { color: tokens.text.muted }]}>{children}</Text>;
}

export function fileNameOf(path: string): string {
  const idx = path.lastIndexOf('/');
  return idx >= 0 ? path.slice(idx + 1) : path;
}

export function tokensStatusForFile(status: string, tokens: ThemeTokens): { color: string; label: string } {
  switch (status) {
    case 'added':
      return { color: tokens.status.success, label: 'A' };
    case 'modified':
      return { color: tokens.status.warning, label: 'M' };
    case 'deleted':
      return { color: tokens.status.danger, label: 'D' };
    case 'renamed':
      return { color: tokens.accent.primary, label: 'R' };
    case 'copied':
      return { color: tokens.accent.primary, label: 'C' };
    case 'untracked':
      return { color: tokens.text.muted, label: 'U' };
    case 'unmerged':
      return { color: tokens.status.danger, label: '!' };
    default:
      return { color: tokens.text.muted, label: '?' };
  }
}

const styles = StyleSheet.create({
  section: { gap: 6 },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    paddingHorizontal: 4,
  },
  card: {
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  divider: { height: StyleSheet.hairlineWidth, marginLeft: 14 },
  row: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 12, gap: 12 },
  rowIcon: { width: 22, textAlign: 'center' },
  rowBody: { flex: 1, gap: 2 },
  rowTitle: { fontSize: 15, fontWeight: '500' },
  rowSubtitle: { fontSize: 12 },
  rowTrailing: { marginLeft: 8 },
  grid: { flexDirection: 'row', gap: 8 },
  gridItem: {
    flex: 1,
    flexBasis: 0,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 12,
    paddingHorizontal: 6,
  },
  gridLabel: { fontSize: 12, fontWeight: '500' },
  badge: {
    position: 'absolute',
    top: 4,
    right: 4,
    paddingHorizontal: 5,
    minWidth: 16,
    height: 16,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeLabel: { fontSize: 9, fontWeight: '700' },
  primary: {
    height: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryLabel: { fontSize: 16, fontWeight: '600' },
  ghost: {
    height: 44,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ghostLabel: { fontSize: 15, fontWeight: '500' },
  fieldWrap: { gap: 6 },
  fieldLabel: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    paddingHorizontal: 4,
  },
  field: {
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 12,
    paddingVertical: 12,
    fontSize: 15,
    minHeight: 44,
  },
  fieldMultiline: { minHeight: 100, textAlignVertical: 'top' },
  pill: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999 },
  pillLabel: { fontSize: 11, fontWeight: '700' },
  error: { fontSize: 13, paddingHorizontal: 4 },
  muted: { fontSize: 13, paddingHorizontal: 4 },
});
