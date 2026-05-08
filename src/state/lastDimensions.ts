let last: { cols: number; rows: number } = { cols: 80, rows: 24 };

export function getLastDimensions(): { cols: number; rows: number } {
  return last;
}

export function recordDimensions(cols: number, rows: number): void {
  if (cols > 0 && rows > 0) last = { cols, rows };
}
