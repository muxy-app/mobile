export type RGB = { r: number; g: number; b: number };

export function intToRgb(n: number): RGB {
  return {
    r: (n >> 16) & 0xff,
    g: (n >> 8) & 0xff,
    b: n & 0xff,
  };
}

export function rgbToHex({ r, g, b }: RGB): string {
  return `#${[r, g, b].map((c) => clamp255(c).toString(16).padStart(2, '0')).join('')}`;
}

export function intToHex(n: number): string {
  return rgbToHex(intToRgb(n));
}

export function hexToRgb(hex: string): RGB {
  const normalized = hex.startsWith('#') ? hex.slice(1) : hex;
  const expanded =
    normalized.length === 3
      ? normalized
          .split('')
          .map((c) => c + c)
          .join('')
      : normalized;
  const n = parseInt(expanded, 16);
  if (Number.isNaN(n) || expanded.length !== 6) return { r: 0, g: 0, b: 0 };
  return intToRgb(n);
}

export function mix(a: RGB, b: RGB, t: number): RGB {
  const tt = clamp01(t);
  return {
    r: a.r + (b.r - a.r) * tt,
    g: a.g + (b.g - a.g) * tt,
    b: a.b + (b.b - a.b) * tt,
  };
}

export function lighten(c: RGB, t: number): RGB {
  return mix(c, { r: 255, g: 255, b: 255 }, t);
}

export function darken(c: RGB, t: number): RGB {
  return mix(c, { r: 0, g: 0, b: 0 }, t);
}

export function luminance({ r, g, b }: RGB): number {
  const channel = (v: number) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

export function isDark(c: RGB): boolean {
  return luminance(c) < 0.5;
}

export function bestContrast(bg: RGB, candidates: RGB[], fallback: RGB): RGB {
  let best: RGB | null = null;
  let bestRatio = 0;
  for (const c of candidates) {
    const ratio = contrastRatio(bg, c);
    if (ratio > bestRatio) {
      bestRatio = ratio;
      best = c;
    }
  }
  return best && bestRatio >= 3 ? best : fallback;
}

export function contrastRatio(a: RGB, b: RGB): number {
  const la = luminance(a);
  const lb = luminance(b);
  const [hi, lo] = la > lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}

function clamp255(n: number): number {
  return Math.max(0, Math.min(255, Math.round(n)));
}

function clamp01(n: number): number {
  return Math.max(0, Math.min(1, n));
}
