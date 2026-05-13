import { resolveColor } from './namedColors';

describe('resolveColor', () => {
  it('returns null for undefined or empty input', () => {
    expect(resolveColor(undefined)).toBeNull();
    expect(resolveColor('')).toBeNull();
  });

  it('passes through valid hex colors as-is', () => {
    expect(resolveColor('#fff')).toBe('#fff');
    expect(resolveColor('#abcdef')).toBe('#abcdef');
    expect(resolveColor('#ABCDEF12')).toBe('#ABCDEF12');
  });

  it('trims surrounding whitespace before matching', () => {
    expect(resolveColor('  #fff  ')).toBe('#fff');
    expect(resolveColor('  red  ')).toBe('#E5484D');
  });

  it('resolves known named colors case-insensitively', () => {
    expect(resolveColor('red')).toBe('#E5484D');
    expect(resolveColor('BLUE')).toBe('#3E63DD');
    expect(resolveColor('TeAl')).toBe('#12A594');
  });

  it('returns null for unknown names', () => {
    expect(resolveColor('mauve')).toBeNull();
  });

  it('rejects malformed hex strings', () => {
    expect(resolveColor('#gg')).toBeNull();
    expect(resolveColor('123456')).toBeNull();
  });
});
