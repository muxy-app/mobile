import { buildTerminalInputDiff } from './terminalInput';

describe('buildTerminalInputDiff', () => {
  it('uses terminal delete bytes when text is removed', () => {
    expect(buildTerminalInputDiff('abc', 'ab')).toBe('\x7f');
    expect(buildTerminalInputDiff('abc', 'a')).toBe('\x7f\x7f');
  });

  it('keeps additions after the shared prefix', () => {
    expect(buildTerminalInputDiff('ab', 'abcd')).toBe('cd');
    expect(buildTerminalInputDiff('abc', 'ax')).toBe('\x7f\x7fx');
  });
});
