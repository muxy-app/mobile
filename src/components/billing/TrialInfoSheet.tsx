import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect } from 'react';
import {
  ActivityIndicator,
  Dimensions,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolate,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useBillingStore, useEntitlement, copy } from '@/billing';
import { useTokens } from '@/theme';

type Props = {
  visible: boolean;
  onClose: () => void;
};

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

export function TrialInfoSheet({ visible, onClose }: Props) {
  const tokens = useTokens();
  const insets = useSafeAreaInsets();
  const entitlement = useEntitlement();
  const price = useBillingStore((s) => s.productPrice);
  const purchasing = useBillingStore((s) => s.purchasing);
  const restoring = useBillingStore((s) => s.restoring);
  const buy = useBillingStore((s) => s.buy);
  const restore = useBillingStore((s) => s.restore);
  const error = useBillingStore((s) => s.error);

  const sheetHeight = Math.min(SCREEN_HEIGHT * 0.78, SCREEN_HEIGHT - 80);

  const translateY = useSharedValue(sheetHeight);
  const overlay = useSharedValue(0);

  const finishClose = useCallback(() => onClose(), [onClose]);

  const dismiss = useCallback(() => {
    if (purchasing) return;
    overlay.value = withTiming(0, { duration: 220 });
    translateY.value = withTiming(sheetHeight, { duration: 220 }, (finished) => {
      if (finished) runOnJS(finishClose)();
    });
  }, [purchasing, overlay, translateY, sheetHeight, finishClose]);

  useEffect(() => {
    if (visible) {
      translateY.value = sheetHeight;
      overlay.value = 0;
      overlay.value = withTiming(1, { duration: 220 });
      translateY.value = withSpring(0, { damping: 22, stiffness: 220, mass: 0.9 });
    }
  }, [visible, sheetHeight, translateY, overlay]);

  const panGesture = Gesture.Pan()
    .activeOffsetY(8)
    .failOffsetX([-20, 20])
    .onUpdate((e) => {
      if (e.translationY > 0) translateY.value = e.translationY;
    })
    .onEnd((e) => {
      if (purchasing) {
        translateY.value = withSpring(0, { damping: 22, stiffness: 220 });
        return;
      }
      if (e.translationY > 120 || e.velocityY > 600) {
        overlay.value = withTiming(0, { duration: 220 });
        translateY.value = withTiming(sheetHeight, { duration: 220 }, (finished) => {
          if (finished) runOnJS(finishClose)();
        });
      } else {
        translateY.value = withSpring(0, { damping: 22, stiffness: 220 });
      }
    });

  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));
  const overlayStyle = useAnimatedStyle(() => ({
    backgroundColor: `rgba(0,0,0,${interpolate(overlay.value, [0, 1], [0, 0.45])})`,
  }));

  const title = copy.sheetTitle(entitlement);
  const bullets = copy.sheetBullets({ entitlement, price });
  const ctaLabel = copy.primaryCtaLabel({ entitlement, price });
  const showCta = entitlement.kind !== 'unlocked';
  const ctaDisabled = purchasing;

  const handleBuy = async () => {
    if (entitlement.kind === 'unlocked') {
      dismiss();
      return;
    }
    await buy();
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={dismiss}
      statusBarTranslucent>
      <View style={StyleSheet.absoluteFill}>
        <Animated.View style={[StyleSheet.absoluteFill, overlayStyle]}>
          <Pressable style={StyleSheet.absoluteFill} onPress={dismiss} />
        </Animated.View>
        <View style={styles.sheetWrap} pointerEvents="box-none">
          <Animated.View
            style={[
              styles.sheet,
              {
                height: sheetHeight,
                backgroundColor: tokens.surface.primary,
                borderColor: tokens.border.subtle,
                paddingBottom: insets.bottom + 12,
              },
              sheetStyle,
            ]}>
            <GestureDetector gesture={panGesture}>
              <View collapsable={false}>
                <View style={styles.handleArea}>
                  <View style={[styles.handle, { backgroundColor: tokens.border.strong }]} />
                </View>
                <View style={[styles.header, { borderBottomColor: tokens.border.subtle }]}>
                  <View style={styles.headerSide} />
                  <Text style={[styles.headerTitle, { color: tokens.text.primary }]} numberOfLines={1}>
                    {title}
                  </Text>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="Close"
                    onPress={dismiss}
                    disabled={purchasing}
                    hitSlop={10}
                    style={({ pressed }) => [
                      styles.headerSide,
                      styles.headerRight,
                      { opacity: purchasing ? 0.5 : pressed ? 0.5 : 1 },
                    ]}>
                    <Ionicons name="close" size={22} color={tokens.text.primary} />
                  </Pressable>
                </View>
              </View>
            </GestureDetector>

            <ScrollView contentContainerStyle={styles.body}>
              <Text style={[styles.sectionLabel, { color: tokens.text.muted }]}>How it works</Text>
              <View style={styles.bulletList}>
                {bullets.map((b, i) => (
                  <View key={i} style={styles.bulletRow}>
                    <Text style={[styles.bulletDot, { color: tokens.text.muted }]}>•</Text>
                    <Text style={[styles.bulletText, { color: tokens.text.primary }]}>{b}</Text>
                  </View>
                ))}
              </View>

              {error ? (
                <Text style={[styles.errorText, { color: tokens.status.danger }]}>{error}</Text>
              ) : null}

              {showCta ? (
                <Pressable
                  accessibilityRole="button"
                  onPress={handleBuy}
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
                    <Text style={[styles.ctaLabel, { color: tokens.accent.contrast }]}>
                      {ctaLabel}
                    </Text>
                  )}
                </Pressable>
              ) : null}

              {showCta ? (
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
              ) : null}
            </ScrollView>
          </Animated.View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  sheetWrap: { flex: 1, justifyContent: 'flex-end' },
  sheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    borderTopWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  handleArea: { paddingTop: 8, paddingBottom: 6, alignItems: 'center' },
  handle: { width: 38, height: 4, borderRadius: 2 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerSide: { width: 44, height: 30, justifyContent: 'center' },
  headerRight: { alignItems: 'flex-end' },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 16, fontWeight: '600' },
  body: { padding: 20, gap: 16 },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  bulletList: { gap: 10 },
  bulletRow: { flexDirection: 'row', gap: 8 },
  bulletDot: { fontSize: 15, lineHeight: 22 },
  bulletText: { flex: 1, fontSize: 15, lineHeight: 22 },
  errorText: { fontSize: 13 },
  cta: {
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 4,
  },
  ctaLabel: { fontSize: 16, fontWeight: '600' },
  restoreButton: { alignItems: 'center', paddingVertical: 8 },
  restoreLabel: { fontSize: 14, fontWeight: '500' },
});
