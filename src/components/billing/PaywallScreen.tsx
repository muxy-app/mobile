import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useEffect } from 'react';
import {
  ActivityIndicator,
  BackHandler,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useBillingStore, useEntitlement, copy } from '@/billing';
import { useTokens } from '@/theme';

export function PaywallScreen() {
  const tokens = useTokens();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const entitlement = useEntitlement();
  const price = useBillingStore((s) => s.productPrice);
  const purchasing = useBillingStore((s) => s.purchasing);
  const restoring = useBillingStore((s) => s.restoring);
  const buy = useBillingStore((s) => s.buy);
  const restore = useBillingStore((s) => s.restore);
  const error = useBillingStore((s) => s.error);

  useEffect(() => {
    if (entitlement.kind === 'unlocked') {
      router.back();
    }
  }, [entitlement.kind, router]);

  useEffect(() => {
    if (Platform.OS !== 'android' || !purchasing) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => sub.remove();
  }, [purchasing]);

  const closePaywall = () => {
    if (purchasing) return;
    router.back();
  };

  const title = copy.paywallTitle(entitlement);
  const subtitle = copy.paywallSubtitle(entitlement);
  const ctaLabel = copy.paywallButtonLabel({ entitlement, price });
  const ctaDisabled = purchasing;

  return (
    <View style={[styles.root, { backgroundColor: tokens.surface.primary, paddingTop: insets.top }]}>
      <View style={styles.headerBar}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Close"
          onPress={closePaywall}
          disabled={purchasing}
          hitSlop={12}
          style={({ pressed }) => [styles.headerButton, { opacity: purchasing ? 0.5 : pressed ? 0.5 : 1 }]}>
          <Ionicons name="close" size={26} color={tokens.text.primary} />
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + 32 }]}>
        <View style={styles.hero}>
          <Text style={[styles.title, { color: tokens.text.primary }]}>{title}</Text>
          <Text style={[styles.subtitle, { color: tokens.text.secondary }]}>{subtitle}</Text>
        </View>

        {error ? (
          <Text style={[styles.errorText, { color: tokens.status.danger }]}>{error}</Text>
        ) : null}

        <Pressable
          accessibilityRole="button"
          onPress={buy}
          disabled={ctaDisabled}
          style={({ pressed }) => [
            styles.cta,
            {
              backgroundColor: tokens.accent.primary,
              opacity: ctaDisabled ? 0.5 : pressed ? 0.85 : 1,
            },
          ]}>
          {purchasing ? (
            <ActivityIndicator color={tokens.accent.contrast} />
          ) : (
            <Text style={[styles.ctaLabel, { color: tokens.accent.contrast }]}>{ctaLabel}</Text>
          )}
        </Pressable>

        <Pressable
          accessibilityRole="button"
          onPress={restore}
          disabled={restoring}
          style={({ pressed }) => [
            styles.restoreButton,
            { opacity: restoring ? 0.5 : pressed ? 0.6 : 1 },
          ]}>
          <Text style={[styles.restoreLabel, { color: tokens.text.secondary }]}>
            {restoring ? 'Restoring…' : 'Restore purchase'}
          </Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    paddingHorizontal: 8,
    paddingVertical: 8,
  },
  headerButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  body: { paddingHorizontal: 24, gap: 24, flexGrow: 1 },
  hero: { alignItems: 'center', gap: 12, paddingTop: 24, paddingBottom: 12 },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center' },
  subtitle: { fontSize: 16, lineHeight: 22, textAlign: 'center' },
  errorText: { fontSize: 13, textAlign: 'center' },
  cta: {
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
  },
  ctaLabel: { fontSize: 17, fontWeight: '600' },
  restoreButton: { alignItems: 'center', paddingVertical: 12 },
  restoreLabel: { fontSize: 15, fontWeight: '500' },
});
