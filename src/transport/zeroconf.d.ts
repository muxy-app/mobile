declare module 'react-native-zeroconf' {
  export type ZeroconfService = {
    name?: string;
    host?: string;
    port?: number;
    addresses?: string[];
    fullName?: string;
    txt?: Record<string, string>;
  };

  type ZeroconfEvents = {
    start: () => void;
    stop: () => void;
    error: (err: Error) => void;
    found: (name: string) => void;
    remove: (name: string) => void;
    resolved: (service: ZeroconfService) => void;
    update: () => void;
  };

  export default class Zeroconf {
    constructor();
    scan(type?: string, protocol?: string, domain?: string): void;
    stop(): void;
    getServices(): Record<string, ZeroconfService>;
    on<E extends keyof ZeroconfEvents>(event: E, listener: ZeroconfEvents[E]): this;
    off<E extends keyof ZeroconfEvents>(event: E, listener: ZeroconfEvents[E]): this;
    removeDeviceListeners(): void;
  }
}
