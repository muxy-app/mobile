import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  KeyboardController,
  KeyboardEvents,
  useReanimatedKeyboardAnimation,
} from 'react-native-keyboard-controller';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { bytesToBase64, stringToBase64 } from '@/lib/base64';
import {
  getNerdFont,
  loadNerdFont,
  type NerdFontBase64,
} from '@/lib/nerdFont';
import {
  addSSHClosedListener,
  addSSHDataListener,
  addSSHStateChangeListener,
  buildSSHNativeConfig,
  connect,
  disconnect,
  readKnownHostFingerprint,
  readSSHCredential,
  resize,
  useSSHStore,
  write,
  writeKnownHostFingerprint,
  type SSHConnection,
} from '@/ssh';
import { newEntryId, useSettingsStore } from '@/state';
import { useTokens } from '@/theme';

import { buildTerminalTheme } from '../terminal/buildTerminalTheme';
import {
  KEY_BAR_HEIGHT,
  KeyBar,
  transformWithModifiers,
} from '../terminal/KeyBar';
import { TerminalJumpToBottomButton } from '../terminal/TerminalJumpToBottomButton';
import {
  type TerminalDimensions,
  type TerminalKeyboardPhase,
  type TerminalWebViewHandle,
  TerminalWebView,
} from '../terminal/TerminalWebView';
import { TerminalInput } from '../terminal/TerminalInputCapture';
import type { TerminalInputHandle } from '../terminal/TerminalInputCapture.types';
import { scheduleTerminalInputFocus } from '../terminal/terminalFocus';
import { buildTerminalInputDiff } from '../terminal/terminalInput';
import { useSSHHostKeyPrompt } from './useSSHHostKeyPrompt';

type Props = {
  connection: SSHConnection;
};

const INPUT_SENTINEL = '​';

export function SSHTerminal(props: Props) {
  const tokens = useTokens();
  const useNerdFont = useSettingsStore((state) => state.useNerdFont);
  const [nerdFont, setNerdFont] = useState<NerdFontBase64 | null>(
    getNerdFont,
  );
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
    return (
      <View
        style={[
          styles.root,
          { backgroundColor: tokens.surface.primary },
        ]}
      />
    );
  }

  const selectedNerdFont = useNerdFont ? nerdFont : null;

  return (
    <SSHSessionTerminal
      key={selectedNerdFont ? 'nerd' : 'system'}
      {...props}
      nerdFont={selectedNerdFont}
    />
  );
}

function SSHSessionTerminal({
  connection,
  nerdFont,
}: Props & { nerdFont: NerdFontBase64 | null }) {
  const tokens = useTokens();
  const webRef = useRef<TerminalWebViewHandle>(null);
  const inputRef = useRef<TerminalInputHandle>(null);
  const sessionIdRef = useRef<string | null>(null);
  const dimensionsRef = useRef<TerminalDimensions | null>(null);
  const lastSentRef = useRef('');
  const [inputValue, setInputValue] = useState(INPUT_SENTINEL);
  const [focused, setFocused] = useState(false);
  const [hasDimensions, setHasDimensions] = useState(false);
  const [ready, setReady] = useState(false);
  const [isFollowingBottom, setIsFollowingBottom] = useState(true);
  const [attemptConnectionId, setAttemptConnectionId] =
    useState(newEntryId);

  const session = useSSHStore(
    (state) => state.sessions[connection.id],
  );
  const setSession = useSSHStore((state) => state.setSession);
  const clearSession = useSSHStore((state) => state.clearSession);
  const autoFocusTerminal = useSettingsStore(
    (state) => state.autoFocusTerminal,
  );
  const terminalTheme = useMemo(
    () => buildTerminalTheme(null, tokens),
    [tokens],
  );

  const handlePromptError = useCallback(
    (message: string) => {
      setSession(connection.id, {
        sessionId: sessionIdRef.current,
        state: 'failed',
        error: message,
      });
    },
    [connection.id, setSession],
  );
  const handleTrust = useCallback(
    async (fingerprint: string) => {
      await writeKnownHostFingerprint(connection.id, fingerprint);
    },
    [connection.id],
  );

  useSSHHostKeyPrompt({
    connectionId: attemptConnectionId,
    onTrust: handleTrust,
    onError: handlePromptError,
  });

  useEffect(() => {
    const dataSubscription = addSSHDataListener((event) => {
      if (event.connectionId !== attemptConnectionId) return;
      sessionIdRef.current = event.sessionId;
      webRef.current?.write(event.dataBase64);
    });
    const stateSubscription = addSSHStateChangeListener((event) => {
      if (event.connectionId !== attemptConnectionId) return;
      sessionIdRef.current = event.sessionId;
      setSession(connection.id, {
        sessionId: event.sessionId,
        state: event.state,
        error: event.errorMessage ?? null,
      });
    });
    const closedSubscription = addSSHClosedListener((event) => {
      if (event.connectionId !== attemptConnectionId) return;
      const current = useSSHStore.getState().sessions[connection.id];
      if (current?.state === 'failed') return;
      setSession(connection.id, {
        sessionId: event.sessionId,
        state: 'disconnected',
        error: event.reason ?? null,
      });
    });

    return () => {
      dataSubscription.remove();
      stateSubscription.remove();
      closedSubscription.remove();
      clearSession(connection.id);
    };
  }, [
    attemptConnectionId,
    clearSession,
    connection.id,
    setSession,
  ]);

  useEffect(() => {
    if (!ready || !hasDimensions) return;
    const initialDimensions = dimensionsRef.current;
    if (!initialDimensions) return;

    let active = true;
    sessionIdRef.current = null;
    setSession(connection.id, {
      sessionId: null,
      state: 'connecting',
      error: null,
    });

    const openSession = async () => {
      try {
        const [credential, knownHostFingerprint] = await Promise.all([
          readSSHCredential(connection.id),
          readKnownHostFingerprint(connection.id),
        ]);
        if (!credential) {
          throw new Error(
            'The saved SSH credentials could not be read.',
          );
        }
        const config = buildSSHNativeConfig(
          attemptConnectionId,
          connection,
          credential,
          initialDimensions,
          knownHostFingerprint ?? undefined,
        );
        const sessionId = await connect(config);
        if (!active) {
          await disconnect(sessionId);
          return;
        }
        sessionIdRef.current = sessionId;
      } catch (error) {
        if (!active) return;
        setSession(connection.id, {
          sessionId: sessionIdRef.current,
          state: 'failed',
          error:
            error instanceof Error
              ? error.message
              : 'Could not connect to the SSH server.',
        });
      }
    };

    void openSession();

    return () => {
      active = false;
      const sessionId = sessionIdRef.current;
      sessionIdRef.current = null;
      if (sessionId) {
        disconnect(sessionId).catch(() => {});
      }
    };
  }, [
    attemptConnectionId,
    connection,
    hasDimensions,
    ready,
    setSession,
  ]);

  useEffect(() => {
    if (ready) webRef.current?.setTheme(terminalTheme);
  }, [ready, terminalTheme]);

  useEffect(() => {
    if (!autoFocusTerminal || session?.state !== 'connected') return;
    return scheduleTerminalInputFocus(inputRef.current);
  }, [autoFocusTerminal, connection.id, session?.state]);

  const handleDimensions = useCallback((next: TerminalDimensions) => {
    dimensionsRef.current = next;
    setHasDimensions(true);
    const sessionId = sessionIdRef.current;
    if (sessionId) {
      resize(sessionId, next.cols, next.rows).catch(() => {});
    }
  }, []);

  const sendBase64 = useCallback(
    (base64: string) => {
      const sessionId = sessionIdRef.current;
      if (!sessionId) return;
      write(sessionId, base64).catch((error) => {
        setSession(connection.id, {
          sessionId,
          state: 'failed',
          error:
            error instanceof Error
              ? error.message
              : 'Could not send terminal input.',
        });
      });
    },
    [connection.id, setSession],
  );

  const handleData = useCallback(
    (base64: string) => {
      sendBase64(transformWithModifiers(base64));
    },
    [sendBase64],
  );

  const sendInputDiff = useCallback(
    (next: string) => {
      const previous = lastSentRef.current;
      const output = buildTerminalInputDiff(previous, next);
      lastSentRef.current = next;
      if (output) {
        sendBase64(
          transformWithModifiers(stringToBase64(output)),
        );
      }
    },
    [sendBase64],
  );

  const handleInputChange = useCallback(
    (text: string) => {
      const sentinelIndex = text.lastIndexOf(INPUT_SENTINEL);
      if (sentinelIndex === -1) {
        sendBase64(bytesToBase64(new Uint8Array([0x7f])));
        lastSentRef.current = '';
        setInputValue(INPUT_SENTINEL);
        return;
      }

      const body = text.slice(sentinelIndex + INPUT_SENTINEL.length);
      const newlineIndex = body.indexOf('\n');
      if (newlineIndex === -1) {
        setInputValue(INPUT_SENTINEL + body);
        sendInputDiff(body);
        return;
      }

      sendInputDiff(body.slice(0, newlineIndex));
      sendBase64(stringToBase64('\r'));
      lastSentRef.current = '';
      setInputValue(INPUT_SENTINEL);
    },
    [sendBase64, sendInputDiff],
  );

  const handleInputBlur = useCallback(() => {
    setFocused(false);
    lastSentRef.current = '';
    setInputValue(INPUT_SENTINEL);
  }, []);
  const handleInputFocus = useCallback(() => setFocused(true), []);

  const insets = useSafeAreaInsets();
  const { height } = useReanimatedKeyboardAnimation();
  const safeAreaStyle = useMemo(
    () => ({ paddingBottom: insets.bottom }),
    [insets.bottom],
  );
  const keyBarSlideStyle = useAnimatedStyle(() => ({
    transform: [
      { translateY: Math.min(0, height.value + insets.bottom) },
    ],
  }));
  const jumpToBottomSlideStyle = useAnimatedStyle(() => ({
    transform: [
      { translateY: Math.min(0, height.value + insets.bottom) },
    ],
  }));
  const setTerminalKeyboardOffset = useCallback(
    (
      keyboardHeight: number,
      duration: number,
      phase: TerminalKeyboardPhase,
    ) => {
      const offset = Math.round(
        Math.max(0, keyboardHeight - insets.bottom),
      );
      webRef.current?.setKeyboardOffset(offset, duration, phase);
    },
    [insets.bottom],
  );

  const keyboardVisibleRef = useRef(false);
  const keyboardTransitioningRef = useRef(false);

  useEffect(() => {
    const willShowSubscription = KeyboardEvents.addListener(
      'keyboardWillShow',
      (event) => {
        keyboardTransitioningRef.current = true;
        setTerminalKeyboardOffset(
          event.height,
          event.duration,
          'willShow',
        );
      },
    );
    const willHideSubscription = KeyboardEvents.addListener(
      'keyboardWillHide',
      (event) => {
        keyboardTransitioningRef.current = true;
        setTerminalKeyboardOffset(0, event.duration, 'willHide');
      },
    );
    const didShowSubscription = KeyboardEvents.addListener(
      'keyboardDidShow',
      (event) => {
        keyboardVisibleRef.current = true;
        keyboardTransitioningRef.current = false;
        setTerminalKeyboardOffset(event.height, 0, 'didShow');
      },
    );
    const didHideSubscription = KeyboardEvents.addListener(
      'keyboardDidHide',
      () => {
        keyboardVisibleRef.current = false;
        keyboardTransitioningRef.current = false;
        setTerminalKeyboardOffset(0, 0, 'didHide');
      },
    );

    return () => {
      willShowSubscription.remove();
      willHideSubscription.remove();
      didShowSubscription.remove();
      didHideSubscription.remove();
    };
  }, [setTerminalKeyboardOffset]);

  useEffect(() => {
    if (!ready) return;
    if (keyboardTransitioningRef.current) return;
    const visible = KeyboardController.isVisible();
    const keyboardState = KeyboardController.state();
    keyboardVisibleRef.current = visible;
    setTerminalKeyboardOffset(
      visible ? keyboardState.height : 0,
      0,
      'sync',
    );
  }, [ready, setTerminalKeyboardOffset]);

  const handleTap = useCallback(() => {
    if (keyboardVisibleRef.current) {
      Keyboard.dismiss();
      inputRef.current?.blur();
      return;
    }
    inputRef.current?.focus();
  }, []);

  const state = session?.state ?? 'idle';
  const error = session?.error;
  const connecting = state === 'idle' || state === 'connecting';
  const unavailable = state === 'failed' || state === 'disconnected';
  const handleReconnect = useCallback(() => {
    webRef.current?.clear();
    setAttemptConnectionId(newEntryId());
  }, []);
  const handleJumpToBottom = useCallback(() => {
    webRef.current?.scrollToBottom();
  }, []);

  return (
    <View
      style={[
        styles.root,
        { backgroundColor: terminalTheme.background },
      ]}>
      <View style={[styles.slider, safeAreaStyle]}>
        <View style={styles.terminalArea}>
          <TerminalWebView
            ref={webRef}
            theme={terminalTheme}
            nerdFont={nerdFont}
            focused={focused}
            onReady={() => setReady(true)}
            onDimensions={handleDimensions}
            onData={handleData}
            onFollowingBottomChange={setIsFollowingBottom}
            onTap={handleTap}
            onError={(message) => {
              console.log('[ssh-terminal] ' + message);
            }}
            onRenderer={(renderer, reason) => {
              if (reason) {
                console.log(
                  '[ssh-terminal] renderer=' +
                    renderer +
                    ' reason=' +
                    reason,
                );
                return;
              }
              console.log(
                '[ssh-terminal] renderer=' + renderer,
              );
            }}
          />

          {!isFollowingBottom ? (
            <TerminalJumpToBottomButton
              color={terminalTheme.foreground}
              onPress={handleJumpToBottom}
              style={jumpToBottomSlideStyle}
            />
          ) : null}

          <TerminalInput
            ref={inputRef}
            value={inputValue}
            onChangeText={handleInputChange}
            onFocus={handleInputFocus}
            onBlur={handleInputBlur}
            onHardwareInput={sendBase64}
          />

          {connecting ? (
            <View
              style={[
                styles.overlay,
                { backgroundColor: tokens.surface.primary },
              ]}>
              <ActivityIndicator color={tokens.accent.primary} />
              <Text
                style={[
                  styles.statusText,
                  { color: tokens.text.secondary },
                ]}>
                Connecting…
              </Text>
            </View>
          ) : null}

          {unavailable ? (
            <View
              style={[
                styles.overlay,
                { backgroundColor: tokens.surface.primary },
              ]}>
              <Text
                style={[
                  styles.errorTitle,
                  { color: tokens.text.primary },
                ]}>
                Connection closed
              </Text>
              <Text
                style={[
                  styles.errorBody,
                  {
                    color: error
                      ? tokens.status.danger
                      : tokens.text.muted,
                  },
                ]}>
                {error ?? 'The SSH session ended.'}
              </Text>
              <Pressable
                accessibilityRole="button"
                onPress={handleReconnect}
                style={({ pressed }) => [
                  styles.retry,
                  {
                    backgroundColor: tokens.accent.primary,
                    opacity: pressed ? 0.85 : 1,
                  },
                ]}>
                <Text
                  style={[
                    styles.retryLabel,
                    { color: tokens.accent.contrast },
                  ]}>
                  Reconnect
                </Text>
              </Pressable>
            </View>
          ) : null}
        </View>

        <Animated.View
          style={[styles.keyBarSlot, keyBarSlideStyle]}>
          {state === 'connected' ? (
            <KeyBar onBytes={sendBase64} />
          ) : null}
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
  overlay: {
    position: 'absolute',
    inset: 0,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 32,
    gap: 12,
  },
  statusText: { fontSize: 14, fontWeight: '500' },
  errorTitle: { fontSize: 20, fontWeight: '600' },
  errorBody: { fontSize: 14, textAlign: 'center' },
  retry: {
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 999,
    marginTop: 8,
  },
  retryLabel: { fontSize: 14, fontWeight: '600' },
});
