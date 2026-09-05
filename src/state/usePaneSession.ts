import { useCallback, useEffect, useRef, useState } from 'react';

import { client } from './connection';
import { useDevicesStore } from './devicesStore';
import { type PaneSession, usePaneSessionStore } from './paneSessionStore';

export type PaneSessionCallbacks = {
  onSnapshotBytes: (base64: string) => void;
  onTakeoverStart: () => void;
  onTakeoverWrite: (base64: string) => void;
  onTakeoverEnd: (snapshot: string | null) => void;
  onWrite: (base64: string) => void;
};

export type UsePaneSessionOptions = PaneSessionCallbacks & {
  paneId: string | undefined;
  cols: number | null;
  rows: number | null;
};

const TAKEOVER_GRACE_MS = 2000;
const SNAPSHOT_IDLE_MS = 1500;

let lastTakeOverAt = 0;

function transition(next: PaneSession) {
  usePaneSessionStore.getState().setSession(next);
}

function markTakeOver() {
  lastTakeOverAt = Date.now();
}

function withinTakeOverGrace(): boolean {
  return Date.now() - lastTakeOverAt < TAKEOVER_GRACE_MS;
}

function waitForSnapshot(paneId: string, timeoutMs: number) {
  let settled = false;
  let timeoutStarted = false;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let resolveSnapshot: (bytes: string | null) => void = () => {};
  const snapshot = new Promise<string | null>((resolve) => {
    resolveSnapshot = resolve;
  });

  const finish = (bytes: string | null) => {
    if (settled) return;
    settled = true;
    if (timer !== null) clearTimeout(timer);
    offSnapshot();
    offOutput();
    resolveSnapshot(bytes);
  };

  const startTimeout = () => {
    if (settled) return;
    timeoutStarted = true;
    if (timer !== null) clearTimeout(timer);
    timer = setTimeout(() => finish(null), timeoutMs);
  };

  const offSnapshot = client.on('terminalSnapshot', (event) => {
    if (event.value.paneID !== paneId) return;
    finish(event.value.bytes);
  });
  const offOutput = client.on('terminalOutput', (event) => {
    if (event.value.paneID !== paneId || !timeoutStarted) return;
    startTimeout();
  });

  return { snapshot, startTimeout, cancel: () => finish(null) };
}

export function usePaneSession({
  paneId,
  cols,
  rows,
  onSnapshotBytes,
  onTakeoverStart,
  onTakeoverWrite,
  onTakeoverEnd,
  onWrite,
}: UsePaneSessionOptions) {
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const [takeoverAttempt, setTakeoverAttempt] = useState(0);

  const callbacksRef = useRef<PaneSessionCallbacks>({
    onSnapshotBytes,
    onTakeoverStart,
    onTakeoverWrite,
    onTakeoverEnd,
    onWrite,
  });
  callbacksRef.current = {
    onSnapshotBytes,
    onTakeoverStart,
    onTakeoverWrite,
    onTakeoverEnd,
    onWrite,
  };

  const dimsRef = useRef<{ cols: number; rows: number } | null>(null);
  if (cols !== null && rows !== null && cols > 0 && rows > 0) {
    dimsRef.current = { cols, rows };
  }
  const dimsReady = cols !== null && rows !== null && cols > 0 && rows > 0;

  useEffect(() => {
    const offOutput = client.on('terminalOutput', (event) => {
      const session = usePaneSessionStore.getState().session;
      if (session.kind === 'taking-over' && event.value.paneID === session.paneId) {
        callbacksRef.current.onTakeoverWrite(event.value.bytes);
        return;
      }
      if (session.kind !== 'streaming' || event.value.paneID !== session.paneId) return;
      callbacksRef.current.onWrite(event.value.bytes);
    });

    const offSnapshot = client.on('terminalSnapshot', (event) => {
      const session = usePaneSessionStore.getState().session;
      if (session.kind !== 'streaming' || event.value.paneID !== session.paneId) return;
      callbacksRef.current.onSnapshotBytes(event.value.bytes);
    });

    const offOwnership = client.on('paneOwnershipChanged', (event) => {
      const session = usePaneSessionStore.getState().session;
      const ourPaneId = 'paneId' in session ? session.paneId : null;
      if (!ourPaneId || event.value.paneID !== ourPaneId) return;

      const devicesState = useDevicesStore.getState();
      const installDeviceID = devicesState.installDeviceID;
      const activeDevice = devicesState.activeDeviceId
        ? devicesState.devices.find((d) => d.id === devicesState.activeDeviceId)
        : null;
      const ourClientID = activeDevice?.pairing?.clientID ?? null;

      const owner = event.value.owner;
      const eventDeviceID = owner && 'remote' in owner ? owner.remote.deviceID : null;

      const weOwn =
        !!eventDeviceID &&
        ((!!ourClientID && eventDeviceID === ourClientID) ||
          (!!installDeviceID && eventDeviceID === installDeviceID));

      if (weOwn) {
        if (session.kind === 'lost' || session.kind === 'failed') {
          transition({ kind: 'streaming', paneId: ourPaneId });
        }
        return;
      }

      if (withinTakeOverGrace()) return;

      if (session.kind === 'streaming' || session.kind === 'taking-over') {
        const takenBy =
          owner && 'mac' in owner
            ? owner.mac.deviceName
            : owner && 'remote' in owner
              ? owner.remote.deviceName
              : undefined;
        transition({ kind: 'lost', paneId: ourPaneId, takenBy });
      }
    });

    return () => {
      offOutput();
      offSnapshot();
      offOwnership();
    };
  }, []);

  useEffect(() => {
    if (!paneId) {
      const current = usePaneSessionStore.getState().session;
      if (current.kind !== 'idle') {
        transition({ kind: 'idle' });
      }
      return;
    }
    if (connectionPhase !== 'connected') {
      const current = usePaneSessionStore.getState().session;
      if (current.kind !== 'disconnected' || current.paneId !== paneId) {
        transition({ kind: 'disconnected', paneId });
      }
      return;
    }
    if (!dimsReady) return;
    const dims = dimsRef.current;
    if (!dims) return;

    let cancelled = false;
    const snapshotWait = waitForSnapshot(paneId, SNAPSHOT_IDLE_MS);

    const run = async () => {
      transition({ kind: 'taking-over', paneId });
      markTakeOver();
      callbacksRef.current.onTakeoverStart();

      try {
        await client.request('takeOverPane', {
          type: 'takeOverPane',
          value: { paneID: paneId, cols: dims.cols, rows: dims.rows },
        });
      } catch (err) {
        snapshotWait.cancel();
        if (cancelled) return;
        const session = usePaneSessionStore.getState().session;
        if (session.kind === 'taking-over' && session.paneId === paneId) {
          transition({
            kind: 'failed',
            paneId,
            reason: err instanceof Error ? err.message : 'Could not take control',
          });
        }
        return;
      }

      if (cancelled) return;
      markTakeOver();
      snapshotWait.startTimeout();

      const snapshot = await snapshotWait.snapshot;
      if (cancelled) return;
      callbacksRef.current.onTakeoverEnd(snapshot);

      const session = usePaneSessionStore.getState().session;
      if (session.kind === 'taking-over' && session.paneId === paneId) {
        transition({ kind: 'streaming', paneId });
      }
    };

    run();

    return () => {
      cancelled = true;
      snapshotWait.cancel();
      client
        .request('releasePane', { type: 'releasePane', value: { paneID: paneId } })
        .catch(() => {});
    };
  }, [paneId, connectionPhase, dimsReady, takeoverAttempt]);

  return useCallback(() => {
    setTakeoverAttempt((attempt) => attempt + 1);
  }, []);
}

export function sendTerminalInput(paneId: string, base64: string): boolean {
  if (useDevicesStore.getState().connectionPhase !== 'connected') return false;
  const session = usePaneSessionStore.getState().session;
  if (session.kind !== 'streaming' || session.paneId !== paneId) return false;

  try {
    client.notify('terminalInput', {
      type: 'terminalInput',
      value: { paneID: paneId, bytes: base64 },
    });
    return true;
  } catch (error) {
    console.warn(`[terminal] input send failed: ${String(error)}`);
    return false;
  }
}

export function sendTerminalScroll(
  paneId: string,
  deltaX: number,
  deltaY: number,
  precise: boolean,
): void {
  const session = usePaneSessionStore.getState().session;
  if (session.kind !== 'streaming' || session.paneId !== paneId) return;
  client
    .request('terminalScroll', {
      type: 'terminalScroll',
      value: { paneID: paneId, deltaX, deltaY, precise },
    })
    .catch(() => {});
}
