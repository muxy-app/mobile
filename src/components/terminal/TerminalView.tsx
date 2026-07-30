import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Keyboard, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import {
  KeyboardController,
  KeyboardEvents,
  useReanimatedKeyboardAnimation,
} from 'react-native-keyboard-controller';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { bytesToBase64, stringToBase64 } from '@/lib/base64';
import { getNerdFont, loadNerdFont, type NerdFontBase64 } from '@/lib/nerdFont';
import {
  recordDimensions,
  reclaimPane,
  sendTerminalInput,
  sendTerminalScroll,
  useDevicesStore,
  usePaneSession,
  usePaneSessionStore,
  useSettingsStore,
} from '@/state';
import { useTokens } from '@/theme';

import { buildTerminalTheme } from './buildTerminalTheme';
import { KEY_BAR_HEIGHT, KeyBar, transformWithModifiers } from './KeyBar';
import {
  type TerminalKeyboardPhase,
  TerminalWebView,
  type TerminalDimensions,
  type TerminalScroll,
  type TerminalWebViewHandle,
} from './TerminalWebView';
import { scheduleTerminalInputFocus } from './terminalFocus';
import { buildTerminalInputDiff } from './terminalInput';

type Props = {
  paneId: string;
  onNewTerminal?: () => void;
  onSelectTabShortcut?: (digit: number) => void;
};

const INPUT_SENTINEL = '​';

export function TerminalView(props: Props) {
  const tokens = useTokens();
  const useNerdFont = useSettingsStore((s) => s.useNerdFont);
  const [nerdFont, setNerdFont] = useState<NerdFontBase64 | null>(getNerdFont);
  const [nerdFontUnavailable, setNerdFontUnavailable] = useState(false);

  useEffect(() => {
    if (nerdFont) return;
    let active = true;
    loadNerdFont()
      .then((font) => {
        if (active) setNerdFont(font);
      })
      .catch(() => {
        if (active) setNerdFontUnavailable(true);
      });
    return () => {
      active = false;
    };
  }, [nerdFont]);

  if (useNerdFont && !nerdFont && !nerdFontUnavailable) {
    return <View style={[styles.root, { backgroundColor: tokens.surface.primary }]} />;
  }

  const selectedNerdFont = useNerdFont ? nerdFont : null;

  return (
    <TerminalSessionView
      key={selectedNerdFont ? 'nerd' : 'system'}
      {...props}
      nerdFont={selectedNerdFont}
    />
  );
}

function TerminalSessionView({
  paneId,
  onNewTerminal,
  onSelectTabShortcut,
  nerdFont,
}: Props & { nerdFont: NerdFontBase64 | null }) {
  const tokens = useTokens();
  const webRef = useRef<TerminalWebViewHandle>(null);
  const inputRef = useRef<TextInput>(null);
  const lastSentRef = useRef('');
  const [inputValue, setInputValue] = useState(INPUT_SENTINEL);

  const lastTheme = useDevicesStore((s) => s.lastAppliedTheme);
  const activePairing = useDevicesStore((s) => {
    const id = s.activeDeviceId;
    if (!id) return null;
    return s.devices.find((d) => d.id === id)?.pairing ?? null;
  });
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);
  const session = usePaneSessionStore((s) => s.session);

  const deviceTheme = useMemo(() => {
    if (activePairing) {
      return {
        themeFg: activePairing.themeFg,
        themeBg: activePairing.themeBg,
        themePalette: activePairing.themePalette,
      };
    }
    return lastTheme;
  }, [activePairing, lastTheme]);

  const terminalTheme = useMemo(() => buildTerminalTheme(deviceTheme, tokens), [deviceTheme, tokens]);

  const [dimensions, setDimensions] = useState<TerminalDimensions | null>(null);
  const [ready, setReady] = useState(false);
  const autoFocusTerminal = useSettingsStore((s) => s.autoFocusTerminal);

  usePaneSession({
    paneId,
    cols: dimensions?.cols ?? null,
    rows: dimensions?.rows ?? null,
    onSnapshotBytes: (base64) => webRef.current?.loadSnapshot(base64),
    onWrite: (base64) => webRef.current?.write(base64),
  });

  useEffect(() => {
    if (ready) webRef.current?.setTheme(terminalTheme);
  }, [terminalTheme, ready]);

  const sessionForUs =
    'paneId' in session && session.paneId === paneId ? session : null;
  const ownershipLost = sessionForUs?.kind === 'lost';
  const failed = sessionForUs?.kind === 'failed';
  const reconnecting = connectionPhase === 'reconnecting' || connectionPhase === 'connecting';

  useEffect(() => {
    if (!autoFocusTerminal) return;
    return scheduleTerminalInputFocus(inputRef.current);
  }, [paneId, autoFocusTerminal]);

  const onResume = () => {
    if (!dimensions) return;
    reclaimPane(paneId, dimensions.cols, dimensions.rows);
  };

  const handleData = (base64: string) => {
    sendTerminalInput(paneId, transformWithModifiers(base64));
  };

  const handleScroll = useCallback(
    ({ deltaX, deltaY, precise }: TerminalScroll) => {
      sendTerminalScroll(paneId, deltaX, deltaY, precise);
    },
    [paneId],
  );

  const handleKeyBarBytes = (base64: string) => {
    sendTerminalInput(paneId, base64);
  };

  const sendInputDiff = useCallback(
    (next: string) => {
      const prev = lastSentRef.current;
      const out = buildTerminalInputDiff(prev, next);
      lastSentRef.current = next;
      if (out) sendTerminalInput(paneId, transformWithModifiers(stringToBase64(out)));
    },
    [paneId],
  );

  const handleInputChange = useCallback(
    (text: string) => {
      const sentinelIdx = text.lastIndexOf(INPUT_SENTINEL);
      if (sentinelIdx === -1) {
        sendTerminalInput(paneId, bytesToBase64(new Uint8Array([0x7f])));
        lastSentRef.current = '';
        setInputValue(INPUT_SENTINEL);
        return;
      }
      const body = text.slice(sentinelIdx + INPUT_SENTINEL.length);
      const newlineIdx = body.indexOf('\n');
      if (newlineIdx === -1) {
        setInputValue(INPUT_SENTINEL + body);
        sendInputDiff(body);
        return;
      }
      const before = body.slice(0, newlineIdx);
      sendInputDiff(before);
      sendTerminalInput(paneId, stringToBase64('\r'));
      lastSentRef.current = '';
      setInputValue(INPUT_SENTINEL);
    },
    [paneId, sendInputDiff],
  );

  const handleInputBlur = useCallback(() => {
    lastSentRef.current = '';
    setInputValue(INPUT_SENTINEL);
  }, []);

  const insets = useSafeAreaInsets();
  const { height } = useReanimatedKeyboardAnimation();
  const safeAreaStyle = useMemo(() => ({ paddingBottom: insets.bottom }), [insets.bottom]);
  const keyBarSlideStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: Math.min(0, height.value + insets.bottom) }],
  }));
  const setTerminalKeyboardOffset = useCallback(
    (keyboardHeight: number, duration: number, phase: TerminalKeyboardPhase) => {
      const offset = Math.round(Math.max(0, keyboardHeight - insets.bottom));
      webRef.current?.setKeyboardOffset(offset, duration, phase);
    },
    [insets.bottom],
  );

  const keyboardVisibleRef = useRef(false);
  const keyboardTransitioningRef = useRef(false);
  useEffect(() => {
    const willShowSub = KeyboardEvents.addListener('keyboardWillShow', (event) => {
      keyboardTransitioningRef.current = true;
      setTerminalKeyboardOffset(event.height, event.duration, 'willShow');
    });
    const willHideSub = KeyboardEvents.addListener('keyboardWillHide', (event) => {
      keyboardTransitioningRef.current = true;
      setTerminalKeyboardOffset(0, event.duration, 'willHide');
    });
    const didShowSub = KeyboardEvents.addListener('keyboardDidShow', (event) => {
      keyboardVisibleRef.current = true;
      keyboardTransitioningRef.current = false;
      setTerminalKeyboardOffset(event.height, 0, 'didShow');
    });
    const didHideSub = KeyboardEvents.addListener('keyboardDidHide', () => {
      keyboardVisibleRef.current = false;
      keyboardTransitioningRef.current = false;
      setTerminalKeyboardOffset(0, 0, 'didHide');
    });
    return () => {
      willShowSub.remove();
      willHideSub.remove();
      didShowSub.remove();
      didHideSub.remove();
    };
  }, [setTerminalKeyboardOffset]);

  useEffect(() => {
    if (!ready) return;
    if (keyboardTransitioningRef.current) return;
    const visible = KeyboardController.isVisible();
    const state = KeyboardController.state();
    keyboardVisibleRef.current = visible;
    setTerminalKeyboardOffset(visible ? state.height : 0, 0, 'sync');
  }, [ready, setTerminalKeyboardOffset]);

  const handleTap = useCallback(() => {
    if (keyboardVisibleRef.current) {
      Keyboard.dismiss();
      inputRef.current?.blur();
      return;
    }
    inputRef.current?.focus();
  }, []);

  const inputSelection = useMemo(
    () => ({ start: inputValue.length, end: inputValue.length }),
    [inputValue],
  );

  const handleReady = useCallback(() => {
    setReady(true);
  }, []);

  return (
    <View style={[styles.root, { backgroundColor: terminalTheme.background }]}>
      <View style={[styles.slider, safeAreaStyle]}>
        <View style={styles.terminalArea}>
          <TerminalWebView
            ref={webRef}
            theme={terminalTheme}
            nerdFont={nerdFont}
            onReady={handleReady}
            onDimensions={(d) => {
              setDimensions(d);
              recordDimensions(d.cols, d.rows);
            }}
            onData={handleData}
            onScroll={handleScroll}
            onTap={handleTap}
            onNewTerminalShortcut={onNewTerminal}
            onSelectTabShortcut={onSelectTabShortcut}
            onRenderer={(renderer, reason) => {
              if (reason) {
                console.log('[terminal] renderer=' + renderer + ' reason=' + reason);
                return;
              }
              console.log('[terminal] renderer=' + renderer);
            }}
          />

          <TextInput
            ref={inputRef}
            value={inputValue}
            selection={inputSelection}
            onChangeText={handleInputChange}
            onBlur={handleInputBlur}
            multiline
            autoCorrect={false}
            autoCapitalize="none"
            autoComplete="off"
            spellCheck={false}
            caretHidden
            style={styles.hiddenInput}
          />

          {reconnecting ? (
            <View
              style={[
                styles.banner,
                { backgroundColor: tokens.surface.tertiary, borderColor: tokens.border.subtle },
              ]}>
              <ActivityIndicator size="small" color={tokens.text.muted} />
              <Text style={[styles.bannerLabel, { color: tokens.text.secondary }]}>Reconnecting…</Text>
            </View>
          ) : null}

          {ownershipLost ? (
            <View style={[styles.fullOverlay, { backgroundColor: tokens.surface.primary }]}>
              <Text style={[styles.title, { color: tokens.text.primary }]}>Desktop took control</Text>
              <Text style={[styles.body, { color: tokens.text.muted }]}>
                {sessionForUs?.kind === 'lost' && sessionForUs.takenBy
                  ? `${sessionForUs.takenBy} is using this terminal.`
                  : 'Another client is controlling this pane.'}
              </Text>
              <Pressable
                onPress={onResume}
                style={({ pressed }) => [
                  styles.cta,
                  { backgroundColor: tokens.accent.primary, opacity: pressed ? 0.85 : 1 },
                ]}>
                <Text style={[styles.ctaLabel, { color: tokens.accent.contrast }]}>Take Over</Text>
              </Pressable>
            </View>
          ) : null}

          {failed ? (
            <View style={[styles.fullOverlay, { backgroundColor: tokens.surface.primary }]}>
              <Text style={[styles.title, { color: tokens.text.primary }]}>Couldn’t take control</Text>
              <Text style={[styles.body, { color: tokens.status.danger }]}>
                {sessionForUs?.kind === 'failed' ? sessionForUs.reason : ''}
              </Text>
              <Pressable
                onPress={onResume}
                style={({ pressed }) => [
                  styles.cta,
                  { backgroundColor: tokens.accent.primary, opacity: pressed ? 0.85 : 1 },
                ]}>
                <Text style={[styles.ctaLabel, { color: tokens.accent.contrast }]}>Try again</Text>
              </Pressable>
            </View>
          ) : null}
        </View>

        <Animated.View style={[styles.keyBarSlot, keyBarSlideStyle]}>
          {sessionForUs?.kind === 'streaming' ? <KeyBar onBytes={handleKeyBarBytes} /> : null}
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  slider: { flex: 1 },
  terminalArea: { flex: 1 },
  keyBarSlot: { height: KEY_BAR_HEIGHT },
  banner: {
    position: 'absolute',
    top: 8,
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: StyleSheet.hairlineWidth,
  },
  bannerLabel: { fontSize: 13, fontWeight: '500' },
  softOverlay: {
    position: 'absolute',
    inset: 0,
    alignItems: 'center',
    justifyContent: 'center',
    opacity: 0.55,
    gap: 12,
  },
  softLabel: { fontSize: 14, fontWeight: '500' },
  fullOverlay: {
    position: 'absolute',
    inset: 0,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 12,
  },
  title: { fontSize: 20, fontWeight: '600' },
  body: { fontSize: 14, textAlign: 'center' },
  cta: { paddingHorizontal: 18, paddingVertical: 10, borderRadius: 999, marginTop: 8 },
  ctaLabel: { fontSize: 14, fontWeight: '600' },
  hiddenInput: {
    position: 'absolute',
    width: 1,
    height: 1,
    opacity: 0,
    top: 0,
    left: 0,
  },
});
