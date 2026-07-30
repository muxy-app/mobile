import * as SecureStore from 'expo-secure-store';

import type { SSHCredential } from './types';

const CREDENTIAL_KEY_PREFIX = 'muxy.ssh.credentials.';

export async function readSSHCredential(
  connectionId: string,
): Promise<SSHCredential | null> {
  const stored = await SecureStore.getItemAsync(
    credentialKey(connectionId),
  );
  if (!stored) return null;

  try {
    const credential = JSON.parse(stored) as unknown;
    if (!credential || typeof credential !== 'object') return null;
    if (
      'type' in credential &&
      credential.type === 'password' &&
      'password' in credential &&
      typeof credential.password === 'string' &&
      credential.password.trim().length > 0
    ) {
      return {
        type: credential.type,
        password: credential.password,
      };
    }
    const passphrase =
      'passphrase' in credential
        ? credential.passphrase
        : undefined;
    if (
      'type' in credential &&
      credential.type === 'privateKey' &&
      'privateKey' in credential &&
      typeof credential.privateKey === 'string' &&
      credential.privateKey.trim().length > 0 &&
      (passphrase === undefined || typeof passphrase === 'string')
    ) {
      return {
        type: credential.type,
        privateKey: credential.privateKey,
        passphrase,
      };
    }
    return null;
  } catch {
    return null;
  }
}

export async function writeSSHCredential(
  connectionId: string,
  credential: SSHCredential,
): Promise<void> {
  await SecureStore.setItemAsync(
    credentialKey(connectionId),
    JSON.stringify(credential),
    {
      keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    },
  );
}

export async function deleteSSHCredential(
  connectionId: string,
): Promise<void> {
  await SecureStore.deleteItemAsync(credentialKey(connectionId));
}

function credentialKey(connectionId: string): string {
  return CREDENTIAL_KEY_PREFIX + connectionId;
}
