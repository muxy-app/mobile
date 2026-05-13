import { Ionicons } from '@expo/vector-icons';
import { forwardRef, useImperativeHandle } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { useTokens } from '@/theme';

export type SwipeDirection = 'prev' | 'next';

export type SwipeArrowOverlayHandle = {
  setDragOffset: (dx: number, canPrev: boolean, canNext: boolean) => void;
  releaseDrag: () => void;
  flash: (direction: SwipeDirection) => void;
};

const MAX_DRAG = 90;

export const SwipeArrowOverlay = forwardRef<SwipeArrowOverlayHandle>(function SwipeArrowOverlay(
  _,
  ref,
) {
  const tokens = useTokens();

  const prevOpacity = useSharedValue(0);
  const prevScale = useSharedValue(0.6);
  const nextOpacity = useSharedValue(0);
  const nextScale = useSharedValue(0.6);

  useImperativeHandle(ref, () => ({
    setDragOffset: (dx, canPrev, canNext) => {
      if (dx > 0 && canPrev) {
        const t = Math.min(1, dx / MAX_DRAG);
        prevOpacity.value = t;
        prevScale.value = 0.6 + 0.4 * t;
        nextOpacity.value = 0;
        nextScale.value = 0.6;
        return;
      }
      if (dx < 0 && canNext) {
        const t = Math.min(1, -dx / MAX_DRAG);
        nextOpacity.value = t;
        nextScale.value = 0.6 + 0.4 * t;
        prevOpacity.value = 0;
        prevScale.value = 0.6;
        return;
      }
      prevOpacity.value = 0;
      nextOpacity.value = 0;
    },
    releaseDrag: () => {
      prevOpacity.value = withTiming(0, { duration: 180, easing: Easing.out(Easing.quad) });
      nextOpacity.value = withTiming(0, { duration: 180, easing: Easing.out(Easing.quad) });
      prevScale.value = withTiming(0.6, { duration: 180 });
      nextScale.value = withTiming(0.6, { duration: 180 });
    },
    flash: (direction) => {
      const opacity = direction === 'prev' ? prevOpacity : nextOpacity;
      const scale = direction === 'prev' ? prevScale : nextScale;
      opacity.value = withTiming(1, { duration: 120, easing: Easing.out(Easing.quad) }, () => {
        opacity.value = withTiming(0, { duration: 260, easing: Easing.in(Easing.quad) });
      });
      scale.value = withTiming(1, { duration: 120 }, () => {
        scale.value = withTiming(0.6, { duration: 260 });
      });
    },
  }));

  const prevStyle = useAnimatedStyle(() => ({
    opacity: prevOpacity.value,
    transform: [{ scale: prevScale.value }],
  }));
  const nextStyle = useAnimatedStyle(() => ({
    opacity: nextOpacity.value,
    transform: [{ scale: nextScale.value }],
  }));

  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Animated.View style={[styles.bubble, styles.prev, { backgroundColor: tokens.surface.tertiary, borderColor: tokens.accent.primary }, prevStyle]}>
        <Ionicons name="chevron-back" size={26} color={tokens.accent.primary} />
      </Animated.View>
      <Animated.View style={[styles.bubble, styles.next, { backgroundColor: tokens.surface.tertiary, borderColor: tokens.accent.primary }, nextStyle]}>
        <Ionicons name="chevron-forward" size={26} color={tokens.accent.primary} />
      </Animated.View>
    </View>
  );
});

const styles = StyleSheet.create({
  bubble: {
    position: 'absolute',
    top: '50%',
    marginTop: -26,
    width: 52,
    height: 52,
    borderRadius: 26,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
  },
  prev: {
    left: 16,
  },
  next: {
    right: 16,
  },
});
