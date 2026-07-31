import { forwardRef, useCallback, useImperativeHandle, useRef, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import WebView, { type WebViewMessageEvent } from 'react-native-webview';

import { NERD_FONT_FAMILY, type NerdFontBase64 } from '@/lib/nerdFont';

import { buildTerminalHtml, type TerminalTheme } from './terminalHtml';

const FONT_FAMILY = Platform.select({
  ios: 'Menlo, monospace',
  android: 'monospace',
  default: 'Menlo, Consolas, monospace',
}) as string;

const FONT_SIZE = 12;

export type TerminalKeyboardPhase = 'willShow' | 'willHide' | 'didShow' | 'didHide' | 'sync';

export type TerminalWebViewHandle = {
  write: (base64: string) => void;
  loadSnapshot: (base64: string) => void;
  setTheme: (theme: TerminalTheme) => void;
  clear: () => void;
  scrollToBottom: () => void;
  setKeyboardOffset: (offset: number, duration: number, phase: TerminalKeyboardPhase) => void;
};

export type TerminalDimensions = { cols: number; rows: number };
export type TerminalScroll = {
  deltaX: number;
  deltaY: number;
  precise: boolean;
};

type Props = {
  theme: TerminalTheme;
  nerdFont: NerdFontBase64 | null;
  onReady: () => void;
  onDimensions: (dims: TerminalDimensions) => void;
  onData?: (base64: string) => void;
  onScroll?: (scroll: TerminalScroll) => void;
  onFollowingBottomChange?: (followingBottom: boolean) => void;
  onSelectionChange?: (selection: string) => void;
  onError?: (message: string) => void;
  onTap?: () => void;
  onNewTerminalShortcut?: () => void;
  onSelectTabShortcut?: (digit: number) => void;
  onRenderer?: (renderer: string, reason?: string) => void;
};

export const TerminalWebView = forwardRef<TerminalWebViewHandle, Props>(function TerminalWebView(
  {
    theme,
    nerdFont,
    onReady,
    onDimensions,
    onData,
    onScroll,
    onFollowingBottomChange,
    onSelectionChange,
    onError,
    onTap,
    onNewTerminalShortcut,
    onSelectTabShortcut,
    onRenderer,
  },
  ref,
) {
  const webRef = useRef<WebView>(null);
  const queuedWritesRef = useRef<string[]>([]);
  const writeFrameRef = useRef<number | null>(null);
  const fontFamily = nerdFont ? `'${NERD_FONT_FAMILY}', ${FONT_FAMILY}` : FONT_FAMILY;

  const [html] = useState(() =>
    buildTerminalHtml({
      theme,
      fontFamily,
      fontSize: FONT_SIZE,
      commandShortcutsEnabled: Platform.OS !== 'ios',
      forwardTerminalScroll: onScroll !== undefined,
      nativeTextSelectionEnabled: Platform.OS !== 'web',
    }),
  );

  const send = useCallback((msg: object) => {
    const code = `window.handleMessage && window.handleMessage(${JSON.stringify(msg)}); true;`;
    webRef.current?.injectJavaScript(code);
  }, []);

  const cancelQueuedWrites = useCallback(() => {
    if (writeFrameRef.current !== null) {
      cancelAnimationFrame(writeFrameRef.current);
      writeFrameRef.current = null;
    }
    queuedWritesRef.current = [];
  }, []);

  const flushQueuedWrites = useCallback(() => {
    writeFrameRef.current = null;
    const writes = queuedWritesRef.current;
    if (writes.length === 0) return;
    queuedWritesRef.current = [];
    send({ type: 'write', bytes: writes });
  }, [send]);

  const queueWrite = useCallback((base64: string) => {
    queuedWritesRef.current.push(base64);
    if (writeFrameRef.current !== null) return;
    writeFrameRef.current = requestAnimationFrame(flushQueuedWrites);
  }, [flushQueuedWrites]);

  useImperativeHandle(
    ref,
    () => ({
      write: queueWrite,
      loadSnapshot: (base64) => {
        cancelQueuedWrites();
        send({ type: 'loadSnapshot', bytes: base64 });
      },
      setTheme: (next) => send({ type: 'setTheme', theme: next }),
      clear: () => {
        cancelQueuedWrites();
        send({ type: 'clear' });
      },
      scrollToBottom: () => send({ type: 'scrollToBottom' }),
      setKeyboardOffset: (offset, duration, phase) => send({ type: 'setKeyboardOffset', offset, duration, phase }),
    }),
    [cancelQueuedWrites, queueWrite, send],
  );

  const handleMessage = (event: WebViewMessageEvent) => {
    try {
      const msg = JSON.parse(event.nativeEvent.data);
      switch (msg.type) {
        case 'bootstrapReady':
          send({
            type: 'initialize',
            fontFamily,
            font: nerdFont
              ? { family: NERD_FONT_FAMILY, regular: nerdFont.regular, bold: nerdFont.bold }
              : undefined,
          });
          return;
        case 'ready':
          onReady();
          return;
        case 'dimensions':
          onDimensions({ cols: msg.cols, rows: msg.rows });
          return;
        case 'data':
          onData?.(msg.bytes);
          return;
        case 'scroll':
          onScroll?.({
            deltaX: msg.deltaX,
            deltaY: msg.deltaY,
            precise: msg.precise,
          });
          return;
        case 'followingBottom':
          onFollowingBottomChange?.(msg.value);
          return;
        case 'selectionChange':
          onSelectionChange?.(typeof msg.text === 'string' ? msg.text : '');
          return;
        case 'tap':
          onTap?.();
          return;
        case 'newTerminalShortcut':
          onNewTerminalShortcut?.();
          return;
        case 'selectTabShortcut':
          onSelectTabShortcut?.(msg.digit);
          return;
        case 'error':
          onError?.(msg.message);
          return;
        case 'info':
          if (msg.renderer) onRenderer?.(msg.renderer, msg.reason);
          return;
      }
    } catch {
      void 0;
    }
  };

  return (
    <View style={[styles.host, { backgroundColor: theme.background }]}>
      <WebView
        ref={webRef}
        originWhitelist={['*']}
        source={{ html }}
        onMessage={handleMessage}
        javaScriptEnabled
        domStorageEnabled={false}
        scrollEnabled={false}
        bounces={false}
        overScrollMode="never"
        scalesPageToFit={false}
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        hideKeyboardAccessoryView
        keyboardDisplayRequiresUserAction={false}
        automaticallyAdjustContentInsets={false}
        contentInsetAdjustmentBehavior="never"
        androidLayerType="hardware"
        style={[styles.web, { backgroundColor: theme.background }]}
        containerStyle={styles.web}
      />
    </View>
  );
});

const styles = StyleSheet.create({
  host: { flex: 1 },
  web: { flex: 1, backgroundColor: 'transparent' },
});
