import { isBillingEnforced } from './enforcement';

describe('isBillingEnforced', () => {
  it('does not enforce billing on Android development builds', () => {
    expect(isBillingEnforced('android', true)).toBe(false);
  });

  it('enforces billing on Android production builds', () => {
    expect(isBillingEnforced('android', false)).toBe(true);
  });

  it('does not enforce billing on iOS', () => {
    expect(isBillingEnforced('ios', false)).toBe(false);
  });
});
