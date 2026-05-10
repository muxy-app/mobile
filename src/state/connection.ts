import {
  DEMO_DEVICE_HOST,
  DEMO_DEVICE_ID,
  DEMO_DEVICE_NAME,
  DEMO_DEVICE_PORT,
  DemoBackend,
} from '@/demo/demoBackend';
import { AppStateBinder, isWSError, WSClient } from '@/transport';

import { resolveDeviceName } from './deviceName';
import { useDevicesStore } from './devicesStore';
import { readInstallToken } from './secureTokens';
import { useSettingsStore } from './settingsStore';

export const client = new WSClient({
  url: 'ws://0.0.0.0:0',
  autoReconnect: true,
  requestTimeoutMs: 15_000,
});

let demoBackend: DemoBackend | null = null;

function ensureDemoBackend(): DemoBackend {
  if (!demoBackend) {
    demoBackend = new DemoBackend((event, data) => client.emitDemoEvent(event, data));
    client.setDemoBackend(demoBackend);
  }
  return demoBackend;
}

function clearDemoBackend(): void {
  if (!demoBackend) return;
  demoBackend = null;
  client.setDemoBackend(null);
}

export function applyDemoMode(enabled: boolean): void {
  const s = useDevicesStore.getState();
  const hasDemoEntry = s.devices.some((d) => d.id === DEMO_DEVICE_ID);
  const activeId = s.activeDeviceId;

  if (enabled) {
    ensureDemoBackend();
    s.ensureInstallDeviceID();
    if (!hasDemoEntry) {
      s.upsertDevice({
        id: DEMO_DEVICE_ID,
        label: DEMO_DEVICE_NAME,
        host: DEMO_DEVICE_HOST,
        port: DEMO_DEVICE_PORT,
        pairedAt: new Date().toISOString(),
      });
    }
    if (activeId && activeId !== DEMO_DEVICE_ID) {
      s.setActiveDevice(null);
    }
  } else {
    clearDemoBackend();
    if (hasDemoEntry) s.removeDevice(DEMO_DEVICE_ID);
  }
}

let started = false;

export function startConnectionLifecycle(): () => void {
  if (started) return () => {};
  started = true;

  const offState = client.on('stateChange', async (state) => {
    const s = useDevicesStore.getState();
    const active = s.activeDeviceId ? s.devices.find((d) => d.id === s.activeDeviceId) : null;

    if (!active) {
      s.setConnection('idle');
      return;
    }

    if (state === 'connecting') return s.setConnection('connecting');
    if (state === 'reconnecting') return s.setConnection('reconnecting');
    if (state === 'closed') return s.setConnection('disconnected');
    if (state !== 'open') return;

    if (!s.installDeviceID) return;
    s.setConnection('authenticating');
    const targetEntryId = active.id;

    try {
      const isDemo = useSettingsStore.getState().demoMode && active.id === DEMO_DEVICE_ID;
      const token = isDemo ? 'demo-token' : await readInstallToken();
      if (!token) {
        s.setNeedsRepair(targetEntryId, true);
        s.setConnection('unauthorized', 'No saved credential — please pair again.');
        client.disconnect();
        return;
      }

      const result = await client.request('authenticateDevice', {
        type: 'authenticateDevice',
        value: {
          deviceID: s.installDeviceID,
          deviceName: resolveDeviceName(),
          token,
        },
      });

      const latest = useDevicesStore.getState();
      if (latest.activeDeviceId !== targetEntryId) return;

      latest.setPairing(targetEntryId, result.value);
      latest.setLastConnectedAt(targetEntryId, new Date().toISOString());
      latest.setNeedsRepair(targetEntryId, false);
      latest.setConnection('connected');

      const { themeFg, themeBg, themePalette } = result.value;
      if (themeFg !== undefined && themeBg !== undefined) {
        latest.setLastAppliedTheme({ themeFg, themeBg, themePalette });
      }
    } catch (err) {
      const latest = useDevicesStore.getState();
      if (latest.activeDeviceId !== targetEntryId) return;

      if (isWSError(err) && err.code === 401) {
        latest.setNeedsRepair(targetEntryId, true);
        latest.setConnection('unauthorized', 'This device was revoked. Re-pair to continue.');
        client.disconnect();
      } else {
        latest.setConnection(
          'disconnected',
          err instanceof Error ? err.message : 'Authentication failed',
        );
      }
    }
  });

  const offError = client.on('error', (e) => {
    const s = useDevicesStore.getState();
    if (s.connectionPhase === 'connected') return;
    s.setConnection(s.connectionPhase, e.message);
  });

  const binder = new AppStateBinder(client);
  binder.start();

  return () => {
    offState();
    offError();
    binder.stop();
    client.disconnect();
    started = false;
  };
}

export function applyActiveDevice(): void {
  const s = useDevicesStore.getState();
  const active = s.activeDeviceId ? s.devices.find((d) => d.id === s.activeDeviceId) : null;

  if (!active || !s.installDeviceID) {
    client.disconnect();
    s.setConnection('idle');
    return;
  }

  client.setUrl(`ws://${active.host}:${active.port}`);
  client.connect();
}
