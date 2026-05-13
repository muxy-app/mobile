import { isWSError, WSError } from './errors';

describe('WSError', () => {
  it('captures code and message', () => {
    const err = new WSError(401, 'unauthorized');
    expect(err.code).toBe(401);
    expect(err.message).toBe('unauthorized');
    expect(err.name).toBe('WSError');
  });

  it('is an Error instance', () => {
    expect(new WSError(0, 'x')).toBeInstanceOf(Error);
  });
});

describe('isWSError', () => {
  it('returns true for WSError instances', () => {
    expect(isWSError(new WSError(500, 'boom'))).toBe(true);
  });

  it('returns false for plain Error', () => {
    expect(isWSError(new Error('plain'))).toBe(false);
  });

  it('returns false for non-error values', () => {
    expect(isWSError(null)).toBe(false);
    expect(isWSError(undefined)).toBe(false);
    expect(isWSError('error')).toBe(false);
    expect(isWSError({ code: 1, message: 'x' })).toBe(false);
  });
});
