import type { DeviceTheme } from '../transport/protocol';

import { canDeriveFromTheme, deriveTokensFromTheme } from './deriveTokens';

describe('canDeriveFromTheme', () => {
  it('is false for null or undefined', () => {
    expect(canDeriveFromTheme(null)).toBe(false);
    expect(canDeriveFromTheme(undefined)).toBe(false);
  });

  it('is false when fg or bg are missing', () => {
    expect(canDeriveFromTheme({})).toBe(false);
    expect(canDeriveFromTheme({ themeFg: 0xffffff })).toBe(false);
    expect(canDeriveFromTheme({ themeBg: 0x000000 })).toBe(false);
  });

  it('is true when both fg and bg are provided', () => {
    expect(canDeriveFromTheme({ themeFg: 0xffffff, themeBg: 0x000000 })).toBe(true);
  });
});

describe('deriveTokensFromTheme', () => {
  function darkTheme(): DeviceTheme {
    return { themeFg: 0xffffff, themeBg: 0x000000, themePalette: [] };
  }
  function lightTheme(): DeviceTheme {
    return { themeFg: 0x000000, themeBg: 0xffffff, themePalette: [] };
  }

  it('picks dark mode for a dark background', () => {
    expect(deriveTokensFromTheme(darkTheme()).mode).toBe('dark');
  });

  it('picks light mode for a light background', () => {
    expect(deriveTokensFromTheme(lightTheme()).mode).toBe('light');
  });

  it('sets surface.primary to the background color', () => {
    expect(deriveTokensFromTheme(darkTheme()).surface.primary).toBe('#000000');
    expect(deriveTokensFromTheme(lightTheme()).surface.primary).toBe('#ffffff');
  });

  it('sets text.primary to the foreground color', () => {
    expect(deriveTokensFromTheme(darkTheme()).text.primary).toBe('#ffffff');
    expect(deriveTokensFromTheme(lightTheme()).text.primary).toBe('#000000');
  });

  it('uses palette index 4 as the accent when available', () => {
    const theme: DeviceTheme = {
      themeFg: 0xffffff,
      themeBg: 0x000000,
      themePalette: [0x111111, 0x222222, 0x333333, 0x444444, 0xff00ff],
    };
    expect(deriveTokensFromTheme(theme).accent.primary).toBe('#ff00ff');
  });

  it('falls back to fg as accent when palette is short', () => {
    expect(deriveTokensFromTheme(darkTheme()).accent.primary).toBe('#ffffff');
  });

  it('maps palette indices 1/2/3 to danger/success/warning', () => {
    const theme: DeviceTheme = {
      themeFg: 0xffffff,
      themeBg: 0x000000,
      themePalette: [0x000000, 0xff0000, 0x00ff00, 0x0000ff],
    };
    const tokens = deriveTokensFromTheme(theme);
    expect(tokens.status.danger).toBe('#ff0000');
    expect(tokens.status.success).toBe('#00ff00');
    expect(tokens.status.warning).toBe('#0000ff');
  });

  it('uses sensible defaults for status when palette omits them', () => {
    const tokens = deriveTokensFromTheme(darkTheme());
    expect(tokens.status.danger).toMatch(/^#[0-9a-f]{6}$/);
    expect(tokens.status.success).toMatch(/^#[0-9a-f]{6}$/);
    expect(tokens.status.warning).toMatch(/^#[0-9a-f]{6}$/);
  });

  it('produces a contrast color that is readable against the accent', () => {
    const theme: DeviceTheme = {
      themeFg: 0xffffff,
      themeBg: 0x000000,
      themePalette: [0, 0, 0, 0, 0x000000],
    };
    const tokens = deriveTokensFromTheme(theme);
    expect(tokens.accent.contrast).toBe('#ffffff');
  });

  it('outputs all token fields as hex strings', () => {
    const tokens = deriveTokensFromTheme(darkTheme());
    const hexes = [
      tokens.surface.primary,
      tokens.surface.secondary,
      tokens.surface.tertiary,
      tokens.text.primary,
      tokens.text.secondary,
      tokens.text.muted,
      tokens.text.inverse,
      tokens.border.subtle,
      tokens.border.strong,
      tokens.accent.primary,
      tokens.accent.contrast,
    ];
    for (const h of hexes) expect(h).toMatch(/^#[0-9a-f]{6}$/);
  });
});
