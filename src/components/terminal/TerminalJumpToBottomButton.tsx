import { Ionicons } from '@expo/vector-icons';
import { Pressable, type StyleProp, StyleSheet, type ViewStyle } from 'react-native';
import Animated from 'react-native-reanimated';

import { useTokens } from '@/theme';

type Props = {
  color: string;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
};

export function TerminalJumpToBottomButton({ color, onPress, style }: Props) {
  const tokens = useTokens();

  return (
    <Animated.View pointerEvents="box-none" style={[styles.container, style]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Jump to live output"
        hitSlop={4}
        onPress={onPress}
        style={({ pressed }) => [
          styles.button,
          {
            backgroundColor: tokens.surface.tertiary,
            borderColor: tokens.border.strong,
            opacity: pressed ? 0.7 : 1,
          },
        ]}>
        <Ionicons name="chevron-down" size={18} color={color} />
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    zIndex: 1,
  },
  button: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 20,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
    boxShadow: '0 3px 8px rgba(0, 0, 0, 0.18)',
  },
});
