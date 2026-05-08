export type BackoffOptions = {
  baseMs?: number;
  capMs?: number;
  jitter?: number;
};

export class BackoffScheduler {
  private attempt = 0;
  private readonly base: number;
  private readonly cap: number;
  private readonly jitter: number;

  constructor(opts: BackoffOptions = {}) {
    this.base = opts.baseMs ?? 500;
    this.cap = opts.capMs ?? 30_000;
    this.jitter = opts.jitter ?? 0.3;
  }

  next(): number {
    const exp = Math.min(this.cap, this.base * 2 ** this.attempt);
    this.attempt += 1;
    const offset = exp * this.jitter * (Math.random() * 2 - 1);
    return Math.max(0, Math.round(exp + offset));
  }

  reset(): void {
    this.attempt = 0;
  }

  get attempts(): number {
    return this.attempt;
  }
}
