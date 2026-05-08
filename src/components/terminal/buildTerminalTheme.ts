import { intToHex } from '@/theme/colorMath';
import type { ThemeTokens } from '@/theme/tokens';
import type { DeviceTheme } from '@/transport';

import type { TerminalTheme } from './terminalHtml';

const DEFAULT_PALETTE = [
  '#000000', '#cc0000', '#4e9a06', '#c4a000',
  '#3465a4', '#75507b', '#06989a', '#d3d7cf',
  '#555753', '#ef2929', '#8ae234', '#fce94f',
  '#729fcf', '#ad7fa8', '#34e2e2', '#eeeeec',
];

export function buildTerminalTheme(device: DeviceTheme | null, tokens: ThemeTokens): TerminalTheme {
  const palette =
    device?.themePalette && device.themePalette.length > 0
      ? device.themePalette.map((n) => intToHex(n))
      : [];

  const fg = device?.themeFg !== undefined ? intToHex(device.themeFg) : tokens.text.primary;
  const bg = device?.themeBg !== undefined ? intToHex(device.themeBg) : tokens.surface.primary;

  const at = (i: number) => palette[i] ?? DEFAULT_PALETTE[i] ?? '#ffffff';

  return {
    background: bg,
    foreground: fg,
    cursor: fg,
    cursorAccent: bg,
    selectionBackground: 'rgba(255,255,255,0.25)',
    black: at(0),
    red: at(1),
    green: at(2),
    yellow: at(3),
    blue: at(4),
    magenta: at(5),
    cyan: at(6),
    white: at(7),
    brightBlack: at(8),
    brightRed: at(9),
    brightGreen: at(10),
    brightYellow: at(11),
    brightBlue: at(12),
    brightMagenta: at(13),
    brightCyan: at(14),
    brightWhite: at(15),
  };
}
