import { isPurchased, isUserCancelledPurchaseError, type IapPurchase } from './iap';
import { PRODUCT_ID } from './productId';

describe('isUserCancelledPurchaseError', () => {
  it('recognizes the v15 user-cancelled code', () => {
    expect(isUserCancelledPurchaseError({ code: 'user-cancelled', message: 'Cancelled' })).toBe(
      true,
    );
  });

  it('recognizes the legacy Android user-cancelled code', () => {
    expect(isUserCancelledPurchaseError({ code: 'E_USER_CANCELLED', message: 'Cancelled' })).toBe(
      true,
    );
  });

  it('recognizes Android cancel messages without a code', () => {
    expect(isUserCancelledPurchaseError({ message: 'Payment is Cancelled.' })).toBe(true);
  });

  it('does not hide non-cancel purchase errors', () => {
    expect(isUserCancelledPurchaseError({ code: 'network-error', message: 'Network failed' })).toBe(
      false,
    );
  });
});

describe('isPurchased', () => {
  it('requires the unlock product and purchased state', () => {
    const purchase: IapPurchase = {
      productId: PRODUCT_ID,
      purchaseState: 'purchased',
      purchaseToken: 'token',
      transactionId: 'transaction',
    };

    expect(isPurchased(purchase)).toBe(true);
    expect(isPurchased({ ...purchase, purchaseState: 'pending' })).toBe(false);
    expect(isPurchased({ ...purchase, productId: 'other' })).toBe(false);
  });
});
