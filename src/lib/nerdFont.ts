import { Asset } from 'expo-asset';

export const NERD_FONT_FAMILY = 'JetBrainsMonoNF';

export type NerdFontBase64 = { regular: string; bold: string };

type Listener = (data: NerdFontBase64) => void;

let cached: NerdFontBase64 | null = null;
let inflight: Promise<NerdFontBase64> | null = null;
const listeners = new Set<Listener>();

export function getNerdFont(): NerdFontBase64 | null {
  return cached;
}

export function subscribeNerdFont(fn: Listener): () => void {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}

export function loadNerdFont(): Promise<NerdFontBase64> {
  if (cached) return Promise.resolve(cached);
  if (inflight) return inflight;

  inflight = (async () => {
    const [regularAsset, boldAsset] = await Promise.all([
      Asset.fromModule(require('../../assets/fonts/JetBrainsMonoNerdFontMono-Regular.ttf')).downloadAsync(),
      Asset.fromModule(require('../../assets/fonts/JetBrainsMonoNerdFontMono-Bold.ttf')).downloadAsync(),
    ]);
    const regularUri = regularAsset.localUri ?? regularAsset.uri;
    const boldUri = boldAsset.localUri ?? boldAsset.uri;
    const [regularBuf, boldBuf] = await Promise.all([
      fetch(regularUri).then((r) => r.arrayBuffer()),
      fetch(boldUri).then((r) => r.arrayBuffer()),
    ]);
    const result: NerdFontBase64 = {
      regular: bufferToBase64(regularBuf),
      bold: bufferToBase64(boldBuf),
    };
    cached = result;
    inflight = null;
    listeners.forEach((fn) => {
      try {
        fn(result);
      } catch {
        void 0;
      }
    });
    return result;
  })();

  return inflight;
}

function bufferToBase64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  const chunkSize = 0x8000;
  let bin = '';
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const slice = bytes.subarray(i, i + chunkSize);
    bin += String.fromCharCode.apply(null, slice as unknown as number[]);
  }
  return globalThis.btoa(bin);
}
