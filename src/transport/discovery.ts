import { NativeModules } from 'react-native';
import Zeroconf, { type ZeroconfService } from 'react-native-zeroconf';

export type DiscoveredService = {
  name: string;
  host: string;
  port: number;
};

const SERVICE_TYPE = 'muxy';
const SERVICE_PROTOCOL = 'tcp';
const SERVICE_DOMAIN = 'local.';

const RESOLVE_TIMEOUT_MS = 4000;

export function isDiscoveryAvailable(): boolean {
  return NativeModules.RNZeroconf != null;
}

function pickHost(service: ZeroconfService): string | null {
  const ipv4 = service.addresses?.find((a) => /^\d+\.\d+\.\d+\.\d+$/.test(a));
  if (ipv4) return ipv4;
  const first = service.addresses?.[0];
  if (first) return first;
  if (service.host) return service.host;
  return null;
}

function toDiscovered(raw: ZeroconfService): DiscoveredService | null {
  if (!raw.name || !raw.port) return null;
  const host = pickHost(raw);
  if (!host) return null;
  return { name: raw.name, host, port: raw.port };
}

export type BrowseHandle = {
  stop: () => void;
};

export function browseServices(
  onUpdate: (services: DiscoveredService[]) => void,
  onError?: (err: Error) => void,
): BrowseHandle {
  if (!isDiscoveryAvailable()) {
    onUpdate([]);
    return { stop: () => {} };
  }

  const z = new Zeroconf();
  const resolved = new Map<string, DiscoveredService>();
  let stopped = false;

  const emit = () => {
    if (stopped) return;
    onUpdate(Array.from(resolved.values()));
  };

  const onResolved = (raw: ZeroconfService) => {
    const service = toDiscovered(raw);
    if (!service) return;
    resolved.set(service.name, service);
    emit();
  };

  const onRemove = (name: string) => {
    if (resolved.delete(name)) emit();
  };

  const onErr = (err: Error) => onError?.(err);

  z.on('resolved', onResolved);
  z.on('remove', onRemove);
  z.on('error', onErr);

  try {
    z.scan(SERVICE_TYPE, SERVICE_PROTOCOL, SERVICE_DOMAIN);
  } catch (err) {
    onError?.(err instanceof Error ? err : new Error(String(err)));
  }
  emit();

  return {
    stop: () => {
      if (stopped) return;
      stopped = true;
      z.off('resolved', onResolved);
      z.off('remove', onRemove);
      z.off('error', onErr);
      try {
        z.stop();
        z.removeDeviceListeners();
      } catch {}
    },
  };
}

export function resolveService(serviceName: string): Promise<DiscoveredService | null> {
  return new Promise((resolve) => {
    if (!isDiscoveryAvailable()) {
      resolve(null);
      return;
    }

    const z = new Zeroconf();
    let settled = false;

    const finish = (result: DiscoveredService | null) => {
      if (settled) return;
      settled = true;
      z.off('resolved', onResolved);
      clearTimeout(timer);
      try {
        z.stop();
        z.removeDeviceListeners();
      } catch {}
      resolve(result);
    };

    const onResolved = (raw: ZeroconfService) => {
      if (raw.name !== serviceName) return;
      const service = toDiscovered(raw);
      if (service) finish(service);
    };

    z.on('resolved', onResolved);
    const timer = setTimeout(() => finish(null), RESOLVE_TIMEOUT_MS);

    try {
      z.scan(SERVICE_TYPE, SERVICE_PROTOCOL, SERVICE_DOMAIN);
    } catch {
      finish(null);
    }
  });
}
