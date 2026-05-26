import { buildTerminalHtml, type TerminalTheme } from './terminalHtml';

const theme: TerminalTheme = {
  background: '#000000',
  foreground: '#ffffff',
  cursor: '#ffffff',
  cursorAccent: '#000000',
  selectionBackground: '#333333',
  black: '#000000',
  red: '#ff0000',
  green: '#00ff00',
  yellow: '#ffff00',
  blue: '#0000ff',
  magenta: '#ff00ff',
  cyan: '#00ffff',
  white: '#ffffff',
  brightBlack: '#555555',
  brightRed: '#ff5555',
  brightGreen: '#55ff55',
  brightYellow: '#ffff55',
  brightBlue: '#5555ff',
  brightMagenta: '#ff55ff',
  brightCyan: '#55ffff',
  brightWhite: '#ffffff',
};

describe('buildTerminalHtml', () => {
  it('can disable WebView command shortcuts when native menu commands own them', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
      commandShortcutsEnabled: false,
    });

    expect(html).toContain('"commandShortcutsEnabled":false');
    expect(html).toContain('INITIAL.commandShortcutsEnabled !== false');
  });

  it('keeps terminal viewport anchored after resize and snapshot updates', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });

    expect(html).toContain('function isScrolledToBottom()');
    expect(html).toContain('var shouldStickToBottom = isAltBuffer() || isScrolledToBottom();');
    expect(html).toContain('if (shouldStickToBottom) scrollToBottom();');
    expect(html).toContain('if (resizeShouldStickToBottom) scrollToBottom();');
  });

  it('accepts batched terminal output chunks from the native host', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });

    expect(html).toContain('Array.isArray(msg.bytes)');
    expect(html).toContain('pendingWrites.push(decodeBase64(msg.bytes[writeIndex]));');
  });
});
