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

  var root = document.getElementById('root');
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
  function isScrolledToBottom() {
    try {
      var buffer = term.buffer && term.buffer.active;
      return !!buffer && buffer.viewportY >= buffer.baseY;
    } catch (err) {
      return true;
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
    var lines = pendingLines;
    if (lines === 0) return;
    pendingLines = 0;
    if (isMouseTrackingActive()) {
      dispatchWheel(lines, pendingClientX, pendingClientY);
      return;
    }
    if (isAltBuffer()) {
      sendArrowKeys(lines);
      return;
    }
    try { term.scrollLines(lines); } catch (e) {}
  }
  function queueLines(lines, clientX, clientY) {
    if (lines === 0) return;
    pendingLines += lines;
    pendingClientX = clientX;
    pendingClientY = clientY;
    if (!flushRaf) flushRaf = requestAnimationFrame(flushPendingLines);
  }
  function queueScrollPixels(dy, clientX, clientY) {
    scrollAccumulator += dy;
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
  function cancelFlush() {
    if (flushRaf) cancelAnimationFrame(flushRaf);
    flushRaf = 0;
    pendingLines = 0;
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

  var lastDims = { cols: 0, rows: 0 };
  function reportDimensions() {
    var shouldStickToBottom = isAltBuffer() || isScrolledToBottom();
    try {
      fit.fit();
    } catch (e) {}
    if (shouldStickToBottom) scrollToBottom();
    if (term.cols !== lastDims.cols || term.rows !== lastDims.rows) {
      lastDims = { cols: term.cols, rows: term.rows };
      post({ type: 'dimensions', cols: term.cols, rows: term.rows });
    }
  }

  window.addEventListener('resize', reportDimensions);

  if (typeof ResizeObserver !== 'undefined') {
    var resizeRaf = 0;
    var ro = new ResizeObserver(function () {
      if (resizeRaf) return;
      resizeRaf = requestAnimationFrame(function () {
        resizeRaf = 0;
        reportDimensions();
      });
    });
    ro.observe(root);
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


  var fontInstalled = false;
  function installFont(regularB64, boldB64) {
    if (fontInstalled) return;
    var css = '';
    if (regularB64) {
      css += "@font-face{font-family:'JetBrainsMonoNF';src:url(data:font/ttf;base64," + regularB64 + ") format('truetype');font-weight:400;font-style:normal;font-display:block;}";
    }
    if (boldB64) {
      css += "@font-face{font-family:'JetBrainsMonoNF';src:url(data:font/ttf;base64," + boldB64 + ") format('truetype');font-weight:700;font-style:normal;font-display:block;}";
    }
    if (!css) return;
    var styleEl = document.createElement('style');
    styleEl.textContent = css;
    document.head.appendChild(styleEl);
    fontInstalled = true;
  }

  window.handleMessage = function (msg) {
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
          if (typeof msg.cols === 'number' && typeof msg.rows === 'number'
              && msg.cols > 0 && msg.rows > 0) {
            try { term.resize(msg.cols, msg.rows); } catch (e) {}
          }
          if (isAltBuffer()) {
            term.reset();
          }
          if (msg.bytes) term.write(decodeBase64(msg.bytes));
          scrollToBottom();
          break;
        case 'setTheme':
          term.options.theme = msg.theme;
          break;
        case 'resize':
          var resizeShouldStickToBottom = isAltBuffer() || isScrolledToBottom();
          term.resize(msg.cols, msg.rows);
          if (resizeShouldStickToBottom) scrollToBottom();
          break;
        case 'clear':
          term.clear();
          term.reset();
          break;
        case 'requestDimensions':
          reportDimensions();
          break;
        case 'installFont':
          installFont(msg.regular, msg.bold);
          break;
        case 'setFontFamily':
          term.options.fontFamily = msg.fontFamily;
          reportDimensions();
          break;
      }
    } catch (e) {
      reportError('handleMessage failed', e);
    }
  };

  setTimeout(function () {
    reportDimensions();
    post({ type: 'ready' });
  }, 0);
})();
</script>
</body>
</html>`;
}
