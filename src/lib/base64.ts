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

export function base64ToBytes(b64: string): Uint8Array {
  if (typeof globalThis.atob === 'function') {
    const bin = globalThis.atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  const clean = b64.replace(/[^A-Za-z0-9+/=]/g, '');
  const len = (clean.length * 3) / 4 - (clean.endsWith('==') ? 2 : clean.endsWith('=') ? 1 : 0);
  const out = new Uint8Array(Math.max(0, Math.floor(len)));
  let p = 0;
  for (let i = 0; i < clean.length; i += 4) {
    const c1 = TABLE.indexOf(clean[i]!);
    const c2 = TABLE.indexOf(clean[i + 1]!);
    const c3 = clean[i + 2] === '=' ? -1 : TABLE.indexOf(clean[i + 2]!);
    const c4 = clean[i + 3] === '=' ? -1 : TABLE.indexOf(clean[i + 3]!);
    out[p++] = (c1 << 2) | (c2 >> 4);
    if (c3 >= 0) out[p++] = ((c2 & 0x0f) << 4) | (c3 >> 2);
    if (c4 >= 0) out[p++] = ((c3 & 0x03) << 6) | c4;
  }
  return out;
}

export function base64ToString(b64: string): string {
  return new TextDecoder().decode(base64ToBytes(b64));
}
