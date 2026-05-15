import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react';
import { Appearance, useColorScheme } from 'react-native';

import { useDevicesStore } from '@/state';
import type { DeviceTheme } from '@/transport';

import { canDeriveFromTheme, deriveTokensFromTheme } from './deriveTokens';
import { defaultTokens, type ThemeMode, type ThemeTokens } from './tokens';

type ThemeContextValue = {
  tokens: ThemeTokens;
  mode: ThemeMode;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const systemScheme = useColorScheme();

  const pairing = useDevicesStore((s) => {
    const id = s.activeDeviceId;
    if (!id) return null;
    return s.devices.find((d) => d.id === id)?.pairing ?? null;
  });
  const lastTheme = useDevicesStore((s) => s.lastAppliedTheme);

  const deviceTheme = useMemo<DeviceTheme | null>(() => {
    if (pairing) {
      const fromPairing: DeviceTheme = {
        themeFg: pairing.themeFg,
        themeBg: pairing.themeBg,
        themePalette: pairing.themePalette,
      };
      if (canDeriveFromTheme(fromPairing)) return fromPairing;
    }
    if (lastTheme && canDeriveFromTheme(lastTheme)) return lastTheme;
    return null;
  }, [pairing, lastTheme]);

  const value = useMemo<ThemeContextValue>(() => {
    if (deviceTheme) {
      const tokens = deriveTokensFromTheme(deviceTheme);
      return { tokens, mode: tokens.mode };
    }

    const mode: ThemeMode = systemScheme === 'light' ? 'light' : 'dark';
    const tokens = mode === 'dark' ? defaultTokens.dark : defaultTokens.light;
    return { tokens, mode };
  }, [deviceTheme, systemScheme]);

  useEffect(() => {
    if (!deviceTheme) {
      Appearance.setColorScheme(null);
      return;
    }
    Appearance.setColorScheme(value.mode);
    return () => {
      Appearance.setColorScheme(null);
    };
  }, [deviceTheme, value.mode]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within a ThemeProvider');
  return ctx;
}

export function useTokens(): ThemeTokens {
  return useTheme().tokens;
}
