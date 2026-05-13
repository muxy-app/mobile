import {
  computeEntitlement,
  daysRemaining,
  PAYMENT_REQUIRED,
  TRIAL_DURATION_MS,
} from './entitlement';

describe('computeEntitlement', () => {
  it('returns unlocked when purchased, regardless of trial state', () => {
    expect(computeEntitlement({ purchased: true, trialStartedAt: null, now: 0 })).toEqual({
      kind: 'unlocked',
    });
    expect(
      computeEntitlement({ purchased: true, trialStartedAt: 1, now: 1 + TRIAL_DURATION_MS + 1 }),
    ).toEqual({ kind: 'unlocked' });
  });

  if (!PAYMENT_REQUIRED) {
    it('returns a full trial when payment is not required', () => {
      expect(
        computeEntitlement({ purchased: false, trialStartedAt: null, now: Date.now() }),
      ).toEqual({ kind: 'trial', msRemaining: TRIAL_DURATION_MS });
    });
  } else {
    it('returns loading when trial has not started yet', () => {
      expect(
        computeEntitlement({ purchased: false, trialStartedAt: null, now: Date.now() }),
      ).toEqual({ kind: 'loading' });
    });

    it('returns trial with remaining ms during the trial window', () => {
      const startedAt = 1_000_000;
      const now = startedAt + TRIAL_DURATION_MS / 2;
      expect(computeEntitlement({ purchased: false, trialStartedAt: startedAt, now })).toEqual({
        kind: 'trial',
        msRemaining: TRIAL_DURATION_MS / 2,
      });
    });

    it('returns expired once the trial window has elapsed', () => {
      const startedAt = 0;
      const now = TRIAL_DURATION_MS;
      expect(computeEntitlement({ purchased: false, trialStartedAt: startedAt, now })).toEqual({
        kind: 'expired',
      });
    });
  }
});

describe('daysRemaining', () => {
  it('rounds up partial days', () => {
    expect(daysRemaining(1)).toBe(1);
    expect(daysRemaining(24 * 60 * 60 * 1000)).toBe(1);
    expect(daysRemaining(24 * 60 * 60 * 1000 + 1)).toBe(2);
  });

  it('never returns less than 1', () => {
    expect(daysRemaining(0)).toBe(1);
    expect(daysRemaining(-100)).toBe(1);
  });

  it('returns 3 for the full trial duration', () => {
    expect(daysRemaining(TRIAL_DURATION_MS)).toBe(3);
  });
});
