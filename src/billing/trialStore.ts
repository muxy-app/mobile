import * as SecureStore from 'expo-secure-store';

const TRIAL_STARTED_AT_KEY = 'muxy.trial.startedAt';

export async function loadTrialStartedAt(): Promise<number | null> {
  const raw = await SecureStore.getItemAsync(TRIAL_STARTED_AT_KEY);
  if (!raw) return null;
  const ms = Number(raw);
  return Number.isFinite(ms) && ms > 0 ? ms : null;
}

export async function saveTrialStartedAt(ms: number): Promise<void> {
  await SecureStore.setItemAsync(TRIAL_STARTED_AT_KEY, String(ms), {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}
