import * as SecureStore from 'expo-secure-store';

import { newToken } from './ids';

const INSTALL_TOKEN_KEY = 'muxy.installToken';

export async function getOrCreateInstallToken(): Promise<string> {
  const existing = await SecureStore.getItemAsync(INSTALL_TOKEN_KEY);
  if (existing) return existing;
  const fresh = newToken();
  await SecureStore.setItemAsync(INSTALL_TOKEN_KEY, fresh, {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
  return fresh;
}

export async function readInstallToken(): Promise<string | null> {
  return SecureStore.getItemAsync(INSTALL_TOKEN_KEY);
}
