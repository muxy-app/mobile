import { AppState, type AppStateStatus } from 'react-native';

import { AppStateBinder } from './AppStateBinder';
import type { WSClient } from './WSClient';

describe('AppStateBinder', () => {
  let handler: ((state: AppStateStatus) => void) | null = null;
  let addEventListener: jest.SpyInstance;

  beforeEach(() => {
    handler = null;
    addEventListener = jest.spyOn(AppState, 'addEventListener').mockImplementation((_, next) => {
      handler = next;
      return { remove: jest.fn() } as never;
    });
  });

  afterEach(() => {
    addEventListener.mockRestore();
  });

  it('reconnects when the app returns active by default', () => {
    const connect = jest.fn();
    const disconnect = jest.fn();
    const binder = new AppStateBinder({ connect, disconnect } as unknown as WSClient);

    binder.start();
    handler?.('inactive');
    handler?.('active');

    expect(connect).toHaveBeenCalledTimes(1);
  });

  it('skips reconnect when the current state cannot connect', () => {
    const connect = jest.fn();
    const disconnect = jest.fn();
    const binder = new AppStateBinder({ connect, disconnect } as unknown as WSClient, {
      shouldReconnect: () => false,
    });

    binder.start();
    handler?.('inactive');
    handler?.('active');

    expect(connect).not.toHaveBeenCalled();
  });
});
