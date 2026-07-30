import {
  deleteKnownHostFingerprint,
  writeKnownHostFingerprint,
} from './knownHosts';
import {
  deleteSSHCredential,
  readSSHCredential,
  writeSSHCredential,
} from './secrets';
import { useSSHStore } from './store';
import type { SSHConnection, SSHCredential } from './types';

export async function saveSSHConnection(
  connection: SSHConnection,
  credential: SSHCredential,
  trustedFingerprint?: string,
): Promise<void> {
  const existing = useSSHStore
    .getState()
    .connections.find((candidate) => candidate.id === connection.id);
  const previousCredential = existing
    ? await readSSHCredential(connection.id)
    : null;
  const endpointChanged =
    existing !== undefined &&
    (existing.host !== connection.host || existing.port !== connection.port);

  await writeSSHCredential(connection.id, credential);

  try {
    if (endpointChanged) {
      await deleteKnownHostFingerprint(connection.id);
    }
    if (trustedFingerprint) {
      await writeKnownHostFingerprint(
        connection.id,
        trustedFingerprint,
      );
    }
    useSSHStore.getState().upsertConnection(connection);
  } catch (error) {
    if (previousCredential) {
      await writeSSHCredential(connection.id, previousCredential);
    } else {
      await deleteSSHCredential(connection.id);
    }
    throw error;
  }
}

export async function deleteSSHConnection(
  connectionId: string,
): Promise<void> {
  await deleteKnownHostFingerprint(connectionId);
  await deleteSSHCredential(connectionId);
  useSSHStore.getState().removeConnection(connectionId);
}
