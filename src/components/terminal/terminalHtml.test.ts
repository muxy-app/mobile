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

function terminalRuntime(html: string): string {
  return html.slice(html.lastIndexOf('<script>'));
}

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

  it('keeps terminal dimensions immutable after its initial fit', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });

    const runtime = terminalRuntime(html);

    expect(runtime.match(/fit\.fit\(\)/g)).toHaveLength(1);
    expect(runtime).not.toContain('ResizeObserver');
    expect(runtime).not.toContain("addEventListener('resize'");
    expect(runtime).not.toContain('term.resize(');
    expect(runtime).not.toContain("case 'resize'");
    expect(runtime).not.toContain("case 'requestDimensions'");
  });

  it('selects and loads the final font before creating the terminal', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });

    expect(html).toContain('INITIAL.fontFamily = msg.fontFamily;');
    expect(html).toContain('installInitialFont(msg.font).then(startTerminal, startTerminal);');
    expect(html.indexOf('function installInitialFont')).toBeLessThan(html.indexOf('function startTerminal'));
  });

  it('moves terminal content with a transform while its WebView stays fixed', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });

    const runtime = terminalRuntime(html);

    expect(runtime).toContain("msg.type === 'setKeyboardOffset'");
    expect(runtime).toContain("root.style.transform = 'translate3d(0, '");
    expect(runtime).not.toContain('root.style.height');
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
