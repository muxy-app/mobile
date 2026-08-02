import { runInNewContext } from 'node:vm';

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

function terminalRuntimeFunction<TFunction>(html: string, name: string): TFunction {
  const runtime = terminalRuntime(html);
  const start = runtime.indexOf(`function ${name}(`);
  const bodyStart = runtime.indexOf('{', start);
  if (start < 0 || bodyStart < 0) {
    throw new Error(`Runtime function not found: ${name}`);
  }
  let depth = 0;

  for (let index = bodyStart; index < runtime.length; index += 1) {
    if (runtime[index] === '{') depth += 1;
    if (runtime[index] !== '}') continue;
    depth -= 1;
    if (depth === 0) {
      return runInNewContext(`(${runtime.slice(start, index + 1)})`) as TFunction;
    }
  }

  throw new Error(`Runtime function not found: ${name}`);
}

type CursorViewportOffset = (
  cursorBounds: { top: number; bottom: number } | null,
  viewportHeight: number,
  keyboardOffset: number,
) => number;

type IsViewportFollowingBottom = (viewportY: number, baseY: number) => boolean;

type FocusCursorGeometry = (
  cursorColumn: number,
  cursorRow: number,
  cols: number,
  rows: number,
  screenWidth: number,
  screenHeight: number,
) => { left: number; top: number; width: number; height: number } | null;

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

  it('shifts when the keyboard covers the cursor and the cursor remains onscreen', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const cursorViewportOffset = terminalRuntimeFunction<CursorViewportOffset>(
      html,
      'cursorViewportOffset',
    );

    expect(cursorViewportOffset({ top: 500, bottom: 520 }, 700, 300)).toBe(300);
    expect(cursorViewportOffset({ top: 300, bottom: 420 }, 700, 300)).toBe(300);
  });

  it('preserves the viewport when the keyboard does not cover the cursor', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const cursorViewportOffset = terminalRuntimeFunction<CursorViewportOffset>(
      html,
      'cursorViewportOffset',
    );

    expect(cursorViewportOffset({ top: 300, bottom: 400 }, 700, 300)).toBe(0);
    expect(cursorViewportOffset(null, 700, 300)).toBe(0);
  });

  it('preserves the viewport when shifting would clip the cursor above the screen', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const cursorViewportOffset = terminalRuntimeFunction<CursorViewportOffset>(
      html,
      'cursorViewportOffset',
    );

    expect(cursorViewportOffset({ top: 250, bottom: 420 }, 700, 300)).toBe(0);
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

  it('forwards precise scroll pixels before applying local terminal routing', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
      forwardTerminalScroll: true,
    });
    const runtime = terminalRuntime(html);

    expect(html).toContain('"forwardTerminalScroll":true');
    expect(runtime).toContain("post({ type: 'scroll', deltaX: 0, deltaY: remoteDeltaY, precise: true });");
    expect(runtime.indexOf('pendingRemoteDeltaY -= scrollDelta;')).toBeLessThan(
      runtime.indexOf('scrollAccumulator += scrollDelta;'),
    );
    expect(runtime.match(/if \(INITIAL\.forwardTerminalScroll\) return;/g)).toHaveLength(2);
  });

  it('tracks whether the terminal viewport is following live output', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const runtime = terminalRuntime(html);
    const isViewportFollowingBottom =
      terminalRuntimeFunction<IsViewportFollowingBottom>(html, 'isViewportFollowingBottom');

    expect(isViewportFollowingBottom(0, 0)).toBe(true);
    expect(isViewportFollowingBottom(120, 120)).toBe(true);
    expect(isViewportFollowingBottom(119, 120)).toBe(false);
    expect(runtime).toContain('term.onScroll(syncFollowingBottom);');
    expect(runtime).toContain('term.onWriteParsed(syncFollowingBottom);');
    expect(runtime).toContain("post({ type: 'followingBottom', value: followingBottom });");
  });

  it('positions the focus cursor from terminal cell geometry', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const focusCursorGeometry = terminalRuntimeFunction<FocusCursorGeometry>(
      html,
      'focusCursorGeometry',
    );

    expect(focusCursorGeometry(12, 7, 80, 24, 800, 480)).toEqual({
      left: 120,
      top: 140,
      width: 2,
      height: 20,
    });
    expect(focusCursorGeometry(80, 23, 80, 24, 800, 480)?.left).toBe(790);
  });

  it('hides the focus cursor outside a usable viewport', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const focusCursorGeometry = terminalRuntimeFunction<FocusCursorGeometry>(
      html,
      'focusCursorGeometry',
    );

    expect(focusCursorGeometry(0, -1, 80, 24, 800, 480)).toBeNull();
    expect(focusCursorGeometry(0, 24, 80, 24, 800, 480)).toBeNull();
    expect(focusCursorGeometry(0, 0, 0, 24, 800, 480)).toBeNull();
    expect(focusCursorGeometry(0, 0, 80, 24, 0, 480)).toBeNull();
  });

  it('keeps a renderer-independent cursor synchronized while focused', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const runtime = terminalRuntime(html);

    expect(html).toContain('@keyframes terminal-focus-cursor-blink');
    expect(runtime).toContain("if (msg.type === 'setFocused')");
    expect(runtime).toContain("term.options.cursorInactiveStyle = focused ? 'none' : 'outline';");
    expect(runtime).toContain('term.onCursorMove(scheduleFocusCursorUpdate);');
    expect(runtime).toContain('term.onRender(scheduleFocusCursorUpdate);');
    expect(runtime).toContain('term.onScroll(scheduleFocusCursorUpdate);');
  });

  it('cancels pending scrolling before jumping to live output', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const runtime = terminalRuntime(html);

    expect(runtime).toContain(
      "case 'scrollToBottom':\n          scrollToBottom();",
    );
    expect(runtime).toContain(
      'cancelMomentum();\n    cancelFlush();\n    scrollAccumulator = 0;\n    try { term.scrollToBottom();',
    );
  });
});
