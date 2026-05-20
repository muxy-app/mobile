import Constants, { ExecutionEnvironment } from 'expo-constants';
import { Platform } from 'react-native';

import { PRODUCT_ID } from './productId';

export type IapProduct = { id: string; displayPrice: string };
export type IapPurchase = {
  productId: string;
  purchaseState: 'pending' | 'purchased' | 'unknown';
  purchaseToken: string | null;
  transactionId: string | null;
};
export type IapError = { message: string };

export type PurchaseListener = (purchase: IapPurchase) => void;
export type ErrorListener = (error: IapError) => void;

export const IAP_AVAILABLE =
  Constants.executionEnvironment !== ExecutionEnvironment.StoreClient;

type IapModule = typeof import('react-native-iap');
type NativePurchase = Parameters<
  Parameters<IapModule['purchaseUpdatedListener']>[0]
>[0];

let modulePromise: Promise<IapModule | null> | null = null;

async function loadModule(): Promise<IapModule | null> {
  if (!IAP_AVAILABLE) return null;
  if (!modulePromise) {
    modulePromise = import('react-native-iap').catch(() => null);
  }
  return modulePromise;
}

function toIapPurchase(p: NativePurchase): IapPurchase {
  return {
    productId: p.productId,
    purchaseState: p.purchaseState,
    purchaseToken: p.purchaseToken ?? null,
    transactionId: p.id ?? null,
  };
}

export async function connect(): Promise<void> {
  const m = await loadModule();
  if (!m) return;
  await m.initConnection();
}

export async function fetchUnlockProduct(): Promise<IapProduct | null> {
  const m = await loadModule();
  if (!m) return null;
  const result = await m.fetchProducts({ skus: [PRODUCT_ID], type: 'in-app' });
  if (!result || !Array.isArray(result)) return null;
  const match = result.find((p) => p?.id === PRODUCT_ID);
  if (!match || !('displayPrice' in match)) return null;
  return { id: match.id, displayPrice: match.displayPrice };
}

export async function queryUnlockPurchases(): Promise<IapPurchase[]> {
  const m = await loadModule();
  if (!m) return [];
  const purchases = await m.getAvailablePurchases({ onlyIncludeActiveItemsIOS: true });
  return purchases
    .filter((p) => p.productId === PRODUCT_ID)
    .map(toIapPurchase);
}

export async function buyUnlock(): Promise<void> {
  const m = await loadModule();
  if (!m) throw new Error('In-app purchases are not available in this build.');
  await m.requestPurchase({
    type: 'in-app',
    request:
      Platform.OS === 'ios'
        ? { ios: { sku: PRODUCT_ID } }
        : { android: { skus: [PRODUCT_ID] } },
  });
}

export async function finalizePurchase(purchase: IapPurchase): Promise<void> {
  const m = await loadModule();
  if (!m) return;
  if (Platform.OS === 'android' && !purchase.purchaseToken) return;
  if (Platform.OS === 'ios' && !purchase.transactionId) return;
  const native = {
    id: purchase.transactionId ?? '',
    productId: purchase.productId,
    purchaseToken: purchase.purchaseToken ?? undefined,
  } as never;
  await m.finishTransaction({ purchase: native, isConsumable: false });
}

export async function subscribePurchases(
  onUpdate: PurchaseListener,
  onError: ErrorListener,
): Promise<() => void> {
  const m = await loadModule();
  if (!m) return () => {};
  const updateSub = m.purchaseUpdatedListener((p) => {
    try {
      onUpdate(toIapPurchase(p));
    } catch {}
  });
  const errorSub = m.purchaseErrorListener((e) => {
    try {
      onError({ message: e.message });
    } catch {}
  });
  return () => {
    updateSub.remove();
    errorSub.remove();
  };
}

export function isPurchased(purchase: IapPurchase): boolean {
  return purchase.productId === PRODUCT_ID && purchase.purchaseState === 'purchased';
}
