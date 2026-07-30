import {
  type EventSubscription,
  NativeModule,
  requireOptionalNativeModule,
} from 'expo-modules-core';

export type SSHAuthentication =
  | {
      type: 'password';
      password: string;
    }
  | {
      type: 'privateKey';
      privateKey: string;
      passphrase?: string;
    };

export type SSHConnectionConfig = {
  connectionId: string;
  host: string;
  port: number;
  username: string;
  auth: SSHAuthentication;
  cols: number;
  rows: number;
  termType: string;
  knownHostFingerprint?: string;
};

export type SSHConnectionState =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'failed';

export type SSHDataEvent = {
  connectionId: string;
  sessionId: string;
  dataBase64: string;
};

export type SSHStateChangeEvent = {
  connectionId: string;
  sessionId: string;
  state: SSHConnectionState;
  errorCode?: string;
  errorMessage?: string;
};

export type SSHClosedEvent = {
  connectionId: string;
  sessionId: string;
  reason?: string;
};

export type SSHHostKeyPromptEvent = {
  connectionId: string;
  sessionId: string;
  fingerprint: string;
  keyType: string;
};

type MuxySshEvents = {
  onData: (event: SSHDataEvent) => void;
  onStateChange: (event: SSHStateChangeEvent) => void;
  onClosed: (event: SSHClosedEvent) => void;
  onHostKeyPrompt: (event: SSHHostKeyPromptEvent) => void;
};

declare class MuxySshNativeModule extends NativeModule<MuxySshEvents> {
  isAvailable(): boolean;
  connect(config: SSHConnectionConfig): Promise<string>;
  write(sessionId: string, dataBase64: string): Promise<void>;
  resize(sessionId: string, cols: number, rows: number): Promise<void>;
  disconnect(sessionId: string): Promise<void>;
  testConnection(config: SSHConnectionConfig): Promise<void>;
  respondToHostKey(sessionId: string, accept: boolean): Promise<void>;
}

const nativeModule =
  requireOptionalNativeModule<MuxySshNativeModule>('MuxySsh');

export function isSSHAvailable(): boolean {
  return nativeModule?.isAvailable() === true;
}

export function connect(config: SSHConnectionConfig): Promise<string> {
  return requireNativeModule().connect(config);
}

export function write(
  sessionId: string,
  dataBase64: string,
): Promise<void> {
  return requireNativeModule().write(sessionId, dataBase64);
}

export function resize(
  sessionId: string,
  cols: number,
  rows: number,
): Promise<void> {
  return requireNativeModule().resize(sessionId, cols, rows);
}

export function disconnect(sessionId: string): Promise<void> {
  return requireNativeModule().disconnect(sessionId);
}

export function testConnection(
  config: SSHConnectionConfig,
): Promise<void> {
  return requireNativeModule().testConnection(config);
}

export function respondToHostKey(
  sessionId: string,
  accept: boolean,
): Promise<void> {
  return requireNativeModule().respondToHostKey(sessionId, accept);
}

export function addSSHDataListener(
  listener: MuxySshEvents['onData'],
): EventSubscription {
  return addListener('onData', listener);
}

export function addSSHStateChangeListener(
  listener: MuxySshEvents['onStateChange'],
): EventSubscription {
  return addListener('onStateChange', listener);
}

export function addSSHClosedListener(
  listener: MuxySshEvents['onClosed'],
): EventSubscription {
  return addListener('onClosed', listener);
}

export function addSSHHostKeyPromptListener(
  listener: MuxySshEvents['onHostKeyPrompt'],
): EventSubscription {
  return addListener('onHostKeyPrompt', listener);
}

function requireNativeModule(): MuxySshNativeModule {
  if (nativeModule) return nativeModule;
  throw new Error('SSH requires an iOS development or release build.');
}

function addListener<EventName extends keyof MuxySshEvents>(
  eventName: EventName,
  listener: MuxySshEvents[EventName],
): EventSubscription {
  if (nativeModule) {
    return nativeModule.addListener(eventName, listener);
  }
  return { remove: () => {} };
}
