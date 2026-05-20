import {
  footerText,
  paywallButtonLabel,
  paywallSubtitle,
  paywallTitle,
  primaryCtaLabel,
  sheetBullets,
  sheetTitle,
} from './copy';
import { TRIAL_DURATION_MS, type Entitlement } from './entitlement';

const TRIAL: Entitlement = { kind: 'trial', msRemaining: TRIAL_DURATION_MS };
const ONE_DAY: Entitlement = { kind: 'trial', msRemaining: 1 };
const EXPIRED: Entitlement = { kind: 'expired' };
const UNLOCKED: Entitlement = { kind: 'unlocked' };
const LOADING: Entitlement = { kind: 'loading' };

describe('paywallTitle', () => {
  it('reflects entitlement state', () => {
    expect(paywallTitle(TRIAL)).toBe('Trial Active');
    expect(paywallTitle(EXPIRED)).toBe('Trial Ended');
    expect(paywallTitle(UNLOCKED)).toBe('Unlock Muxy');
  });
});

describe('paywallSubtitle', () => {
  it('pluralizes days remaining for a multi-day trial', () => {
    expect(paywallSubtitle(TRIAL)).toBe('3 days left in your free trial.');
  });

  it('uses singular when only one day remains', () => {
    expect(paywallSubtitle(ONE_DAY)).toBe('1 day left in your free trial.');
  });

  it('describes the expired state', () => {
    expect(paywallSubtitle(EXPIRED)).toContain('Your 3-day trial has ended');
  });
});

describe('primaryCtaLabel', () => {
  it('says Close when already unlocked', () => {
    expect(primaryCtaLabel({ entitlement: UNLOCKED, price: '$5' })).toBe('Close');
  });

  it('appends the price when provided', () => {
    expect(primaryCtaLabel({ entitlement: TRIAL, price: '$5' })).toContain('$5');
  });

  it('omits the price suffix when null', () => {
    expect(primaryCtaLabel({ entitlement: TRIAL, price: null })).not.toContain('—');
  });
});

describe('paywallButtonLabel', () => {
  it('appends the price when provided', () => {
    expect(paywallButtonLabel({ entitlement: TRIAL, price: '$5' })).toContain('$5');
  });

  it('uses the unlock verb', () => {
    expect(paywallButtonLabel({ entitlement: TRIAL, price: null })).toMatch(/^Unlock/);
  });
});

describe('footerText', () => {
  it('returns null when unlocked', () => {
    expect(footerText({ entitlement: UNLOCKED, price: '$5' })).toBeNull();
  });

  it('shows trial countdown', () => {
    expect(footerText({ entitlement: TRIAL, price: '$5' })).toBe('Trial: 3 days left');
    expect(footerText({ entitlement: ONE_DAY, price: '$5' })).toBe('Trial: 1 day left');
  });

  it('shows the loading copy with optional price', () => {
    expect(footerText({ entitlement: LOADING, price: '$5' })).toBe('Free for 3 days, then $5');
    expect(footerText({ entitlement: LOADING, price: null })).toBe('Free for 3 days');
  });

  it('shows the expired copy with optional price', () => {
    expect(footerText({ entitlement: EXPIRED, price: '$5' })).toBe('Trial ended — unlock for $5');
    expect(footerText({ entitlement: EXPIRED, price: null })).toBe('Trial ended');
  });
});

describe('sheetTitle', () => {
  it('reflects entitlement state', () => {
    expect(sheetTitle(LOADING)).toBe('Unlock Muxy');
    expect(sheetTitle(TRIAL)).toBe('Trial active');
    expect(sheetTitle(EXPIRED)).toBe('Trial ended');
    expect(sheetTitle(UNLOCKED)).toBe('Unlocked');
  });
});

describe('sheetBullets', () => {
  it('returns a non-empty list and mentions price when provided', () => {
    const withPrice = sheetBullets({ entitlement: TRIAL, price: '$5' });
    expect(withPrice.length).toBeGreaterThan(0);
    expect(withPrice.some((b) => b.includes('$5'))).toBe(true);
  });

  it('uses a fallback phrase when price is null', () => {
    const noPrice = sheetBullets({ entitlement: TRIAL, price: null });
    expect(noPrice.some((b) => b.includes('one-time purchase'))).toBe(true);
  });
});
