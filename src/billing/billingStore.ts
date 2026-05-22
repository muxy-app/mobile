import { create } from 'zustand';

import {
  buyUnlock,
  connect,
  fetchUnlockProduct,
  finalizePurchase,
  isPurchased,
  isUserCancelledPurchaseError,
  queryUnlockPurchases,
  subscribePurchases,
} from './iap';
import { PRODUCT_ID } from './productId';
import { loadTrialStartedAt, saveTrialStartedAt } from './trialStore';

type State = {
  ready: boolean;
  productId: string;
  productPrice: string | null;
  purchased: boolean;
  trialStartedAt: number | null;
  purchasing: boolean;
  restoring: boolean;
  error: string | null;
  initStarted: boolean;
};

type Actions = {
  init: () => Promise<void>;
  refresh: () => Promise<void>;
  startTrialIfAbsent: () => Promise<void>;
  buy: () => Promise<void>;
  restore: () => Promise<void>;
  setError: (error: string | null) => void;
};

export type BillingStore = State & Actions;

const initialState: State = {
  ready: false,
  productId: PRODUCT_ID,
  productPrice: null,
  purchased: false,
  trialStartedAt: null,
  purchasing: false,
  restoring: false,
  error: null,
  initStarted: false,
};

let unsubscribePurchases: (() => void) | null = null;

export const useBillingStore = create<BillingStore>()((set, get) => ({
  ...initialState,

  setError: (error) => set({ error }),

  init: async () => {
    if (get().initStarted) return;
    set({ initStarted: true });

    const trialStartedAt = await loadTrialStartedAt();
    set({ trialStartedAt });

    try {
      await connect();
    } catch (err) {
      set({ ready: true, error: errorMessage(err) });
      return;
    }

    unsubscribePurchases?.();
    unsubscribePurchases = await subscribePurchases(
      async (purchase) => {
        if (!isPurchased(purchase)) return;
        try {
          await finalizePurchase(purchase);
        } catch {}
        set({ purchased: true, purchasing: false, error: null });
      },
      (error) => {
        if (isUserCancelledPurchaseError(error)) {
          set({ purchasing: false, error: null });
          return;
        }
        set({ purchasing: false, error: error.message ?? null });
      },
    );

    try {
      const product = await fetchUnlockProduct();
      if (product) set({ productPrice: product.displayPrice });
    } catch {}

    try {
      const existing = await queryUnlockPurchases();
      const valid = existing.find(isPurchased);
      if (valid) {
        set({ purchased: true });
        try {
          await finalizePurchase(valid);
        } catch {}
      }
    } catch {}

    set({ ready: true });
  },

  refresh: async () => {
    try {
      const existing = await queryUnlockPurchases();
      const valid = existing.find(isPurchased);
      if (valid && !get().purchased) {
        set({ purchased: true });
        try {
          await finalizePurchase(valid);
        } catch {}
      }
    } catch {}
  },

  startTrialIfAbsent: async () => {
    if (get().trialStartedAt != null) return;
    const now = Date.now();
    await saveTrialStartedAt(now);
    set({ trialStartedAt: now });
  },

  buy: async () => {
    if (get().purchasing) return;
    set({ purchasing: true, error: null });
    try {
      await buyUnlock();
    } catch (err) {
      if (isUserCancelledPurchaseError(err)) {
        set({ purchasing: false, error: null });
        return;
      }
      set({ purchasing: false, error: errorMessage(err) });
    }
  },

  restore: async () => {
    if (get().restoring) return;
    set({ restoring: true, error: null });
    try {
      const existing = await queryUnlockPurchases();
      const valid = existing.find(isPurchased);
      if (valid) {
        try {
          await finalizePurchase(valid);
        } catch {}
        set({ purchased: true });
      }
    } catch (err) {
      set({ error: errorMessage(err) });
    } finally {
      set({ restoring: false });
    }
  },
}));

function errorMessage(err: unknown): string {
  if (err instanceof Error) return err.message;
  return 'Unexpected error';
}
