import { BackoffScheduler } from './reconnect';

describe('BackoffScheduler', () => {
  let mathRandom: jest.SpyInstance;

  beforeEach(() => {
    mathRandom = jest.spyOn(Math, 'random').mockReturnValue(0.5);
  });

  afterEach(() => {
    mathRandom.mockRestore();
  });

  it('starts at zero attempts', () => {
    const s = new BackoffScheduler();
    expect(s.attempts).toBe(0);
  });

  it('returns the base delay on first attempt with neutral jitter', () => {
    const s = new BackoffScheduler({ baseMs: 500, capMs: 30_000, jitter: 0 });
    expect(s.next()).toBe(500);
    expect(s.attempts).toBe(1);
  });

  it('doubles on each attempt up to the cap', () => {
    const s = new BackoffScheduler({ baseMs: 100, capMs: 1000, jitter: 0 });
    expect(s.next()).toBe(100);
    expect(s.next()).toBe(200);
    expect(s.next()).toBe(400);
    expect(s.next()).toBe(800);
    expect(s.next()).toBe(1000);
    expect(s.next()).toBe(1000);
  });

  it('applies jitter symmetrically around the exponential value', () => {
    mathRandom.mockReturnValue(1);
    const s = new BackoffScheduler({ baseMs: 1000, capMs: 60_000, jitter: 0.5 });
    expect(s.next()).toBe(1500);

    mathRandom.mockReturnValue(0);
    const s2 = new BackoffScheduler({ baseMs: 1000, capMs: 60_000, jitter: 0.5 });
    expect(s2.next()).toBe(500);
  });

  it('never returns negative values', () => {
    mathRandom.mockReturnValue(0);
    const s = new BackoffScheduler({ baseMs: 100, capMs: 10_000, jitter: 5 });
    expect(s.next()).toBeGreaterThanOrEqual(0);
  });

  it('reset returns attempts to zero and restarts at base', () => {
    const s = new BackoffScheduler({ baseMs: 100, capMs: 10_000, jitter: 0 });
    s.next();
    s.next();
    s.next();
    s.reset();
    expect(s.attempts).toBe(0);
    expect(s.next()).toBe(100);
  });

  it('uses defaults when no options are provided', () => {
    const s = new BackoffScheduler();
    expect(typeof s.next()).toBe('number');
  });
});
