import { base64ToBytes, base64ToString, bytesToBase64, stringToBase64 } from './base64';

function bytesFromArray(arr: number[]): Uint8Array {
  return Uint8Array.from(arr);
}

describe('bytesToBase64', () => {
  it('encodes the empty array as an empty string', () => {
    expect(bytesToBase64(new Uint8Array())).toBe('');
  });

  it('encodes ASCII text', () => {
    expect(bytesToBase64(bytesFromArray([77, 97, 110]))).toBe('TWFu');
  });

  it('pads with one = for two-byte input', () => {
    expect(bytesToBase64(bytesFromArray([77, 97]))).toBe('TWE=');
  });

  it('pads with == for one-byte input', () => {
    expect(bytesToBase64(bytesFromArray([77]))).toBe('TQ==');
  });

  it('encodes high-byte values', () => {
    expect(bytesToBase64(bytesFromArray([0xff, 0xfe, 0xfd]))).toBe('//79');
  });
});

describe('base64ToBytes', () => {
  it('decodes the empty string to an empty array', () => {
    expect(Array.from(base64ToBytes(''))).toEqual([]);
  });

  it('decodes ASCII text', () => {
    expect(Array.from(base64ToBytes('TWFu'))).toEqual([77, 97, 110]);
  });

  it('decodes single-padded input', () => {
    expect(Array.from(base64ToBytes('TWE='))).toEqual([77, 97]);
  });

  it('decodes double-padded input', () => {
    expect(Array.from(base64ToBytes('TQ=='))).toEqual([77]);
  });
});

describe('stringToBase64 / base64ToString', () => {
  it('round-trips ASCII', () => {
    const s = 'Hello, Muxy!';
    expect(base64ToString(stringToBase64(s))).toBe(s);
  });

  it('round-trips UTF-8 multibyte characters', () => {
    const s = 'café — 日本語 — 🚀';
    expect(base64ToString(stringToBase64(s))).toBe(s);
  });

  it('round-trips arbitrary byte sequences', () => {
    const bytes = Uint8Array.from(Array.from({ length: 256 }, (_, i) => i));
    const round = base64ToBytes(bytesToBase64(bytes));
    expect(Array.from(round)).toEqual(Array.from(bytes));
  });
});
