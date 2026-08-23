import { Ionicons } from '@expo/vector-icons';
import {
  GlassView,
  isGlassEffectAPIAvailable,
  isLiquidGlassAvailable,
} from 'expo-glass-effect';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useTokens } from '@/theme';

type Props = {
  title: string;
  onBack: () => void;
  onOpenFiles: () => void;
  onOpenWorktrees: () => void;
  onOpenGit: () => void;
};

type HeaderButtonProps = {
  icon: keyof typeof Ionicons.glyphMap;
  accessibilityLabel: string;
  onPress: () => void;
  glassAvailable: boolean;
};

export function WorkspaceHeader({
  title,
  onBack,
  onOpenFiles,
  onOpenWorktrees,
  onOpenGit,
}: Props) {
  const tokens = useTokens();
  const insets = useSafeAreaInsets();
  const glassAvailable = isGlassEffectAPIAvailable() && isLiquidGlassAvailable();

  return (
    <View
      style={[
        styles.root,
        {
          paddingTop: insets.top,
          backgroundColor: tokens.surface.primary,
        },
      ]}>
      <View style={styles.row}>
        <View style={styles.side}>
          <HeaderButton
            icon="chevron-back"
            accessibilityLabel="Back"
            onPress={onBack}
            glassAvailable={glassAvailable}
          />
        </View>
        <Text numberOfLines={1} style={[styles.title, { color: tokens.text.primary }]}>
          {title}
        </Text>
        <View style={[styles.side, styles.actions]}>
          <HeaderButton
            icon="folder-open-outline"
            accessibilityLabel="Files"
            onPress={onOpenFiles}
            glassAvailable={glassAvailable}
          />
          <HeaderButton
            icon="layers-outline"
            accessibilityLabel="Worktrees"
            onPress={onOpenWorktrees}
            glassAvailable={glassAvailable}
          />
          <HeaderButton
            icon="git-branch-outline"
            accessibilityLabel="Git"
            onPress={onOpenGit}
            glassAvailable={glassAvailable}
          />
        </View>
      </View>
    </View>
  );
}

function HeaderButton({
  icon,
  accessibilityLabel,
  onPress,
  glassAvailable,
}: HeaderButtonProps) {
  const tokens = useTokens();

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      hitSlop={4}
      onPress={onPress}
      style={styles.button}>
      <GlassView
        isInteractive
        glassEffectStyle="regular"
        colorScheme={tokens.mode}
        style={[
          styles.glass,
          !glassAvailable && {
            backgroundColor: tokens.surface.secondary,
            borderColor: tokens.border.subtle,
            borderWidth: StyleSheet.hairlineWidth,
          },
        ]}>
        <Ionicons name={icon} size={20} color={tokens.text.primary} />
      </GlassView>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: {
    paddingHorizontal: 8,
  },
  row: {
    height: 48,
    flexDirection: 'row',
    alignItems: 'center',
  },
  side: {
    width: 124,
    flexDirection: 'row',
    alignItems: 'center',
  },
  actions: {
    justifyContent: 'space-between',
  },
  title: {
    flex: 1,
    fontSize: 17,
    fontWeight: '600',
    textAlign: 'center',
  },
  button: {
    width: 36,
    height: 36,
  },
  glass: {
    flex: 1,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
});
