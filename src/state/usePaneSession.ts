import { useEffect, useRef } from 'react';

import { client } from './connection';
import { useDevicesStore } from './devicesStore';
import { type PaneSession, usePaneSessionStore } from './paneSessionStore';

export type PaneSessionCallbacks = {
  onSnapshotBytes: (base64: string, cols?: number, rows?: number) => void;
  onWrite: (base64: string) => void;
};

export type UsePaneSessionOptions = PaneSessionCallbacks & {
  paneId: string | undefined;
  cols: number | null;
  rows: number | null;
};

const TAKEOVER_GRACE_MS = 2000;
const SNAPSHOT_WAIT_MS = 1500;
const RESIZE_RESYNC_DEBOUNCE_MS = 180;

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

function waitForSnapshot(paneId: string, timeoutMs: number): Promise<string | null> {
  return new Promise((resolve) => {
    let resolved = false;
    const off = client.on('terminalSnapshot', (event) => {
      if (resolved || event.value.paneID !== paneId) return;
      resolved = true;
      off();
      clearTimeout(timer);
      resolve(event.value.bytes);
    });
    const timer = setTimeout(() => {
      if (resolved) return;
      resolved = true;
      off();
      resolve(null);
    }, timeoutMs);
  });
}

export function usePaneSession({
  paneId,
  cols,
  rows,
  onSnapshotBytes,
  onWrite,
}: UsePaneSessionOptions) {
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const sessionKind = usePaneSessionStore((s) => s.session.kind);

  const callbacksRef = useRef<PaneSessionCallbacks>({ onSnapshotBytes, onWrite });
  callbacksRef.current = { onSnapshotBytes, onWrite };

  const dimsRef = useRef<{ cols: number; rows: number } | null>(null);
  if (cols !== null && rows !== null && cols > 0 && rows > 0) {
    dimsRef.current = { cols, rows };
  }
  const dimsReady = cols !== null && rows !== null && cols > 0 && rows > 0;

  const requestedDimsRef = useRef<{ cols: number; rows: number } | null>(null);
  const takeoverOutputBufferRef = useRef<string[]>([]);

  useEffect(() => {
    const offOutput = client.on('terminalOutput', (event) => {
      const session = usePaneSessionStore.getState().session;
      if (session.kind === 'taking-over' && event.value.paneID === session.paneId) {
        takeoverOutputBufferRef.current.push(event.value.bytes);
        return;
      }
      if (session.kind !== 'streaming' || event.value.paneID !== session.paneId) return;
      callbacksRef.current.onWrite(event.value.bytes);
    });

    const offSnapshot = client.on('terminalSnapshot', (event) => {
      const session = usePaneSessionStore.getState().session;
      if (session.kind !== 'streaming' || event.value.paneID !== session.paneId) return;
      const dims = requestedDimsRef.current;
      callbacksRef.current.onSnapshotBytes(event.value.bytes, dims?.cols, dims?.rows);
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
    if (connectionPhase !== 'connected') return;
    if (!dimsReady) return;
    const dims = dimsRef.current;
    if (!dims) return;

    let cancelled = false;

    const run = async () => {
      transition({ kind: 'taking-over', paneId });
      markTakeOver();
      takeoverOutputBufferRef.current = [];

      const snapshotPromise = waitForSnapshot(paneId, SNAPSHOT_WAIT_MS);
      requestedDimsRef.current = { cols: dims.cols, rows: dims.rows };

      try {
        await client.request('takeOverPane', {
          type: 'takeOverPane',
          value: { paneID: paneId, cols: dims.cols, rows: dims.rows },
        });
      } catch (err) {
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

      const snapshot = await snapshotPromise;
      if (cancelled) return;
      if (snapshot) callbacksRef.current.onSnapshotBytes(snapshot, dims.cols, dims.rows);
      const buffered = takeoverOutputBufferRef.current;
      takeoverOutputBufferRef.current = [];
      for (const bytes of buffered) {
        callbacksRef.current.onWrite(bytes);
      }

      const session = usePaneSessionStore.getState().session;
      if (session.kind === 'taking-over' && session.paneId === paneId) {
        transition({ kind: 'streaming', paneId });
      }
    };

    run();

    return () => {
      cancelled = true;
      takeoverOutputBufferRef.current = [];
      client
        .request('releasePane', { type: 'releasePane', value: { paneID: paneId } })
        .catch(() => {});
    };
  }, [paneId, connectionPhase, dimsReady]);

  useEffect(() => {
    if (!paneId || cols === null || rows === null) return;
    if (sessionKind !== 'streaming') return;
    const session = usePaneSessionStore.getState().session;
    if (session.kind !== 'streaming' || session.paneId !== paneId) return;
    client
      .request('terminalResize', {
        type: 'terminalResize',
        value: { paneID: paneId, cols, rows },
      })
      .catch(() => {});
    const resyncTimer = setTimeout(() => {
      const current = usePaneSessionStore.getState().session;
      if (current.kind !== 'streaming' || current.paneId !== paneId) return;
      markTakeOver();
      requestedDimsRef.current = { cols, rows };
      client
        .request('takeOverPane', {
          type: 'takeOverPane',
          value: { paneID: paneId, cols, rows },
        })
        .catch(() => {});
    }, RESIZE_RESYNC_DEBOUNCE_MS);
    return () => clearTimeout(resyncTimer);
  }, [paneId, cols, rows, sessionKind]);
}

export function sendTerminalInput(paneId: string, base64: string): void {
  const session = usePaneSessionStore.getState().session;
  if (session.kind !== 'streaming' || session.paneId !== paneId) return;
  client
    .request('terminalInput', {
      type: 'terminalInput',
      value: { paneID: paneId, bytes: base64 },
    })
    .catch(() => {});
}

export function reclaimPane(paneId: string, cols: number, rows: number): void {
  transition({ kind: 'taking-over', paneId });
  markTakeOver();
  client
    .request('takeOverPane', { type: 'takeOverPane', value: { paneID: paneId, cols, rows } })
    .then(() => {
      markTakeOver();
      const session = usePaneSessionStore.getState().session;
      if (session.kind === 'taking-over' && session.paneId === paneId) {
        transition({ kind: 'streaming', paneId });
      }
    })
    .catch((err) => {
      transition({
        kind: 'failed',
        paneId,
        reason: err instanceof Error ? err.message : 'Could not take control',
      });
    });
}
