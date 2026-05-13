import { EventBus } from './events';

type Map = {
  count: number;
  message: { text: string };
};

describe('EventBus', () => {
  it('delivers emitted data to subscribers', () => {
    const bus = new EventBus<Map>();
    const fn = jest.fn();
    bus.on('count', fn);
    bus.emit('count', 7);
    expect(fn).toHaveBeenCalledWith(7);
  });

  it('supports multiple listeners on the same event', () => {
    const bus = new EventBus<Map>();
    const a = jest.fn();
    const b = jest.fn();
    bus.on('count', a);
    bus.on('count', b);
    bus.emit('count', 1);
    expect(a).toHaveBeenCalledWith(1);
    expect(b).toHaveBeenCalledWith(1);
  });

  it('does not invoke listeners on other events', () => {
    const bus = new EventBus<Map>();
    const fn = jest.fn();
    bus.on('count', fn);
    bus.emit('message', { text: 'hi' });
    expect(fn).not.toHaveBeenCalled();
  });

  it('returns an unsubscribe function', () => {
    const bus = new EventBus<Map>();
    const fn = jest.fn();
    const off = bus.on('count', fn);
    off();
    bus.emit('count', 1);
    expect(fn).not.toHaveBeenCalled();
  });

  it('emitting an event with no listeners is a no-op', () => {
    const bus = new EventBus<Map>();
    expect(() => bus.emit('count', 1)).not.toThrow();
  });

  it('swallows listener exceptions and continues delivery', () => {
    const bus = new EventBus<Map>();
    const a = jest.fn(() => {
      throw new Error('boom');
    });
    const b = jest.fn();
    bus.on('count', a);
    bus.on('count', b);
    expect(() => bus.emit('count', 1)).not.toThrow();
    expect(b).toHaveBeenCalledWith(1);
  });

  it('allows listeners to unsubscribe during emit without skipping others', () => {
    const bus = new EventBus<Map>();
    const b = jest.fn();
    const offA = jest.fn();
    const a = jest.fn(() => offA());
    const offARef = bus.on('count', () => {
      a();
      offARef();
    });
    bus.on('count', b);
    bus.emit('count', 1);
    expect(a).toHaveBeenCalled();
    expect(b).toHaveBeenCalledWith(1);
  });

  it('clear removes all listeners across events', () => {
    const bus = new EventBus<Map>();
    const a = jest.fn();
    const b = jest.fn();
    bus.on('count', a);
    bus.on('message', b);
    bus.clear();
    bus.emit('count', 1);
    bus.emit('message', { text: 'x' });
    expect(a).not.toHaveBeenCalled();
    expect(b).not.toHaveBeenCalled();
  });
});
