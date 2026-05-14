export type WorkspaceMenuCommand =
  | { type: 'newTab' }
  | { type: 'selectTab'; index: number };

export function tabShortcutToIndex(digit: number, tabCount: number): number | null {
  if (!Number.isInteger(digit)) return null;
  if (digit < 1 || digit > 9) return null;
  const index = digit - 1;
  if (index >= tabCount) return null;
  return index;
}
