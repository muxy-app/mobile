import type { DeviceTheme } from '@/transport';

import {
  bestContrast,
  darken,
  intToRgb,
  isDark,
  lighten,
  mix,
  rgbToHex,
  type RGB,
} from './colorMath';
import type { ThemeMode, ThemeTokens } from './tokens';

const STATUS_INDICES = { danger: 1, success: 2, warning: 3 } as const;

export function canDeriveFromTheme(theme: DeviceTheme | null | undefined): boolean {
  if (!theme) return false;
  return typeof theme.themeFg === 'number' && typeof theme.themeBg === 'number';
}

export function deriveTokensFromTheme(theme: DeviceTheme): ThemeTokens {
  const fg = intToRgb(theme.themeFg ?? 0xffffff);
  const bg = intToRgb(theme.themeBg ?? 0x000000);
  const palette = (theme.themePalette ?? []).map((n) => intToRgb(n));

  const dark = isDark(bg);
  const mode: ThemeMode = dark ? 'dark' : 'light';

  const surfacePrimary = bg;
  const surfaceSecondary = dark ? lighten(bg, 0.06) : darken(bg, 0.04);
  const surfaceTertiary = dark ? lighten(bg, 0.12) : darken(bg, 0.08);

  const textPrimary = fg;
  const textSecondary = mix(fg, bg, 0.18);
  const textMuted = mix(fg, bg, 0.5);
  const textInverse = bg;

  const borderSubtle = mix(fg, bg, dark ? 0.85 : 0.88);
  const borderStrong = mix(fg, bg, dark ? 0.7 : 0.75);

  const accent = pickAccent(palette, bg, fg);
  const accentContrast = bestContrast(
    accent,
    [bg, fg, { r: 255, g: 255, b: 255 }, { r: 0, g: 0, b: 0 }],
    dark ? { r: 255, g: 255, b: 255 } : { r: 0, g: 0, b: 0 },
  );

  const danger = palette[STATUS_INDICES.danger] ?? (dark ? { r: 248, g: 113, b: 113 } : { r: 220, g: 38, b: 38 });
  const success = palette[STATUS_INDICES.success] ?? (dark ? { r: 52, g: 211, b: 153 } : { r: 22, g: 163, b: 74 });
  const warning = palette[STATUS_INDICES.warning] ?? (dark ? { r: 251, g: 191, b: 36 } : { r: 217, g: 119, b: 6 });

  return {
    mode,
    surface: {
      primary: rgbToHex(surfacePrimary),
      secondary: rgbToHex(surfaceSecondary),
      tertiary: rgbToHex(surfaceTertiary),
    },
    text: {
      primary: rgbToHex(textPrimary),
      secondary: rgbToHex(textSecondary),
      muted: rgbToHex(textMuted),
      inverse: rgbToHex(textInverse),
    },
    border: {
      subtle: rgbToHex(borderSubtle),
      strong: rgbToHex(borderStrong),
    },
    accent: {
      primary: rgbToHex(accent),
      contrast: rgbToHex(accentContrast),
    },
    status: {
      success: rgbToHex(success),
      warning: rgbToHex(warning),
      danger: rgbToHex(danger),
    },
  };
}

const ACCENT_PALETTE_INDEX = 4;

function pickAccent(palette: RGB[], _bg: RGB, fg: RGB): RGB {
  return palette[ACCENT_PALETTE_INDEX] ?? fg;
}
