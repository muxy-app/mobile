const DELETE = '\x7f';

export function buildTerminalInputDiff(prev: string, next: string): string {
  let i = 0;
  const min = Math.min(prev.length, next.length);
  while (i < min && prev.charCodeAt(i) === next.charCodeAt(i)) i++;
  const retract = prev.length - i;
  const addition = next.slice(i);
  let out = '';
  for (let k = 0; k < retract; k++) out += DELETE;
  return out + addition;
}
