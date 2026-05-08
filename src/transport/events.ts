type Listener<T> = (data: T) => void;

export class EventBus<EventMap extends Record<string, unknown>> {
  private readonly listeners: { [K in keyof EventMap]?: Set<Listener<EventMap[K]>> } = {};

  on<K extends keyof EventMap>(event: K, listener: Listener<EventMap[K]>): () => void {
    let set = this.listeners[event];
    if (!set) {
      set = new Set();
      this.listeners[event] = set;
    }
    set.add(listener);
    return () => {
      set?.delete(listener);
    };
  }

  emit<K extends keyof EventMap>(event: K, data: EventMap[K]): void {
    const set = this.listeners[event];
    if (!set) return;
    for (const listener of [...set]) {
      try {
        listener(data);
      } catch {
        void 0;
      }
    }
  }

  clear(): void {
    for (const key of Object.keys(this.listeners)) {
      delete this.listeners[key as keyof EventMap];
    }
  }
}
