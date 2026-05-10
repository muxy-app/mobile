import { useEffect } from 'react';

import { applyActiveDevice, applyDemoMode, startConnectionLifecycle } from './connection';
import { useDevicesStore } from './devicesStore';
import { useSettingsStore } from './settingsStore';

export function useConnection(): void {
  const hasHydrated = useDevicesStore((s) => s.hasHydrated);
  const settingsHydrated = useSettingsStore((s) => s.hasHydrated);
  const demoMode = useSettingsStore((s) => s.demoMode);
  const activeDeviceId = useDevicesStore((s) => s.activeDeviceId);
  const activeHost = useDevicesStore((s) =>
    s.activeDeviceId ? s.devices.find((d) => d.id === s.activeDeviceId)?.host : undefined,
  );
  const activePort = useDevicesStore((s) =>
    s.activeDeviceId ? s.devices.find((d) => d.id === s.activeDeviceId)?.port : undefined,
  );
  const installDeviceID = useDevicesStore((s) => s.installDeviceID);

  useEffect(() => {
    return startConnectionLifecycle();
  }, []);

  useEffect(() => {
    if (!settingsHydrated) return;
    applyDemoMode(demoMode);
  }, [settingsHydrated, demoMode]);

  useEffect(() => {
    if (!hasHydrated) return;
    applyActiveDevice();
  }, [hasHydrated, activeDeviceId, activeHost, activePort, installDeviceID]);
}
