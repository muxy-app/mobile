import { tabShortcutToIndex } from './workspaceCommands';

describe('tabShortcutToIndex', () => {
  it('maps command digits to zero-based tab indexes', () => {
    expect(tabShortcutToIndex(1, 9)).toBe(0);
    expect(tabShortcutToIndex(9, 9)).toBe(8);
  });

  it('ignores unavailable tabs', () => {
    expect(tabShortcutToIndex(4, 3)).toBeNull();
    expect(tabShortcutToIndex(0, 9)).toBeNull();
    expect(tabShortcutToIndex(10, 9)).toBeNull();
  });
});
