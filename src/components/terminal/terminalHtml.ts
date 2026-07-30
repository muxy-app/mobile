import {
  ADDON_CANVAS_JS,
  ADDON_FIT_JS,
  ADDON_WEBGL_JS,
  XTERM_CSS,
  XTERM_JS,
} from './xtermBundle';

export type TerminalTheme = {
  background: string;
  foreground: string;
  cursor: string;
  cursorAccent: string;
  selectionBackground: string;
  black: string;
  red: string;
  green: string;
  yellow: string;
  blue: string;
  magenta: string;
  cyan: string;
  white: string;
  brightBlack: string;
  brightRed: string;
  brightGreen: string;
  brightYellow: string;
  brightBlue: string;
  brightMagenta: string;
  brightCyan: string;
  brightWhite: string;
};

export type TerminalInitOptions = {
  theme: TerminalTheme;
  fontFamily: string;
  fontSize: number;
  commandShortcutsEnabled?: boolean;
  forwardTerminalScroll?: boolean;
};

export function buildTerminalHtml(init: TerminalInitOptions): string {
  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
<style>
${XTERM_CSS}
html, body { margin: 0; padding: 0; height: 100%; width: 100%; background: ${init.theme.background}; overflow: hidden; -webkit-text-size-adjust: 100%; }
#root { position: absolute; inset: 0; padding: 8px; box-sizing: border-box; }
.xterm, .xterm-screen { user-select: text; -webkit-user-select: text; -webkit-touch-callout: default; }
.xterm-viewport { background-color: transparent !important; }
.xterm-screen canvas { pointer-events: none !important; }
.xterm .xterm-scrollable-element > .scrollbar.vertical { width: 4px !important; }
.xterm .xterm-scrollable-element > .scrollbar.vertical > .slider { width: 4px !important; left: 0 !important; border-radius: 2px; }
.xterm, .xterm-rows {
  -webkit-font-smoothing: antialiased;
  text-rendering: geometricPrecision;
  font-feature-settings: "liga" 0, "calt" 0;
  font-variant-ligatures: none;
}
</style>
</head>
<body>
<div id="root"></div>
<script>${XTERM_JS}</script>
<script>${ADDON_FIT_JS}</script>
<script>${ADDON_WEBGL_JS}</script>
<script>${ADDON_CANVAS_JS}</script>
<script>
(function () {
  var Terminal = window.Terminal;
  var FitAddon = window.FitAddon && window.FitAddon.FitAddon;
  var WebglAddon = window.WebglAddon && window.WebglAddon.WebglAddon;
  var CanvasAddon = window.CanvasAddon && window.CanvasAddon.CanvasAddon;
  var INITIAL = ${JSON.stringify(init)};
  var terminalHandleMessage = null;
  var initializationStarted = false;
  var root = document.getElementById('root');
  var viewportOffset = 0;
  var viewportOffsetLimit = 0;
  var pendingHideViewportOffset = null;
  var pendingHideViewportLimit = 0;
  var cancelTerminalMomentum = null;
  var getTerminalCursorBounds = null;
  var viewportOffsetAwaitingCursor = false;
  var viewportOffsetFollowsCursor = false;

  function post(msg) {
    if (window.ReactNativeWebView) {
      window.ReactNativeWebView.postMessage(JSON.stringify(msg));
    }
  }

  function reportError(message, err) {
    post({ type: 'error', message: message + (err ? ': ' + (err && err.message ? err.message : String(err)) : '') });
  }

  if (!Terminal || !FitAddon) {
    reportError('xterm not loaded');
    return;
  }

  function installInitialFont(font) {
    if (!font) {
      return Promise.resolve();
    }
    var family = JSON.stringify(font.family);
    var styleEl = document.createElement('style');
    styleEl.textContent = '@font-face{font-family:' + family + ';src:url("data:font/ttf;base64,' + font.regular + '") format("truetype");font-weight:400;font-style:normal;font-display:block;}'
      + '@font-face{font-family:' + family + ';src:url("data:font/ttf;base64,' + font.bold + '") format("truetype");font-weight:700;font-style:normal;font-display:block;}';
    document.head.appendChild(styleEl);
    if (!document.fonts || !document.fonts.load) {
      return Promise.resolve();
    }
    return Promise.all([
      document.fonts.load('400 ' + INITIAL.fontSize + 'px ' + family),
      document.fonts.load('700 ' + INITIAL.fontSize + 'px ' + family),
    ]);
  }

  function initializeTerminal(msg) {
    if (initializationStarted) return;
    initializationStarted = true;
    INITIAL.fontFamily = msg.fontFamily;
    installInitialFont(msg.font).then(startTerminal, startTerminal);
  }

  function applyViewportOffset(duration) {
    var transitionDuration = Math.max(0, Number(duration) || 0);
    root.style.transition = transitionDuration > 0
      ? 'transform ' + transitionDuration + 'ms cubic-bezier(0.25, 0.1, 0.25, 1)'
      : 'none';
    root.style.transform = 'translate3d(0, ' + (-viewportOffset) + 'px, 0)';
  }

  function cursorViewportOffset(cursorBounds, viewportHeight, nextLimit) {
    if (cursorBounds === null) return 0;
    if (cursorBounds.bottom <= viewportHeight - nextLimit) return 0;
    if (cursorBounds.top - nextLimit < 0) return 0;
    return nextLimit;
  }

  function chooseInitialViewportOffset(nextLimit) {
    if (!getTerminalCursorBounds) {
      viewportOffsetAwaitingCursor = true;
      return 0;
    }
    var cursorBounds = getTerminalCursorBounds();
    if (cursorBounds === undefined) {
      viewportOffsetAwaitingCursor = true;
      return 0;
    }
    viewportOffsetAwaitingCursor = false;
    if (cursorBounds === null) return 0;
    return cursorViewportOffset(cursorBounds, root.clientHeight, nextLimit);
  }

  function reconcileVisibleViewport(nextLimit) {
    if (pendingHideViewportOffset !== null) {
      var wasPendingAboveKeyboard = pendingHideViewportLimit > 0
        && Math.abs(pendingHideViewportOffset - pendingHideViewportLimit) <= 0.5;
      viewportOffsetLimit = nextLimit;
      if (viewportOffsetFollowsCursor) {
        viewportOffset = chooseInitialViewportOffset(viewportOffsetLimit);
      } else {
        viewportOffset = wasPendingAboveKeyboard
          ? viewportOffsetLimit
          : Math.min(pendingHideViewportOffset, viewportOffsetLimit);
      }
      pendingHideViewportOffset = null;
      pendingHideViewportLimit = 0;
      return;
    }
    var previousLimit = viewportOffsetLimit;
    var wasAnchoredAboveKeyboard = previousLimit > 0 && Math.abs(viewportOffset - previousLimit) <= 0.5;
    viewportOffsetLimit = nextLimit;
    if (previousLimit === 0) {
      viewportOffsetFollowsCursor = true;
      viewportOffset = chooseInitialViewportOffset(viewportOffsetLimit);
      return;
    }
    if (viewportOffsetFollowsCursor) {
      viewportOffset = chooseInitialViewportOffset(viewportOffsetLimit);
      return;
    }
    if (wasAnchoredAboveKeyboard) {
      viewportOffset = viewportOffsetLimit;
      return;
    }
    viewportOffset = Math.min(viewportOffset, viewportOffsetLimit);
  }

  function setKeyboardOffset(offset, duration, phase) {
    var nextLimit = Math.max(0, Number(offset) || 0);
    var transitionDuration = Math.max(0, Number(duration) || 0);
    var limitChanged = Math.abs(nextLimit - viewportOffsetLimit) > 0.5;
    if (limitChanged && cancelTerminalMomentum) cancelTerminalMomentum();
    if (phase === 'willHide') {
      if (viewportOffsetLimit > 0) {
        pendingHideViewportOffset = viewportOffset;
        pendingHideViewportLimit = viewportOffsetLimit;
      }
      viewportOffsetLimit = 0;
      viewportOffset = 0;
      applyViewportOffset(transitionDuration);
      return;
    }
    if (phase === 'didHide' || nextLimit === 0) {
      viewportOffsetLimit = 0;
      viewportOffset = 0;
      pendingHideViewportOffset = null;
      pendingHideViewportLimit = 0;
      viewportOffsetAwaitingCursor = false;
      viewportOffsetFollowsCursor = false;
      applyViewportOffset(transitionDuration);
      return;
    }
    reconcileVisibleViewport(nextLimit);
    applyViewportOffset(transitionDuration);
  }

  function captureRenderedViewportOffset() {
    var transform = window.getComputedStyle(root).transform;
    var values;
    var translateY;
    if (transform && transform.indexOf('matrix3d(') === 0) {
      values = transform.slice(9, -1).split(',');
      translateY = Number(values[13]);
    } else if (transform && transform.indexOf('matrix(') === 0) {
      values = transform.slice(7, -1).split(',');
      translateY = Number(values[5]);
    }
    if (isFinite(translateY)) {
      viewportOffset = Math.min(viewportOffsetLimit, Math.max(0, -translateY));
    }
    applyViewportOffset(0);
  }

  function consumeViewportOffset(delta) {
    if (viewportOffsetLimit <= 0 || delta === 0) return delta;
    var nextOffset = Math.min(viewportOffsetLimit, Math.max(0, viewportOffset + delta));
    var consumed = nextOffset - viewportOffset;
    if (consumed === 0) return delta;
    viewportOffset = nextOffset;
    applyViewportOffset(0);
    return delta - consumed;
  }

  window.handleMessage = function (msg) {
    try {
      if (msg.type === 'initialize') {
        initializeTerminal(msg);
        return;
      }
      if (msg.type === 'setKeyboardOffset') {
        setKeyboardOffset(msg.offset, msg.duration, msg.phase);
        return;
      }
      if (terminalHandleMessage) terminalHandleMessage(msg);
    } catch (e) {
      reportError('handleMessage failed', e);
    }
  };

  function startTerminal() {
  var term = new Terminal({
    cursorBlink: true,
    convertEol: false,
    scrollback: 5000,
    allowProposedApi: true,
    theme: INITIAL.theme,
    fontFamily: INITIAL.fontFamily,
    fontSize: INITIAL.fontSize,
    customGlyphs: true,
    letterSpacing: 0,
    lineHeight: 1.0,
    macOptionIsMeta: true,
  });

  var fit = new FitAddon();
  term.loadAddon(fit);

  term.open(root);

  if (INITIAL.commandShortcutsEnabled !== false) {
    window.addEventListener('keydown', function (e) {
      if (!e.metaKey || e.ctrlKey || e.altKey || e.shiftKey) return;
      var key = String(e.key || '').toLowerCase();
      if (key === 't') {
        e.preventDefault();
        e.stopPropagation();
        post({ type: 'newTerminalShortcut' });
        return;
      }
      if (/^[1-9]$/.test(key)) {
        e.preventDefault();
        e.stopPropagation();
        post({ type: 'selectTabShortcut', digit: Number(key) });
        return;
      }
    }, true);
  }

  var activeRenderer = 'dom';
  var canvasAddon = null;
  function loadCanvas() {
    if (!CanvasAddon) return false;
    try {
      canvasAddon = new CanvasAddon();
      term.loadAddon(canvasAddon);
      activeRenderer = 'canvas';
      return true;
    } catch (err) {
      canvasAddon = null;
      reportError('canvas renderer init failed', err);
      return false;
    }
  }
  function disposeCanvas() {
    if (!canvasAddon) return;
    try { canvasAddon.dispose(); } catch (_) {}
    canvasAddon = null;
  }
  function loadWebgl() {
    if (!WebglAddon) return false;
    try {
      var webgl = new WebglAddon();
      webgl.onContextLoss(function () {
        try { webgl.dispose(); } catch (_) {}
        if (loadCanvas()) {
          post({ type: 'info', renderer: activeRenderer, reason: 'webgl-context-loss' });
        } else {
          activeRenderer = 'dom';
          post({ type: 'info', renderer: activeRenderer, reason: 'webgl-context-loss' });
        }
      });
      term.loadAddon(webgl);
      activeRenderer = 'webgl';
      return true;
    } catch (err) {
      reportError('webgl renderer init failed', err);
      return false;
    }
  }
  if (!loadWebgl()) loadCanvas();
  post({ type: 'info', renderer: activeRenderer });

  function encodeUtf8ToBase64(str) {
    var utf8 = unescape(encodeURIComponent(str));
    return btoa(utf8);
  }
  term.onData(function (data) {
    post({ type: 'data', bytes: encodeUtf8ToBase64(data) });
  });
  term.onBinary(function (data) {
    post({ type: 'data', bytes: btoa(data) });
  });

  var helperTa = document.querySelector('.xterm-helper-textarea');
  if (helperTa) {
    helperTa.setAttribute('readonly', 'readonly');
    helperTa.setAttribute('aria-hidden', 'true');
    helperTa.setAttribute('tabindex', '-1');
    helperTa.style.pointerEvents = 'none';
    helperTa.addEventListener('focus', function () {
      try { helperTa.blur(); } catch (_) {}
    }, true);
  }

  var touchStartX = 0;
  var touchStartY = 0;
  var lastTouchX = 0;
  var lastTouchY = 0;
  var touchMoved = false;
  var hadSelectionAtStart = false;
  var velocitySamples = [];
  var momentumRaf = 0;
  var scrollAccumulator = 0;
  var pendingLines = 0;
  var pendingRemoteDeltaY = 0;
  var pendingClientX = 0;
  var pendingClientY = 0;
  var flushRaf = 0;

  function getLineHeightPx() {
    var fontSize = term.options.fontSize || INITIAL.fontSize;
    return (term.options.lineHeight || 1) * fontSize;
  }
  function isAltBuffer() {
    try {
      return term.buffer && term.buffer.active && term.buffer.active.type === 'alternate';
    } catch (err) {
      return false;
    }
  }
  function scrollToBottom() {
    try { term.scrollToBottom(); } catch (e) {}
  }
  function sendArrowKeys(lines) {
    if (lines === 0) return;
    var seq = lines > 0 ? '\\x1b[B' : '\\x1b[A';
    var count = Math.abs(lines);
    var out = '';
    for (var i = 0; i < count; i++) out += seq;
    post({ type: 'data', bytes: btoa(out) });
  }
  function flushPendingLines() {
    flushRaf = 0;
    var remoteDeltaY = pendingRemoteDeltaY;
    pendingRemoteDeltaY = 0;
    if (remoteDeltaY !== 0) {
      post({ type: 'scroll', deltaX: 0, deltaY: remoteDeltaY, precise: true });
    }
    var lines = pendingLines;
    if (lines === 0) return;
    pendingLines = 0;
    if (isMouseTrackingActive()) {
      if (INITIAL.forwardTerminalScroll) return;
      dispatchWheel(lines, pendingClientX, pendingClientY);
      return;
    }
    if (isAltBuffer()) {
      if (INITIAL.forwardTerminalScroll) return;
      sendArrowKeys(lines);
      return;
    }
    try { term.scrollLines(lines); } catch (e) {}
  }
  function scheduleScrollFlush() {
    if (!flushRaf) flushRaf = requestAnimationFrame(flushPendingLines);
  }
  function queueLines(lines, clientX, clientY) {
    if (lines === 0) return;
    pendingLines += lines;
    pendingClientX = clientX;
    pendingClientY = clientY;
    scheduleScrollFlush();
  }
  function queueScrollPixels(dy, clientX, clientY) {
    var scrollDelta = consumeViewportOffset(dy);
    if (scrollDelta === 0) return;
    if (INITIAL.forwardTerminalScroll) {
      pendingRemoteDeltaY -= scrollDelta;
      scheduleScrollFlush();
    }
    scrollAccumulator += scrollDelta;
    var lineHeight = getLineHeightPx();
    if (lineHeight <= 0) return;
    var lines = (scrollAccumulator / lineHeight) | 0;
    if (lines === 0) return;
    scrollAccumulator -= lines * lineHeight;
    queueLines(lines, clientX, clientY);
  }
  function hasSelection() {
    var sel = window.getSelection && window.getSelection();
    return !!(sel && sel.toString().length > 0);
  }
  function cancelMomentum() {
    if (momentumRaf) cancelAnimationFrame(momentumRaf);
    momentumRaf = 0;
  }
  cancelTerminalMomentum = function () {
    cancelMomentum();
    scrollAccumulator = 0;
  };
  function cancelFlush() {
    if (flushRaf) cancelAnimationFrame(flushRaf);
    flushRaf = 0;
    pendingLines = 0;
    pendingRemoteDeltaY = 0;
  }
  function dispatchWheel(deltaLines, clientX, clientY) {
    var target = term.element;
    if (!target) return;
    var ev;
    try {
      ev = new WheelEvent('wheel', {
        deltaMode: 1,
        deltaY: deltaLines,
        clientX: clientX,
        clientY: clientY,
        bubbles: true,
        cancelable: true,
      });
    } catch (err) {
      ev = new Event('wheel', { bubbles: true, cancelable: true });
      ev.deltaY = deltaLines;
      ev.deltaMode = 1;
      ev.clientX = clientX;
      ev.clientY = clientY;
    }
    target.dispatchEvent(ev);
  }

  function isMouseTrackingActive() {
    try {
      var mode = term.modes && term.modes.mouseTrackingMode;
      return !!mode && mode !== 'none';
    } catch (err) {
      return false;
    }
  }

  function computeVelocity() {
    if (velocitySamples.length < 2) return 0;
    var endSample = velocitySamples[velocitySamples.length - 1];
    var startSample = velocitySamples[0];
    var cutoff = endSample.t - 80;
    for (var i = velocitySamples.length - 1; i >= 0; i--) {
      if (velocitySamples[i].t <= cutoff) {
        startSample = velocitySamples[i];
        break;
      }
      startSample = velocitySamples[i];
    }
    var dt = endSample.t - startSample.t;
    if (dt <= 0) return 0;
    return (startSample.y - endSample.y) / dt;
  }

  function startMomentum(initialVelocity, clientX, clientY) {
    cancelMomentum();
    var velocity = initialVelocity;
    var lastTime = performance.now();
    var step = function () {
      var now = performance.now();
      var dt = Math.min(now - lastTime, 33);
      lastTime = now;
      queueScrollPixels(velocity * dt, clientX, clientY);
      velocity *= Math.pow(0.96, dt / 16);
      if (Math.abs(velocity) > 0.03) {
        momentumRaf = requestAnimationFrame(step);
      } else {
        momentumRaf = 0;
      }
    };
    momentumRaf = requestAnimationFrame(step);
  }

  root.addEventListener('touchstart', function (e) {
    e.stopPropagation();
    cancelMomentum();
    cancelFlush();
    scrollAccumulator = 0;
    viewportOffsetAwaitingCursor = false;
    viewportOffsetFollowsCursor = false;
    captureRenderedViewportOffset();
    touchMoved = false;
    if (e.touches && e.touches[0]) {
      touchStartX = e.touches[0].clientX;
      touchStartY = e.touches[0].clientY;
      lastTouchX = touchStartX;
      lastTouchY = touchStartY;
      velocitySamples = [{ t: performance.now(), y: touchStartY }];
    }
    hadSelectionAtStart = hasSelection();
  }, { passive: true, capture: true });

  root.addEventListener('touchmove', function (e) {
    e.stopPropagation();
    if (!e.touches || !e.touches[0]) return;
    var tx = e.touches[0].clientX;
    var ty = e.touches[0].clientY;
    var totalDx = Math.abs(tx - touchStartX);
    var totalDy = Math.abs(ty - touchStartY);
    if (totalDx > 8 || totalDy > 8) touchMoved = true;

    if (totalDy > totalDx) {
      var dy = lastTouchY - ty;
      if (dy !== 0) queueScrollPixels(dy, tx, ty);
      lastTouchX = tx;
      lastTouchY = ty;
      velocitySamples.push({ t: performance.now(), y: ty });
      while (velocitySamples.length > 6) velocitySamples.shift();
    }
  }, { passive: true, capture: true });

  root.addEventListener('touchend', function (e) {
    e.stopPropagation();
    if (touchMoved) {
      var v = computeVelocity();
      if (Math.abs(v) > 0.1) startMomentum(v, lastTouchX, lastTouchY);
      return;
    }
    if (hadSelectionAtStart || hasSelection()) return;
    post({ type: 'tap' });
  }, { passive: true, capture: true });

  var dimensionsInitialized = false;
  getTerminalCursorBounds = function () {
    if (!dimensionsInitialized) return undefined;
    try {
      var buffer = term.buffer && term.buffer.active;
      var screen = term.element && term.element.querySelector('.xterm-screen');
      if (!buffer || !screen || term.rows <= 0) return undefined;
      var cursorRow = buffer.baseY + buffer.cursorY - buffer.viewportY;
      if (cursorRow < 0 || cursorRow >= term.rows) return null;
      var rootRect = root.getBoundingClientRect();
      var screenRect = screen.getBoundingClientRect();
      var cellHeight = screenRect.height / term.rows;
      if (!isFinite(cellHeight) || cellHeight <= 0) return undefined;
      var top = screenRect.top - rootRect.top + cursorRow * cellHeight;
      return { top: top, bottom: top + cellHeight };
    } catch (e) {
      return undefined;
    }
  };
  function initializeDimensions() {
    if (dimensionsInitialized) return;
    if (root.clientWidth <= 0 || root.clientHeight <= 0) {
      requestAnimationFrame(initializeDimensions);
      return;
    }
    var proposed = fit.proposeDimensions();
    if (!proposed || !isFinite(proposed.cols) || !isFinite(proposed.rows)) {
      requestAnimationFrame(initializeDimensions);
      return;
    }
    try {
      fit.fit();
    } catch (e) {
      requestAnimationFrame(initializeDimensions);
      return;
    }
    dimensionsInitialized = true;
    if (viewportOffsetAwaitingCursor && viewportOffsetLimit > 0) {
      viewportOffset = chooseInitialViewportOffset(viewportOffsetLimit);
      applyViewportOffset(0);
    }
    post({ type: 'dimensions', cols: term.cols, rows: term.rows });
    post({ type: 'ready' });
  }

  var pendingWrites = [];
  var flushScheduled = false;
  function scheduleFlush() {
    if (flushScheduled) return;
    flushScheduled = true;
    requestAnimationFrame(function () {
      flushScheduled = false;
      if (pendingWrites.length === 0) return;
      var combined;
      if (pendingWrites.length === 1) {
        combined = pendingWrites[0];
      } else {
        var total = 0;
        for (var i = 0; i < pendingWrites.length; i++) total += pendingWrites[i].length;
        combined = new Uint8Array(total);
        var off = 0;
        for (var j = 0; j < pendingWrites.length; j++) {
          combined.set(pendingWrites[j], off);
          off += pendingWrites[j].length;
        }
      }
      pendingWrites = [];
      term.write(combined);
    });
  }

  function decodeBase64(b64) {
    var bin = atob(b64);
    var arr = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
    return arr;
  }

  terminalHandleMessage = function (msg) {
    try {
      switch (msg.type) {
        case 'write':
          if (Array.isArray(msg.bytes)) {
            for (var writeIndex = 0; writeIndex < msg.bytes.length; writeIndex++) {
              pendingWrites.push(decodeBase64(msg.bytes[writeIndex]));
            }
          } else {
            pendingWrites.push(decodeBase64(msg.bytes));
          }
          scheduleFlush();
          break;
        case 'loadSnapshot':
          pendingWrites = [];
          flushScheduled = false;
          if (isAltBuffer()) {
            term.reset();
          }
          if (msg.bytes) term.write(decodeBase64(msg.bytes));
          scrollToBottom();
          break;
        case 'setTheme':
          term.options.theme = msg.theme;
          break;
        case 'clear':
          term.clear();
          term.reset();
          break;
      }
    } catch (e) {
      reportError('handleMessage failed', e);
    }
  };

  requestAnimationFrame(initializeDimensions);
  }

  post({ type: 'bootstrapReady' });
})();
</script>
</body>
</html>`;
}
