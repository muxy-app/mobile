import { AppState, type AppStateStatus, type NativeEventSubscription } from 'react-native';

import type { WSClient } from './WSClient';

export type AppStateBinderOptions = {
  reconnectGraceMs?: number;
  shouldReconnect?: () => boolean;
};

export class AppStateBinder {
  private subscription: NativeEventSubscription | null = null;
  private lastStatus: AppStateStatus = AppState.currentState;
  private readonly graceMs: number;
  private readonly shouldReconnect: () => boolean;

  constructor(private readonly client: WSClient, opts: AppStateBinderOptions = {}) {
    this.graceMs = opts.reconnectGraceMs ?? 0;
    this.shouldReconnect = opts.shouldReconnect ?? (() => true);
  }

  start(): void {
    if (this.subscription) return;
    this.subscription = AppState.addEventListener('change', this.handleChange);
  }

  stop(): void {
    this.subscription?.remove();
    this.subscription = null;
  }

  private handleChange = (next: AppStateStatus): void => {
    const prev = this.lastStatus;
    this.lastStatus = next;
    if (next === 'active' && prev !== 'active') {
      if (this.graceMs > 0) {
        setTimeout(this.reconnect, this.graceMs);
      } else {
        this.reconnect();
      }
    } else if ((next === 'background' || next === 'inactive') && prev === 'active') {
      this.client.disconnect();
    }
  };

  private reconnect = (): void => {
    if (!this.shouldReconnect()) return;
    this.client.connect();
  };
}
