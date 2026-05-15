import { DarkTheme, DefaultTheme, ThemeProvider as NavThemeProvider, type Theme as NavTheme } from '@react-navigation/native';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import * as SystemUI from 'expo-system-ui';
import { useEffect, useMemo } from 'react';
import { AppState, Platform, StyleSheet, useColorScheme, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { KeyboardProvider } from 'react-native-keyboard-controller';
import 'react-native-reanimated';

import { useBillingStore } from '@/billing';
import { loadNerdFont } from '@/lib/nerdFont';
import { useConnection, useDevicesStore, useSettingsStore } from '@/state';
import { ThemeProvider, useTheme, useTokens } from '@/theme';
import { hexToRgb, isDark } from '@/theme/colorMath';



SplashScreen.preventAutoHideAsync().catch(() => {});

function NavStack() {
  const { mode } = useTheme();
  const tokens = useTokens();
  const systemScheme = useColorScheme();
  useConnection();

  const devicesHydrated = useDevicesStore((s) => s.hasHydrated);
  const settingsHydrated = useSettingsStore((s) => s.hasHydrated);

  const ready = devicesHydrated && settingsHydrated;

  useEffect(() => {
    if (!ready) return;
    SplashScreen.hideAsync().catch(() => {});
  }, [ready]);

  useEffect(() => {
    loadNerdFont().catch(() => {});
  }, []);

  useEffect(() => {
    if (!ready) return;
    if (Platform.OS === 'ios') return;
    useBillingStore.getState().init().catch(() => {});
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        useBillingStore.getState().refresh().catch(() => {});
      }
    });
    return () => sub.remove();
  }, [ready]);

  useEffect(() => {
    SystemUI.setBackgroundColorAsync(tokens.surface.primary).catch(() => {});
  }, [tokens.surface.primary]);

  const navTheme = useMemo<NavTheme>(() => {
    const base = mode === 'dark' ? DarkTheme : DefaultTheme;
    return {
      ...base,
      dark: mode === 'dark',
      colors: {
        ...base.colors,
        primary: tokens.accent.primary,
        background: tokens.surface.primary,
        card: tokens.surface.primary,
        text: tokens.text.primary,
        border: tokens.border.subtle,
        notification: tokens.accent.primary,
      },
    };
  }, [mode, tokens.accent.primary, tokens.surface.primary, tokens.text.primary, tokens.border.subtle]);

  return (
    <NavThemeProvider value={navTheme}>
      <View style={[StyleSheet.absoluteFillObject, { backgroundColor: tokens.surface.primary }]} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: tokens.surface.primary },
          headerTitleStyle: { color: tokens.text.primary, fontWeight: '600' },
          headerTitleAlign: 'center',
          headerTintColor: tokens.text.primary,
          headerShadowVisible: false,
          contentStyle: { backgroundColor: tokens.surface.primary },
        }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="settings" options={{ presentation: 'modal' }} />
        <Stack.Screen name="add-device" options={{ presentation: 'modal' }} />
        <Stack.Screen name="paywall" options={{ presentation: 'fullScreenModal', headerShown: false }} />
        <Stack.Screen name="projects/index" />
        <Stack.Screen name="projects/[id]/index" />
        <Stack.Screen name="onboarding" options={{ headerShown: false, animation: 'fade' }} />
      </Stack>
      <StatusBar
        key={systemScheme}
        style={isDark(hexToRgb(tokens.text.primary)) ? 'dark' : 'light'}
      />
    </NavThemeProvider>
  );
}

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={styles.root}>
      <KeyboardProvider>
        <ThemeProvider>
          <NavStack />
        </ThemeProvider>
      </KeyboardProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
});
