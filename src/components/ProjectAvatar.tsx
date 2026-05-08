import { Image } from 'expo-image';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { Platform, StyleSheet, Text, View } from 'react-native';

import { useProjectLogo } from '@/state';
import { useTokens } from '@/theme';
import { resolveColor } from '@/theme/namedColors';

type Props = {
  projectId: string;
  name: string;
  icon?: string;
  iconColor?: string;
  hasCustomLogo: boolean;
  size?: number;
};

export function ProjectAvatar({ projectId, name, icon, iconColor, hasCustomLogo, size = 40 }: Props) {
  const tokens = useTokens();
  const logo = useProjectLogo(projectId, hasCustomLogo);

  const radius = Math.round(size * 0.22);
  const bg = resolveColor(iconColor) ?? tokens.accent.primary;
  const fg = contrastTextColor(bg);
  const initial = name.trim().charAt(0).toUpperCase() || '?';

  if (hasCustomLogo && logo) {
    return (
      <Image
        source={{ uri: logo }}
        style={{ width: size, height: size, borderRadius: radius }}
        contentFit="cover"
        transition={120}
      />
    );
  }

  return (
    <View
      style={[
        styles.fallback,
        { width: size, height: size, borderRadius: radius, backgroundColor: bg },
      ]}>
      {icon && Platform.OS === 'ios' ? (
        <SymbolView
          name={icon as SymbolViewProps['name']}
          tintColor={fg}
          size={size * 0.55}
          resizeMode="scaleAspectFit"
          fallback={
            <Text style={[styles.letter, { color: fg, fontSize: size * 0.45 }]}>{initial}</Text>
          }
        />
      ) : (
        <Text style={[styles.letter, { color: fg, fontSize: size * 0.45 }]}>{initial}</Text>
      )}
    </View>
  );
}

function contrastTextColor(hex: string): string {
  const m = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.exec(hex);
  if (!m) return '#FFFFFF';
  let h = m[1]!;
  if (h.length === 3) h = h.split('').map((c) => c + c).join('');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.55 ? '#000000' : '#FFFFFF';
}

const styles = StyleSheet.create({
  fallback: { alignItems: 'center', justifyContent: 'center' },
  letter: { fontWeight: '700' },
});
