import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
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

import { useTokens } from '@/theme';
import type { Project } from '@/transport';

import { FileBrowserScreen } from './FileBrowserScreen';
import { FilePreviewScreen } from './FilePreviewScreen';
import { MoveDestinationScreen } from './MoveDestinationScreen';
import { useFileManager } from './useFileManager';

type Props = {
  visible: boolean;
  onClose: () => void;
  project: Project;
  worktreeId?: string;
};

export function FileSheet({ visible, onClose, project, worktreeId }: Props) {
  const tokens = useTokens();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();
  const sheetHeight = Math.min(screenHeight * 0.92, screenHeight - 60);
  const manager = useFileManager({ projectId: project.id, worktreeId, visible });
  const translateY = useSharedValue(sheetHeight);
  const overlay = useSharedValue(0);

  const finishClose = useCallback(() => onClose(), [onClose]);

  const dismiss = useCallback(() => {
    overlay.value = withTiming(0, { duration: 220 });
    translateY.value = withTiming(sheetHeight, { duration: 220 }, (finished) => {
      if (finished) runOnJS(finishClose)();
    });
  }, [overlay, translateY, sheetHeight, finishClose]);

  const requestClose = useCallback(() => {
    if (!manager.dirty) {
      dismiss();
      return;
    }
    Alert.alert('Unsaved changes', 'Save this file before closing Files?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Discard', style: 'destructive', onPress: dismiss },
      {
        text: 'Save',
        onPress: () => {
          manager.save().then((saved) => {
            if (saved) dismiss();
          }).catch(() => {});
        },
      },
    ]);
  }, [manager, dismiss]);

  const goBack = useCallback(() => {
    if (!manager.dirty) {
      manager.returnToBrowser();
      return;
    }
    Alert.alert('Discard changes?', 'Your unsaved edits will be lost.', [
      { text: 'Keep editing', style: 'cancel' },
      {
        text: 'Discard',
        style: 'destructive',
        onPress: manager.returnToBrowser,
      },
    ]);
  }, [manager]);

  useEffect(() => {
    if (!visible) return;
    translateY.value = sheetHeight;
    overlay.value = 0;
    overlay.value = withTiming(1, { duration: 220 });
    translateY.value = withSpring(0, { damping: 22, stiffness: 220, mass: 0.9 });
  }, [visible, sheetHeight, translateY, overlay]);

  const panGesture = Gesture.Pan()
    .enabled(!manager.editing)
    .activeOffsetY(8)
    .failOffsetX([-20, 20])
    .onUpdate((event) => {
      if (event.translationY > 0) translateY.value = event.translationY;
    })
    .onEnd((event) => {
      if (event.translationY > 120 || event.velocityY > 600) {
        if (manager.dirty) {
          translateY.value = withSpring(0, { damping: 22, stiffness: 220 });
          runOnJS(requestClose)();
          return;
        }
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
      onRequestClose={requestClose}
      statusBarTranslucent>
      <View style={StyleSheet.absoluteFill} accessibilityViewIsModal>
        <Animated.View style={[StyleSheet.absoluteFill, overlayStyle]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Close Files"
            style={StyleSheet.absoluteFill}
            onPress={requestClose}
          />
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
                <FileSheetHeader
                  manager={manager}
                  onBack={goBack}
                  onClose={requestClose}
                />
              </View>
            </GestureDetector>
            <View style={styles.body}>
              {manager.route.name === 'browser' ? (
                <FileBrowserScreen manager={manager} project={project} />
              ) : manager.route.name === 'preview' ? (
                <FilePreviewScreen manager={manager} project={project} entry={manager.route.entry} />
              ) : (
                <MoveDestinationScreen
                  manager={manager}
                  project={project}
                  paths={manager.route.paths}
                />
              )}
            </View>
          </Animated.View>
        </View>
      </View>
    </Modal>
  );
}

function FileSheetHeader({
  manager,
  onBack,
  onClose,
}: {
  manager: ReturnType<typeof useFileManager>;
  onBack: () => void;
  onClose: () => void;
}) {
  const tokens = useTokens();
  const isBrowser = manager.route.name === 'browser';
  const title = manager.route.name === 'preview'
    ? manager.route.entry.name
    : manager.route.name === 'move'
      ? 'Move'
      : 'Files';

  const leftAction = isBrowser ? (
    <Pressable
      accessibilityRole="button"
      onPress={() => {
        if (manager.selectionMode) manager.clearSelection();
        else manager.setSelectionMode(true);
      }}
      style={({ pressed }) => [styles.headerSide, { opacity: pressed ? 0.55 : 1 }]}>
      <Text style={[styles.headerActionLabel, { color: tokens.accent.primary }]}>
        {manager.selectionMode ? 'Done' : 'Select'}
      </Text>
    </Pressable>
  ) : (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Back to files"
      onPress={onBack}
      style={({ pressed }) => [styles.headerSide, { opacity: pressed ? 0.55 : 1 }]}>
      <Ionicons name="chevron-back" size={22} color={tokens.text.primary} />
    </Pressable>
  );

  let rightAction: React.ReactNode;
  if (isBrowser) {
    rightAction = (
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Close Files"
        onPress={onClose}
        style={({ pressed }) => [styles.headerSide, styles.headerRight, { opacity: pressed ? 0.55 : 1 }]}>
        <Ionicons name="close" size={22} color={tokens.text.primary} />
      </Pressable>
    );
  } else if (manager.route.name === 'preview' && manager.previewKind === 'text' && manager.content) {
    rightAction = (
      <Pressable
        accessibilityRole="button"
        disabled={manager.busy}
        onPress={manager.editing ? manager.save : manager.beginEditing}
        style={({ pressed }) => [styles.headerSide, styles.headerRight, { opacity: manager.busy ? 0.45 : pressed ? 0.55 : 1 }]}>
        {manager.busy ? (
          <ActivityIndicator size="small" color={tokens.accent.primary} />
        ) : (
          <Text style={[styles.headerActionLabel, { color: tokens.accent.primary }]}>
            {manager.editing ? 'Save' : 'Edit'}
          </Text>
        )}
      </Pressable>
    );
  } else {
    rightAction = <View style={styles.headerSide} />;
  }

  return (
    <View style={[styles.header, { borderBottomColor: tokens.border.subtle }]}>
      {leftAction}
      <Text style={[styles.headerTitle, { color: tokens.text.primary }]} numberOfLines={1}>
        {title}
      </Text>
      {rightAction}
    </View>
  );
}

const styles = StyleSheet.create({
  sheetWrap: { flex: 1, justifyContent: 'flex-end' },
  sheet: { borderTopLeftRadius: 20, borderTopRightRadius: 20, borderCurve: 'continuous', borderTopWidth: StyleSheet.hairlineWidth, overflow: 'hidden' },
  handleArea: { paddingTop: 8, paddingBottom: 6, alignItems: 'center' },
  handle: { width: 38, height: 4, borderRadius: 2 },
  header: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 8, paddingBottom: 10, borderBottomWidth: StyleSheet.hairlineWidth },
  headerSide: { width: 60, height: 34, justifyContent: 'center', paddingHorizontal: 8 },
  headerRight: { alignItems: 'flex-end' },
  headerActionLabel: { fontSize: 14, fontWeight: '500' },
  headerTitle: { flex: 1, textAlign: 'center', fontSize: 16, fontWeight: '600' },
  body: { flex: 1 },
});
