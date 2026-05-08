const TABLE = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

export function bytesToBase64(bytes: Uint8Array): string {
  if (typeof globalThis.btoa === 'function') {
    let bin = '';
    for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]!);
    return globalThis.btoa(bin);
  }
  let out = '';
  let i = 0;
  while (i < bytes.length) {
    const b1 = bytes[i++]!;
    const b2 = i < bytes.length ? bytes[i++]! : -1;
    const b3 = i < bytes.length ? bytes[i++]! : -1;
    out += TABLE[b1 >> 2];
    out += TABLE[((b1 & 0x03) << 4) | (b2 >= 0 ? b2 >> 4 : 0)];
    out += b2 >= 0 ? TABLE[((b2 & 0x0f) << 2) | (b3 >= 0 ? b3 >> 6 : 0)] : '=';
    out += b3 >= 0 ? TABLE[b3 & 0x3f] : '=';
  }
  return out;
}

export function stringToBase64(input: string): string {
  const enc = new TextEncoder();
  return bytesToBase64(enc.encode(input));
}
