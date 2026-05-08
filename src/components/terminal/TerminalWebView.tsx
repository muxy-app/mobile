import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import WebView, { type WebViewMessageEvent } from 'react-native-webview';

import { buildTerminalHtml, type TerminalTheme } from './terminalHtml';

const FONT_FAMILY = Platform.select({
  ios: 'Menlo, monospace',
  android: 'monospace',
  default: 'Menlo, Consolas, monospace',
}) as string;

const FONT_SIZE = 12;

export type TerminalWebViewHandle = {
  write: (base64: string) => void;
  loadSnapshot: (base64: string) => void;
  setTheme: (theme: TerminalTheme) => void;
  clear: () => void;
  requestDimensions: () => void;
  installFont: (regular: string, bold: string) => void;
  setFontFamily: (fontFamily: string) => void;
};

export type TerminalDimensions = { cols: number; rows: number };

type Props = {
  theme: TerminalTheme;
  onReady: () => void;
  onDimensions: (dims: TerminalDimensions) => void;
  onData?: (base64: string) => void;
  onError?: (message: string) => void;
  onTap?: () => void;
};

export const TerminalWebView = forwardRef<TerminalWebViewHandle, Props>(function TerminalWebView(
  { theme, onReady, onDimensions, onData, onError, onTap },
  ref,
) {
  const webRef = useRef<WebView>(null);

  const [html] = useState(() =>
    buildTerminalHtml({ theme, fontFamily: FONT_FAMILY, fontSize: FONT_SIZE }),
  );

  const send = (msg: object) => {
    const code = `window.handleMessage && window.handleMessage(${JSON.stringify(msg)}); true;`;
    webRef.current?.injectJavaScript(code);
  };

  useImperativeHandle(
    ref,
    () => ({
      write: (base64) => send({ type: 'write', bytes: base64 }),
      loadSnapshot: (base64) => send({ type: 'loadSnapshot', bytes: base64 }),
      setTheme: (next) => send({ type: 'setTheme', theme: next }),
      clear: () => send({ type: 'clear' }),
      requestDimensions: () => send({ type: 'requestDimensions' }),
      installFont: (regular, bold) => send({ type: 'installFont', regular, bold }),
      setFontFamily: (fontFamily) => send({ type: 'setFontFamily', fontFamily }),
    }),
    [],
  );

  const handleMessage = (event: WebViewMessageEvent) => {
    try {
      const msg = JSON.parse(event.nativeEvent.data);
      switch (msg.type) {
        case 'ready':
          onReady();
          return;
        case 'dimensions':
          onDimensions({ cols: msg.cols, rows: msg.rows });
          return;
        case 'data':
          onData?.(msg.bytes);
          return;
        case 'tap':
          onTap?.();
          return;
        case 'error':
          onError?.(msg.message);
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
