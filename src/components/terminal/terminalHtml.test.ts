import { runInNewContext } from 'node:vm';
import { Terminal } from '@xterm/xterm';

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

function terminalRuntimeFunctionSource(html: string, name: string): string {
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
      return runtime.slice(start, index + 1);
    }
  }

  throw new Error(`Runtime function not found: ${name}`);
}

function terminalRuntimeFunction<TFunction>(html: string, name: string): TFunction {
  return runInNewContext(`(${terminalRuntimeFunctionSource(html, name)})`) as TFunction;
}

function createTakeoverRuntime() {
  const emulator = new Terminal({ cols: 40, rows: 8, scrollback: 5000 });
  const listeners = new Set<() => void>();
  const messages: { type: string; id?: number }[] = [];
  const writes: Uint8Array[] = [];
  const scrollToBottom = jest.fn(() => emulator.scrollToBottom());
  const html = buildTerminalHtml({ theme, fontFamily: 'monospace', fontSize: 12 });
  const runtime = terminalRuntime(html);
  const handlerStart = runtime.indexOf('terminalHandleMessage = function (msg)');
  const handlerEnd = runtime.indexOf('\n  setTerminalFocused(terminalFocused);', handlerStart);
  const functions = [
    'decodeBase64',
    'combineWrites',
    'cancelTakeoverPresentation',
    'completeTakeover',
    'writeTakeoverSnapshot',
  ].map((name) => terminalRuntimeFunctionSource(html, name));
  const handleMessage = runInNewContext(
    `${functions.join('\n')}\n${runtime.slice(handlerStart, handlerEnd)}\nterminalHandleMessage;`,
    {
      Uint8Array,
      atob: (value: string) => Buffer.from(value, 'base64').toString('binary'),
      performance: { now: () => Date.now() },
      term: {
        rows: emulator.rows,
        reset: () => emulator.reset(),
        write: (bytes: string | Uint8Array, callback?: () => void) => {
          if (bytes.length > 0) writes.push(Uint8Array.from(Buffer.from(bytes)));
          emulator.write(bytes, callback);
        },
        refresh: () => {},
        onRender: (listener: () => void) => {
          listeners.add(listener);
          return { dispose: () => listeners.delete(listener) };
        },
      },
      root: { setAttribute: () => {}, removeAttribute: () => {} },
      pendingWrites: [],
      flushScheduled: false,
      takeoverInProgress: false,
      takeoverId: 0,
      takeoverStartedAt: 0,
      takeoverByteCount: 0,
      takeoverWrites: [],
      takeoverRenderSubscription: null,
      post: (message: { type: string; id?: number }) => messages.push(message),
      scrollToBottom,
      readFollowingBottom: () => emulator.buffer.active.viewportY === emulator.buffer.active.baseY,
      syncFollowingBottom: () => {},
      scheduleFocusCursorUpdate: () => {},
      isAltBuffer: () => emulator.buffer.active.type === 'alternate',
      reportError: (message: string, error: Error) => { throw error; },
    },
  ) as (message: object) => void;

  return {
    emulator,
    handleMessage,
    messages,
    writes,
    scrollToBottom,
    drain: () => new Promise<void>((resolve) => emulator.write('', resolve)),
    render: () => [...listeners].forEach((listener) => listener()),
  };
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

describe('terminal takeover playback', () => {
  let runtime: ReturnType<typeof createTakeoverRuntime>;

  beforeEach(() => {
    runtime = createTakeoverRuntime();
  });

  afterEach(() => runtime.emulator.dispose());

  it('preserves split UTF-8 and escape sequences in one replay ending at the live screen', async () => {
    const replay = Buffer.from('\x1b[31mhistory 🌍\x1b[0m\r\n'.repeat(30));
    const snapshot = Buffer.from('\x1b[2J\x1b[Hlive$ ');
    runtime.handleMessage({ type: 'takeover', id: 1 });
    for (let offset = 0; offset < replay.length; offset += 17) {
      runtime.handleMessage({
        type: 'takeoverWrite',
        id: 1,
        bytes: replay.subarray(offset, offset + 17).toString('base64'),
      });
    }
    await runtime.drain();

    expect(runtime.writes).toHaveLength(0);
    expect(runtime.emulator.buffer.active.getLine(0)?.translateToString(true)).toBe('');

    runtime.handleMessage({ type: 'takeoverEnd', id: 1, snapshot: snapshot.toString('base64') });
    await runtime.drain();

    expect(runtime.writes).toHaveLength(1);
    expect(Buffer.from(runtime.writes[0] ?? [])).toEqual(Buffer.concat([replay, snapshot]));
    const buffer = runtime.emulator.buffer.active;
    expect(buffer.baseY).toBeGreaterThan(0);
    expect(buffer.viewportY).toBe(buffer.baseY);
    expect(buffer.getLine(buffer.viewportY)?.translateToString(true)).toBe('live$ ');
    expect(buffer.getLine(buffer.baseY - 1)?.translateToString(true)).toBe('history 🌍');
    expect(runtime.messages).toHaveLength(0);

    runtime.render();
    expect(runtime.messages).toEqual([expect.objectContaining({ type: 'takeoverComplete', id: 1 })]);
  });

  it('ignores stale chunks and completion callbacks when takeover is replaced', async () => {
    runtime.handleMessage({ type: 'takeover', id: 1 });
    runtime.handleMessage({ type: 'takeoverWrite', id: 1, bytes: Buffer.from('old\r\n'.repeat(50)).toString('base64') });
    runtime.handleMessage({ type: 'takeoverEnd', id: 1, snapshot: null });
    runtime.handleMessage({ type: 'takeover', id: 2 });
    runtime.handleMessage({ type: 'takeoverWrite', id: 1, bytes: Buffer.from('stale').toString('base64') });
    runtime.handleMessage({ type: 'takeoverEnd', id: 2, snapshot: Buffer.from('current').toString('base64') });
    await runtime.drain();
    runtime.render();

    expect(runtime.emulator.buffer.active.getLine(0)?.translateToString(true)).toBe('current');
    expect(runtime.emulator.buffer.active.baseY).toBe(0);
    expect(runtime.messages).toEqual([expect.objectContaining({ type: 'takeoverComplete', id: 2 })]);
  });

  it('completes empty takeovers and stops forcing the viewport after presentation', async () => {
    runtime.handleMessage({ type: 'takeover', id: 1 });
    runtime.handleMessage({ type: 'takeoverEnd', id: 1, snapshot: null });
    await runtime.drain();
    runtime.render();
    expect(runtime.messages).toHaveLength(1);

    runtime.handleMessage({ type: 'takeover', id: 2 });
    runtime.handleMessage({ type: 'takeoverWrite', id: 2, bytes: Buffer.from('history\r\n'.repeat(50)).toString('base64') });
    runtime.handleMessage({ type: 'takeoverEnd', id: 2, snapshot: null });
    await runtime.drain();
    runtime.render();
    runtime.scrollToBottom.mockClear();
    runtime.render();

    expect(runtime.scrollToBottom).not.toHaveBeenCalled();
    expect(runtime.messages).toHaveLength(2);
  });

  it('positions snapshot-only updates after parsing finishes', async () => {
    runtime.emulator.write('history\r\n'.repeat(50));
    await runtime.drain();
    runtime.handleMessage({ type: 'loadSnapshot', bytes: Buffer.from('\x1b[2J\x1b[Hlive$ ').toString('base64') });

    expect(runtime.scrollToBottom).not.toHaveBeenCalled();
    await runtime.drain();
    expect(runtime.scrollToBottom).toHaveBeenCalledTimes(1);
    const buffer = runtime.emulator.buffer.active;
    expect(buffer.viewportY).toBe(buffer.baseY);
    expect(buffer.getLine(buffer.viewportY)?.translateToString(true)).toBe('live$ ');
  });
});

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

  it('resets the terminal before every takeover replay', () => {
    const html = buildTerminalHtml({
      theme,
      fontFamily: 'Menlo',
      fontSize: 12,
    });
    const runtime = terminalRuntime(html);
    const takeoverStart = runtime.indexOf("case 'takeover':");
    const takeoverBody = runtime.slice(takeoverStart, runtime.indexOf('break;', takeoverStart));

    expect(takeoverBody).toContain('pendingWrites = [];');
    expect(takeoverBody).toContain('flushScheduled = false;');
    expect(takeoverBody.indexOf('term.reset()')).toBeGreaterThan(-1);
    expect(takeoverBody.indexOf('term.reset()')).toBeLessThan(
      takeoverBody.indexOf('decodeBase64(msg.replay[replayIndex])'),
    );
    expect(takeoverBody).not.toContain('isAltBuffer()');
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
