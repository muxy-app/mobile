import { useEffect, useMemo, useState } from 'react';

import { useBillingStore } from './billingStore';
import { computeEntitlement, type Entitlement } from './entitlement';

export function useEntitlement(): Entitlement {
  const purchased = useBillingStore((s) => s.purchased);
  const trialStartedAt = useBillingStore((s) => s.trialStartedAt);
  const now = useNow(60_000);
  return useMemo(
    () => computeEntitlement({ purchased, trialStartedAt, now }),
    [purchased, trialStartedAt, now],
  );
}

export function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
