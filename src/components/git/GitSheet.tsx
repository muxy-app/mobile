import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useState } from 'react';
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

import { GitScreens, type GitRoute } from './GitScreens';
import { fileNameOf } from './ui';

type Props = {
  visible: boolean;
  onClose: () => void;
  projectId: string;
  rootRoute?: GitRootRoute;
};

export type GitRootRoute = 'overview' | 'worktrees';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

export function GitSheet({ visible, onClose, projectId, rootRoute = 'overview' }: Props) {
  const tokens = useTokens();
  const insets = useSafeAreaInsets();

  const sheetHeight = Math.min(SCREEN_HEIGHT * 0.92, SCREEN_HEIGHT - 60);

  const translateY = useSharedValue(sheetHeight);
  const overlay = useSharedValue(0);

  const [route, setRoute] = useState<GitRoute>(routeForRoot(rootRoute));

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
    if (visible) {
      translateY.value = sheetHeight;
      overlay.value = 0;
      setRoute(routeForRoot(rootRoute));
      overlay.value = withTiming(1, { duration: 220 });
      translateY.value = withSpring(0, { damping: 22, stiffness: 220, mass: 0.9 });
    }
  }, [visible, rootRoute, sheetHeight, translateY, overlay]);

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
                <SheetHeader
                  route={route}
                  rootRoute={rootRoute}
                  onBack={() => setRoute(parentRouteFor(route, rootRoute))}
                  onClose={dismiss}
                />
              </View>
            </GestureDetector>

            <View style={styles.body}>
              <GitScreens projectId={projectId} route={route} setRoute={setRoute} />
            </View>
          </Animated.View>
        </View>
      </View>
    </Modal>
  );
}

function SheetHeader({
  route,
  rootRoute,
  onBack,
  onClose,
}: {
  route: GitRoute;
  rootRoute: GitRootRoute;
  onBack: () => void;
  onClose: () => void;
}) {
  const tokens = useTokens();
  const isRoot = route.name === rootRoute;
  const title = headerTitleFor(route);
  return (
    <View style={[styles.header, { borderBottomColor: tokens.border.subtle }]}>
      {isRoot ? (
        <View style={styles.headerSide} />
      ) : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Back"
          onPress={onBack}
          hitSlop={10}
          style={({ pressed }) => [styles.headerSide, { opacity: pressed ? 0.5 : 1 }]}>
          <Ionicons name="chevron-back" size={22} color={tokens.text.primary} />
        </Pressable>
      )}
      <Text style={[styles.headerTitle, { color: tokens.text.primary }]} numberOfLines={1}>
        {title}
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Close"
        onPress={onClose}
        hitSlop={10}
        style={({ pressed }) => [styles.headerSide, styles.headerRight, { opacity: pressed ? 0.5 : 1 }]}>
        <Ionicons name="close" size={22} color={tokens.text.primary} />
      </Pressable>
    </View>
  );
}

function parentRouteFor(route: GitRoute, rootRoute: GitRootRoute): GitRoute {
  if (route.name === 'newWorktree') return { name: 'worktrees' };
  return routeForRoot(rootRoute);
}

function routeForRoot(rootRoute: GitRootRoute): GitRoute {
  if (rootRoute === 'worktrees') return { name: 'worktrees' };
  return { name: 'overview' };
}

function headerTitleFor(route: GitRoute): string {
  switch (route.name) {
    case 'overview':
      return 'Git';
    case 'branches':
      return 'Branches';
    case 'worktrees':
      return 'Worktrees';
    case 'commit':
      return 'Commit';
    case 'createPR':
      return 'New pull request';
    case 'pullRequest':
      return 'Pull request';
    case 'newBranch':
      return 'New branch';
    case 'newWorktree':
      return 'New worktree';
    case 'fileDiff':
      return fileNameOf(route.filePath);
  }
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
    paddingBottom: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerSide: { width: 44, height: 32, alignItems: 'flex-start', justifyContent: 'center', paddingHorizontal: 8 },
  headerRight: { alignItems: 'flex-end' },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 16, fontWeight: '600' },
  body: { flex: 1 },
});
