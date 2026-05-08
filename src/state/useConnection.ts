import { useEffect } from 'react';

import { applyActiveDevice, startConnectionLifecycle } from './connection';
import { useDevicesStore } from './devicesStore';

export function useConnection(): void {
  const hasHydrated = useDevicesStore((s) => s.hasHydrated);
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
    if (!hasHydrated) return;
    applyActiveDevice();
  }, [hasHydrated, activeDeviceId, activeHost, activePort, installDeviceID]);
}
