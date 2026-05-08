import { daysRemaining, type Entitlement } from './entitlement';

type CopyArgs = {
  entitlement: Entitlement;
  price: string | null;
};

export function paywallTitle(ent: Entitlement): string {
  if (ent.kind === 'trial') return 'Trial Active';
  if (ent.kind === 'expired') return 'Trial Ended';
  return 'Unlock Muxy';
}

export function paywallSubtitle(ent: Entitlement): string {
  if (ent.kind === 'trial') {
    const days = daysRemaining(ent.msRemaining);
    return `${days} day${days === 1 ? '' : 's'} left in your free trial.`;
  }
  if (ent.kind === 'expired') {
    return 'Your 3-day trial has ended. Unlock Muxy to keep connecting.';
  }
  return 'Pay once to connect to your Mac.';
}

export function primaryCtaLabel({ entitlement, price }: CopyArgs): string {
  const priceSuffix = price ? ` — ${price}` : '';
  if (entitlement.kind === 'unlocked') return 'Close';
  if (entitlement.kind === 'beta') return `Purchase early${priceSuffix}`;
  return `Unlock now${priceSuffix}`;
}

export function paywallButtonLabel({ entitlement, price }: CopyArgs): string {
  const priceSuffix = price ? ` — ${price}` : '';
  return `Unlock${priceSuffix}`;
}

export function footerText({ entitlement, price }: CopyArgs): string | null {
  if (entitlement.kind === 'unlocked') return null;
  if (entitlement.kind === 'beta') {
    return price ? `Support Muxy — ${price}` : 'Support Muxy';
  }
  if (entitlement.kind === 'loading') {
    return price ? `Free for 3 days, then ${price}` : 'Free for 3 days';
  }
  if (entitlement.kind === 'trial') {
    const days = daysRemaining(entitlement.msRemaining);
    return `Trial: ${days} day${days === 1 ? '' : 's'} left`;
  }
  return price ? `Trial ended — unlock for ${price}` : 'Trial ended';
}

export function sheetTitle(ent: Entitlement): string {
  if (ent.kind === 'beta') return 'Support Muxy';
  if (ent.kind === 'loading') return 'Unlock Muxy';
  if (ent.kind === 'trial') return 'Trial active';
  if (ent.kind === 'expired') return 'Trial ended';
  return 'Unlocked';
}

export function sheetBullets({ entitlement, price }: CopyArgs): string[] {
  const priceText = price ?? 'a one-time purchase';
  if (entitlement.kind === 'beta') {
    return [
      'During beta, connecting to your Mac is completely free — no countdown, no limits.',
      `When Muxy reaches 1.0, a 3-day free trial will start on first pairing. After that, connecting will require a one-time purchase of ${priceText}.`,
      'Purchasing now is optional. If you do, you keep access for life — no subscription, no recurring charges.',
      'Tied to your store account — works on all your devices.',
      'If you reinstall or switch devices, tap "Restore purchase" to recover access.',
    ];
  }
  return [
    'Free for 3 days starting from your first successful pairing.',
    `After the trial ends, connecting to a Mac requires a one-time purchase of ${priceText}.`,
    'Pay once. No subscription, no recurring charges.',
    'Tied to your store account — works on all your devices.',
    'If you reinstall or switch devices, tap "Restore purchase" to recover access.',
  ];
}
