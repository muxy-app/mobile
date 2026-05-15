import {
  bestContrast,
  contrastRatio,
  darken,
  hexToRgb,
  intToHex,
  intToRgb,
  isDark,
  lighten,
  luminance,
  mix,
  rgbToHex,
  type RGB,
} from './colorMath';

const BLACK: RGB = { r: 0, g: 0, b: 0 };
const WHITE: RGB = { r: 255, g: 255, b: 255 };

describe('intToRgb', () => {
  it('extracts 8-bit channels from a packed 24-bit int', () => {
    expect(intToRgb(0xff8040)).toEqual({ r: 0xff, g: 0x80, b: 0x40 });
  });

  it('handles black and white', () => {
    expect(intToRgb(0x000000)).toEqual(BLACK);
    expect(intToRgb(0xffffff)).toEqual(WHITE);
  });
});

describe('rgbToHex / intToHex', () => {
  it('formats with zero-padded lower-case hex', () => {
    expect(rgbToHex({ r: 0, g: 16, b: 255 })).toBe('#0010ff');
  });

  it('clamps values outside 0-255', () => {
    expect(rgbToHex({ r: -10, g: 300, b: 128 })).toBe('#00ff80');
  });

  it('rounds fractional channels', () => {
    expect(rgbToHex({ r: 0.4, g: 0.6, b: 127.5 })).toBe('#000180');
  });

  it('intToHex round-trips through rgb', () => {
    expect(intToHex(0xabcdef)).toBe('#abcdef');
  });
});

describe('hexToRgb', () => {
  it('parses 6-digit hex with leading hash', () => {
    expect(hexToRgb('#ff8040')).toEqual({ r: 0xff, g: 0x80, b: 0x40 });
  });

  it('parses 6-digit hex without leading hash', () => {
    expect(hexToRgb('00aaff')).toEqual({ r: 0x00, g: 0xaa, b: 0xff });
  });

  it('expands 3-digit shorthand', () => {
    expect(hexToRgb('#abc')).toEqual({ r: 0xaa, g: 0xbb, b: 0xcc });
  });

  it('returns black for malformed input', () => {
    expect(hexToRgb('not-a-color')).toEqual(BLACK);
  });
});

describe('mix / lighten / darken', () => {
  it('returns the first color when t is 0', () => {
    expect(mix(BLACK, WHITE, 0)).toEqual(BLACK);
  });

  it('returns the second color when t is 1', () => {
    expect(mix(BLACK, WHITE, 1)).toEqual(WHITE);
  });

  it('returns the midpoint when t is 0.5', () => {
    expect(mix(BLACK, WHITE, 0.5)).toEqual({ r: 127.5, g: 127.5, b: 127.5 });
  });

  it('clamps t to [0, 1]', () => {
    expect(mix(BLACK, WHITE, -1)).toEqual(BLACK);
    expect(mix(BLACK, WHITE, 2)).toEqual(WHITE);
  });

  it('lighten moves toward white, darken toward black', () => {
    expect(lighten(BLACK, 1)).toEqual(WHITE);
    expect(darken(WHITE, 1)).toEqual(BLACK);
  });
});

describe('luminance / isDark', () => {
  it('returns 0 for black and 1 for white', () => {
    expect(luminance(BLACK)).toBe(0);
    expect(luminance(WHITE)).toBeCloseTo(1, 5);
  });

  it('treats white as not dark and black as dark', () => {
    expect(isDark(BLACK)).toBe(true);
    expect(isDark(WHITE)).toBe(false);
  });
});

describe('contrastRatio', () => {
  it('is 21 between black and white', () => {
    expect(contrastRatio(BLACK, WHITE)).toBeCloseTo(21, 5);
  });

  it('is symmetric', () => {
    const a: RGB = { r: 12, g: 34, b: 56 };
    const b: RGB = { r: 200, g: 210, b: 220 };
    expect(contrastRatio(a, b)).toBeCloseTo(contrastRatio(b, a), 10);
  });

  it('is 1 for identical colors', () => {
    expect(contrastRatio(WHITE, WHITE)).toBeCloseTo(1, 5);
  });
});

describe('bestContrast', () => {
  it('picks the candidate with the highest contrast against the background', () => {
    const bg: RGB = { r: 30, g: 30, b: 30 };
    const fallback: RGB = { r: 60, g: 60, b: 60 };
    const chosen = bestContrast(bg, [BLACK, WHITE, { r: 50, g: 50, b: 50 }], fallback);
    expect(chosen).toEqual(WHITE);
  });

  it('falls back when no candidate clears the 3:1 ratio', () => {
    const bg: RGB = { r: 100, g: 100, b: 100 };
    const fallback: RGB = { r: 0, g: 0, b: 0 };
    const chosen = bestContrast(bg, [{ r: 110, g: 110, b: 110 }, { r: 90, g: 90, b: 90 }], fallback);
    expect(chosen).toEqual(fallback);
  });

  it('falls back when the candidate list is empty', () => {
    const fallback: RGB = { r: 1, g: 2, b: 3 };
    expect(bestContrast(WHITE, [], fallback)).toBe(fallback);
  });
});
