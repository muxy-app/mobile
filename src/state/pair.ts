import { isWSError, WSClient, WSError, type Pairing } from '@/transport';

import { newEntryId } from './ids';

export type PairingPhase = 'connecting' | 'authenticating' | 'awaiting-approval';

export type PairInput = {
  host: string;
  port: number;
  installDeviceID: string;
  installDeviceName: string;
  token: string;
  onPhase?: (phase: PairingPhase) => void;
};

export type PairResult = {
  entryId: string;
  pairing: Pairing;
};

const CONNECT_TIMEOUT_MS = 8_000;
const APPROVAL_TIMEOUT_MS = 120_000;

export class PairingError extends Error {
  readonly code: number;
  readonly kind: 'connect' | 'denied' | 'timeout' | 'network' | 'unknown';

  constructor(kind: PairingError['kind'], code: number, message: string) {
    super(message);
    this.name = 'PairingError';
    this.kind = kind;
    this.code = code;
  }
}

export async function pairWithDevice(input: PairInput): Promise<PairResult> {
  const { host, port, installDeviceID, installDeviceName, token, onPhase } = input;
  const url = `ws://${host}:${port}`;

  const client = new WSClient({
    url,
    autoReconnect: false,
    requestTimeoutMs: APPROVAL_TIMEOUT_MS,
  });

  try {
    onPhase?.('connecting');
    await waitForOpen(client);

    onPhase?.('authenticating');
    try {
      const auth = await client.request('authenticateDevice', {
        type: 'authenticateDevice',
        value: { deviceID: installDeviceID, deviceName: installDeviceName, token },
      });
      return { entryId: newEntryId(), pairing: auth.value };
    } catch (err) {
      if (!isWSError(err) || err.code !== 401) throw err;
    }

    onPhase?.('awaiting-approval');
    const pair = await client.request('pairDevice', {
      type: 'pairDevice',
      value: { deviceID: installDeviceID, deviceName: installDeviceName, token },
    });
    return { entryId: newEntryId(), pairing: pair.value };
  } catch (err) {
    throw mapPairError(err);
  } finally {
    client.disconnect();
  }
}

function waitForOpen(client: WSClient): Promise<void> {
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (fn: () => void) => {
      if (settled) return;
      settled = true;
      offState();
      offError();
      clearTimeout(timer);
      fn();
    };

    const offState = client.on('stateChange', (state) => {
      if (state === 'open') finish(() => resolve());
      if (state === 'closed') {
        finish(() =>
          reject(new PairingError('connect', 0, 'Connection closed before pairing started')),
        );
      }
    });

    const offError = client.on('error', (e) => {
      finish(() => reject(new PairingError('network', 0, e.message)));
    });

    const timer = setTimeout(() => {
      finish(() =>
        reject(new PairingError('connect', 0, 'Could not reach Muxy at that address')),
      );
    }, CONNECT_TIMEOUT_MS);

    client.connect();
  });
}

function mapPairError(err: unknown): PairingError {
  if (err instanceof PairingError) return err;
  if (isWSError(err)) {
    if (err.code === 403) return new PairingError('denied', 403, 'Pairing was denied on the Mac');
    if (err.code === 408) return new PairingError('timeout', 408, 'Pairing timed out');
    return new PairingError('unknown', err.code, err.message);
  }
  if (err instanceof WSError) {
    return new PairingError('unknown', err.code, err.message);
  }
  if (err instanceof Error) return new PairingError('unknown', 0, err.message);
  return new PairingError('unknown', 0, 'Unknown error during pairing');
}

