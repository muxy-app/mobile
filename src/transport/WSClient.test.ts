import { WSClient } from './WSClient';

class FakeWebSocket {
  static latest: FakeWebSocket | null = null;

  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: (() => void) | null = null;
  readonly sent: string[] = [];

  constructor(readonly url: string) {
    FakeWebSocket.latest = this;
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {}
}

describe('WSClient terminal input', () => {
  const NativeWebSocket = globalThis.WebSocket;

  beforeEach(() => {
    FakeWebSocket.latest = null;
    globalThis.WebSocket = FakeWebSocket as unknown as typeof WebSocket;
  });

  afterEach(() => {
    globalThis.WebSocket = NativeWebSocket;
    jest.restoreAllMocks();
  });

  it('sends fire-and-forget input without creating a request timeout', () => {
    const client = new WSClient({ url: 'ws://muxy.test', autoReconnect: false });
    client.connect();

    const socket = FakeWebSocket.latest;
    expect(socket).not.toBeNull();
    socket?.onopen?.();

    const timeoutSpy = jest.spyOn(globalThis, 'setTimeout');
    client.notify('terminalInput', {
      type: 'terminalInput',
      value: { paneID: 'pane-1', bytes: 'YQ==' },
    });

    expect(timeoutSpy).not.toHaveBeenCalled();
    expect(socket?.sent).toEqual([
      JSON.stringify({
        type: 'request',
        payload: {
          id: '1',
          method: 'terminalInput',
          params: {
            type: 'terminalInput',
            value: { paneID: 'pane-1', bytes: 'YQ==' },
          },
        },
      }),
    ]);
  });
});
