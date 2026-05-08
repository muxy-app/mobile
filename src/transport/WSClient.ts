import { WSError } from './errors';
import { EventBus } from './events';
import {
  type EventDataMap,
  type EventName,
  type IncomingEnvelope,
  type MethodName,
  type MethodParams,
  type MethodResult,
} from './protocol';
import { BackoffScheduler, type BackoffOptions } from './reconnect';

export type ConnectionState =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'reconnecting'
  | 'closed';

type LifecycleMap = {
  stateChange: ConnectionState;
  error: { code?: number; message: string };
};

type AnyEventMap = LifecycleMap & EventDataMap;

export type WSClientOptions = {
  url: string;
  requestTimeoutMs?: number;
  autoReconnect?: boolean;
  backoff?: BackoffOptions;
};

type Pending = {
  method: string;
  resolve: (value: unknown) => void;
  reject: (err: Error) => void;
  timeoutId: ReturnType<typeof setTimeout>;
  startedAt: number;
};

const DEFAULT_REQUEST_TIMEOUT_MS = 15_000;

export class WSClient {
  private url: string;
  private socket: WebSocket | null = null;
  private state: ConnectionState = 'idle';
  private readonly pending = new Map<string, Pending>();
  private readonly bus = new EventBus<AnyEventMap>();
  private readonly backoff: BackoffScheduler;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private intentionallyClosed = false;
  private readonly requestTimeoutMs: number;
  private readonly autoReconnect: boolean;
  private nextId = 1;

  constructor(opts: WSClientOptions) {
    this.url = opts.url;
    this.requestTimeoutMs = opts.requestTimeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS;
    this.autoReconnect = opts.autoReconnect ?? true;
    this.backoff = new BackoffScheduler(opts.backoff);
  }

  getState(): ConnectionState {
    return this.state;
  }

  getUrl(): string {
    return this.url;
  }

  setUrl(url: string): void {
    if (this.url === url) return;
    this.url = url;
    if (this.state === 'open' || this.state === 'connecting' || this.state === 'reconnecting') {
      this.cycleConnection();
    }
  }

  on<K extends keyof AnyEventMap>(event: K, listener: (data: AnyEventMap[K]) => void): () => void {
    return this.bus.on(event, listener);
  }

  connect(): void {
    if (this.state === 'open' || this.state === 'connecting') return;
    this.intentionallyClosed = false;
    this.backoff.reset();
    this.openSocket();
  }

  disconnect(): void {
    this.intentionallyClosed = true;
    this.clearReconnectTimer();
    this.closeSocket(1000, 'client disconnect');
    this.failAllPending(new WSError(0, 'Client disconnected'));
    this.setState('closed');
  }

  async request<M extends MethodName>(method: M, params: MethodParams<M>): Promise<MethodResult<M>> {
    if (this.state !== 'open' || !this.socket) {
      throw new WSError(0, `Cannot send "${method}": connection is ${this.state}`);
    }
    const id = String(this.nextId++);
    const envelope = { type: 'request', payload: { id, method, params } };

    return new Promise<MethodResult<M>>((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        if (this.pending.delete(id)) {
          reject(new WSError(408, `Request "${method}" timed out`));
        }
      }, this.requestTimeoutMs);

      this.pending.set(id, {
        method,
        resolve: resolve as (v: unknown) => void,
        reject,
        timeoutId,
        startedAt: Date.now(),
      });

      try {
        this.socket?.send(JSON.stringify(envelope));
      } catch (err) {
        this.pending.delete(id);
        clearTimeout(timeoutId);
        reject(err instanceof Error ? err : new Error(String(err)));
      }
    });
  }

  private cycleConnection(): void {
    this.closeSocket(1000, 'reconnecting with new url');
    this.failAllPending(new WSError(0, 'Connection cycled'));
    this.backoff.reset();
    this.openSocket();
  }

  private setState(state: ConnectionState): void {
    if (this.state === state) return;
    this.state = state;
    this.bus.emit('stateChange', state);
  }

  private openSocket(): void {
    this.setState(this.backoff.attempts === 0 ? 'connecting' : 'reconnecting');

    let socket: WebSocket;
    try {
      socket = new WebSocket(this.url);
    } catch (err) {
      this.bus.emit('error', { message: err instanceof Error ? err.message : String(err) });
      this.scheduleReconnect();
      return;
    }
    this.socket = socket;

    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.backoff.reset();
      this.setState('open');
    };

    socket.onmessage = (e: WebSocketMessageEvent) => {
      if (this.socket !== socket) return;
      this.handleMessage(e.data);
    };

    socket.onerror = () => {
      if (this.socket !== socket) return;
      this.bus.emit('error', { message: 'WebSocket error' });
    };

    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.failAllPending(new WSError(0, 'Connection closed'));
      if (this.intentionallyClosed) {
        this.setState('closed');
        return;
      }
      if (this.autoReconnect) {
        this.scheduleReconnect();
      } else {
        this.setState('closed');
      }
    };
  }

  private closeSocket(code: number, reason: string): void {
    const s = this.socket;
    if (!s) return;
    this.socket = null;
    s.onopen = null;
    s.onmessage = null;
    s.onerror = null;
    s.onclose = null;
    try {
      s.close(code, reason);
    } catch {
      void 0;
    }
  }

  private scheduleReconnect(): void {
    this.clearReconnectTimer();
    this.setState('reconnecting');
    const delay = this.backoff.next();
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, delay);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private failAllPending(err: Error): void {
    for (const p of this.pending.values()) {
      clearTimeout(p.timeoutId);
      p.reject(err);
    }
    this.pending.clear();
  }

  private handleMessage(raw: unknown): void {
    let parsed: IncomingEnvelope;
    try {
      parsed = JSON.parse(typeof raw === 'string' ? raw : String(raw)) as IncomingEnvelope;
    } catch {
      this.bus.emit('error', { message: 'Failed to parse incoming frame' });
      return;
    }

    if (parsed.type === 'response') {
      this.handleResponse(parsed);
      return;
    }
    if (parsed.type === 'event') {
      this.handleEvent(parsed);
    }
  }

  private handleResponse(env: Extract<IncomingEnvelope, { type: 'response' }>): void {
    const { id, result, error } = env.payload;
    const p = this.pending.get(id);
    if (!p) return;
    this.pending.delete(id);
    clearTimeout(p.timeoutId);
    if (error) {
      p.reject(new WSError(error.code, error.message));
    } else if (result) {
      p.resolve(result);
    } else {
      p.reject(new WSError(0, `Empty response for "${p.method}"`));
    }
  }

  private handleEvent(env: Extract<IncomingEnvelope, { type: 'event' }>): void {
    const { event, data } = env.payload;
    if (!isKnownEvent(event)) return;
    this.bus.emit(event, data as EventDataMap[typeof event]);
  }
}

function isKnownEvent(name: string): name is EventName {
  return (
    name === 'workspaceChanged' ||
    name === 'terminalOutput' ||
    name === 'terminalSnapshot' ||
    name === 'notificationReceived' ||
    name === 'projectsChanged' ||
    name === 'paneOwnershipChanged' ||
    name === 'themeChanged'
  );
}
