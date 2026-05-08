import { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';

import { useBillingStore, useEntitlement, copy } from '@/billing';
import { useTokens } from '@/theme';

import { TrialInfoSheet } from './TrialInfoSheet';

export function EntitlementFooter() {
  const tokens = useTokens();
  const entitlement = useEntitlement();
  const price = useBillingStore((s) => s.productPrice);
  const [sheetVisible, setSheetVisible] = useState(false);

  if (entitlement.kind === 'unlocked') return null;

  const text = copy.footerText({ entitlement, price });
  if (!text) return null;

  return (
    <>
      <Pressable
        accessibilityRole="button"
        onPress={() => setSheetVisible(true)}
        style={({ pressed }) => [
          styles.banner,
          {
            backgroundColor: tokens.surface.secondary,
            borderColor: tokens.border.subtle,
            opacity: pressed ? 0.85 : 1,
          },
        ]}>
        <Text
          style={[styles.bannerText, { color: tokens.text.secondary }]}
          numberOfLines={1}
          ellipsizeMode="tail">
          {text}
        </Text>
        <Text style={[styles.bannerHint, { color: tokens.text.muted }]}>Tap for details</Text>
      </Pressable>
      <TrialInfoSheet visible={sheetVisible} onClose={() => setSheetVisible(false)} />
    </>
  );
}

const styles = StyleSheet.create({
  banner: {
    marginHorizontal: 16,
    marginBottom: 24,
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  bannerText: { fontSize: 14, fontWeight: '500', flexShrink: 1 },
  bannerHint: { fontSize: 12, fontWeight: '500' },
});
