import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEY = 'muxy.ssh.knownHosts.v1';

let mutationQueue: Promise<void> = Promise.resolve();

export async function readKnownHostFingerprint(
  connectionId: string,
): Promise<string | null> {
  await mutationQueue;
  const knownHosts = await readKnownHosts();
  return knownHosts[connectionId] ?? null;
}

export function writeKnownHostFingerprint(
  connectionId: string,
  fingerprint: string,
): Promise<void> {
  const normalized = fingerprint.toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(normalized)) {
    return Promise.reject(new Error('The SSH host fingerprint is invalid.'));
  }
  return mutateKnownHosts((knownHosts) => ({
    ...knownHosts,
    [connectionId]: normalized,
  }));
}

export function deleteKnownHostFingerprint(
  connectionId: string,
): Promise<void> {
  return mutateKnownHosts((knownHosts) => {
    if (!(connectionId in knownHosts)) return knownHosts;
    const { [connectionId]: _removed, ...remaining } = knownHosts;
    return remaining;
  });
}

function mutateKnownHosts(
  update: (knownHosts: Record<string, string>) => Record<string, string>,
): Promise<void> {
  const operation = mutationQueue.then(async () => {
    const knownHosts = await readKnownHosts();
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(update(knownHosts)));
  });
  mutationQueue = operation.catch(() => {});
  return operation;
}

async function readKnownHosts(): Promise<Record<string, string>> {
  const stored = await AsyncStorage.getItem(STORAGE_KEY);
  if (!stored) return {};

  try {
    const parsed = JSON.parse(stored) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    return Object.fromEntries(
      Object.entries(parsed).filter(
        (entry): entry is [string, string] =>
          typeof entry[1] === 'string' &&
          /^[a-f0-9]{64}$/.test(entry[1]),
      ),
    );
  } catch {
    return {};
  }
}
