import * as Crypto from 'expo-crypto';

export function newDeviceID(): string {
  return Crypto.randomUUID();
}

export function newToken(): string {
  const bytes = Crypto.getRandomBytes(32);
  let hex = '';
  for (const b of bytes) hex += b.toString(16).padStart(2, '0');
  return hex;
}

export function newEntryId(): string {
  return Crypto.randomUUID();
}
