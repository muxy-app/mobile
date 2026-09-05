import { act, cleanup, renderHook } from '@testing-library/react-native';

import { client } from './connection';
import { useDevicesStore } from './devicesStore';
import { usePaneSessionStore } from './paneSessionStore';
import { sendTerminalInput, usePaneSession } from './usePaneSession';

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);
jest.mock('./connection', () => {
  const { WSClient } = jest.requireActual('../transport/WSClient');
  const client = new WSClient({ url: 'ws://muxy.test', autoReconnect: false });
  client.request = jest.fn().mockResolvedValue({ type: 'ok' });
  client.notify = jest.fn();
  return { client };
});

function callbacks() {
  return {
    onSnapshotBytes: jest.fn(),
    onTakeoverStart: jest.fn(),
    onTakeoverWrite: jest.fn(),
    onTakeoverEnd: jest.fn(),
    onWrite: jest.fn(),
  };
}

function emitSnapshot(bytes = 'c25hcHNob3Q=') {
  client.emitDemoEvent('terminalSnapshot', {
    type: 'terminalCells',
    value: { paneID: 'pane-1', bytes },
  });
}

describe('pane takeover lifecycle', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    useDevicesStore.setState({ connectionPhase: 'connected' });
    usePaneSessionStore.setState({ session: { kind: 'idle' } });
  });

  afterEach(() => {
    cleanup();
    jest.useRealTimers();
  });

  it('keeps receiving large replay until its snapshot even after the original timeout', async () => {
    const events = callbacks();
    renderHook(() => usePaneSession({ paneId: 'pane-1', cols: 40, rows: 24, ...events }));
    await act(async () => {});

    for (let index = 0; index < 3; index += 1) {
      await act(async () => {
        jest.advanceTimersByTime(1000);
        client.emitDemoEvent('terminalOutput', {
          type: 'terminalOutput',
          value: { paneID: 'pane-1', bytes: 'cmVwbGF5' },
        });
      });
    }

    expect(events.onTakeoverWrite).toHaveBeenCalledTimes(3);
    expect(events.onTakeoverEnd).not.toHaveBeenCalled();
    expect(events.onWrite).not.toHaveBeenCalled();
    expect(usePaneSessionStore.getState().session.kind).toBe('taking-over');

    await act(async () => emitSnapshot());
    expect(events.onTakeoverEnd).toHaveBeenCalledWith('c25hcHNob3Q=');
    expect(usePaneSessionStore.getState().session.kind).toBe('streaming');
  });

  it('starts the missing-snapshot fallback after the server acknowledges takeover', async () => {
    let acknowledge: (value: { type: 'ok' }) => void = () => {};
    jest.mocked(client.request).mockReturnValueOnce(new Promise((resolve) => {
      acknowledge = resolve;
    }));
    const events = callbacks();
    renderHook(() => usePaneSession({ paneId: 'pane-1', cols: 40, rows: 24, ...events }));

    await act(async () => jest.advanceTimersByTime(3000));
    await act(async () => acknowledge({ type: 'ok' }));
    expect(events.onTakeoverEnd).not.toHaveBeenCalled();

    await act(async () => emitSnapshot());
    expect(events.onTakeoverEnd).toHaveBeenCalledWith('c25hcHNob3Q=');
  });

  it('rejects input while reconnecting and retakes the pane before resuming it', async () => {
    const events = callbacks();
    renderHook(() => usePaneSession({ paneId: 'pane-1', cols: 40, rows: 24, ...events }));
    await act(async () => emitSnapshot());
    expect(sendTerminalInput('pane-1', 'YQ==')).toBe(true);

    await act(async () => useDevicesStore.setState({ connectionPhase: 'reconnecting' }));
    expect(usePaneSessionStore.getState().session).toEqual({ kind: 'disconnected', paneId: 'pane-1' });
    expect(sendTerminalInput('pane-1', 'Yg==')).toBe(false);

    await act(async () => useDevicesStore.setState({ connectionPhase: 'connected' }));
    expect(events.onTakeoverStart).toHaveBeenCalledTimes(2);
    expect(sendTerminalInput('pane-1', 'Yg==')).toBe(false);

    await act(async () => emitSnapshot());
    expect(sendTerminalInput('pane-1', 'Yw==')).toBe(true);
    expect(client.notify).toHaveBeenCalledTimes(2);
  });

  it('cancels abandoned snapshot waits when the terminal unmounts', async () => {
    const events = callbacks();
    const hook = renderHook(() => usePaneSession({ paneId: 'pane-1', cols: 40, rows: 24, ...events }));
    await act(async () => {});
    hook.unmount();

    await act(async () => {
      emitSnapshot();
      jest.advanceTimersByTime(5000);
    });
    expect(events.onTakeoverEnd).not.toHaveBeenCalled();
    expect(events.onSnapshotBytes).not.toHaveBeenCalled();
  });
});
