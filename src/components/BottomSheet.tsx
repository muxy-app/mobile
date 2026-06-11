import type { ReactNode } from 'react';
import { useCallback, useEffect } from 'react';
import { Dimensions, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
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

import { useTokens } from '@/theme';

type Props = {
  visible: boolean;
  onClose: () => void;
  title: string;
  heightRatio?: number;
  children: ReactNode;
};

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

export function BottomSheet({ visible, onClose, title, heightRatio = 0.5, children }: Props) {
  const tokens = useTokens();
  const insets = useSafeAreaInsets();

  const sheetHeight = Math.min(SCREEN_HEIGHT * heightRatio, SCREEN_HEIGHT - 60);

  const translateY = useSharedValue(sheetHeight);
  const overlay = useSharedValue(0);

  const finishClose = useCallback(() => {
    onClose();
  }, [onClose]);

  const dismiss = useCallback(() => {
    overlay.value = withTiming(0, { duration: 220 });
    translateY.value = withTiming(sheetHeight, { duration: 220 }, (finished) => {
      if (finished) runOnJS(finishClose)();
    });
  }, [overlay, translateY, sheetHeight, finishClose]);

  useEffect(() => {
    if (!visible) return;
    translateY.value = sheetHeight;
    overlay.value = 0;
    overlay.value = withTiming(1, { duration: 220 });
    translateY.value = withSpring(0, { damping: 22, stiffness: 220, mass: 0.9 });
  }, [visible, sheetHeight, translateY, overlay]);

  const panGesture = Gesture.Pan()
    .activeOffsetY(8)
    .failOffsetX([-20, 20])
    .onUpdate((e) => {
      if (e.translationY > 0) translateY.value = e.translationY;
    })
    .onEnd((e) => {
      if (e.translationY > 120 || e.velocityY > 600) {
        overlay.value = withTiming(0, { duration: 220 });
        translateY.value = withTiming(sheetHeight, { duration: 220 }, (finished) => {
          if (finished) runOnJS(finishClose)();
        });
        return;
      }
      translateY.value = withSpring(0, { damping: 22, stiffness: 220 });
    });

  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  const overlayStyle = useAnimatedStyle(() => ({
    backgroundColor: `rgba(0,0,0,${interpolate(overlay.value, [0, 1], [0, 0.45])})`,
  }));

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
                paddingBottom: insets.bottom,
              },
              sheetStyle,
            ]}>
            <GestureDetector gesture={panGesture}>
              <View collapsable={false}>
                <View style={styles.handleArea}>
                  <View style={[styles.handle, { backgroundColor: tokens.border.strong }]} />
                </View>
                <View style={[styles.header, { borderBottomColor: tokens.border.subtle }]}>
                  <Text
                    style={[styles.headerTitle, { color: tokens.text.primary }]}
                    numberOfLines={1}>
                    {title}
                  </Text>
                </View>
              </View>
            </GestureDetector>

            <View style={styles.body}>{children}</View>
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
    paddingHorizontal: 16,
    paddingBottom: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerTitle: { textAlign: 'center', fontSize: 16, fontWeight: '600' },
  body: { flex: 1 },
});
