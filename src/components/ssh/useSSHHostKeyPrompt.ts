import { useEffect } from 'react';
import { Alert } from 'react-native';

import {
  addSSHHostKeyPromptListener,
  respondToHostKey,
} from '@/ssh';

type Options = {
  connectionId: string;
  onTrust: (fingerprint: string) => Promise<void>;
  onError: (message: string) => void;
};

export function subscribeToSSHHostKeyPrompts({
  connectionId,
  onTrust,
  onError,
}: Options): () => void {
  let active = true;
  const pendingSessions = new Set<string>();
  const reject = (sessionId: string) => {
    pendingSessions.delete(sessionId);
    respondToHostKey(sessionId, false).catch(() => {});
  };

  const subscription = addSSHHostKeyPromptListener((event) => {
    if (event.connectionId !== connectionId) return;
    if (pendingSessions.has(event.sessionId)) return;
    pendingSessions.add(event.sessionId);

    const approve = async () => {
      if (!active) return;
      try {
        await onTrust(event.fingerprint);
        if (!active) return;
        await respondToHostKey(event.sessionId, true);
        pendingSessions.delete(event.sessionId);
      } catch (error) {
        reject(event.sessionId);
        if (!active) return;
        onError(
          error instanceof Error
            ? error.message
            : 'Could not save the trusted host key.',
        );
      }
    };

    Alert.alert(
      'Trust SSH Host?',
      `${event.keyType}\nSHA-256 ${formatFingerprint(event.fingerprint)}`,
      [
        {
          text: 'Cancel',
          style: 'cancel',
          onPress: () => reject(event.sessionId),
        },
        {
          text: 'Trust',
          onPress: () => {
            void approve();
          },
        },
      ],
      { cancelable: false },
    );
  });

  return () => {
    active = false;
    subscription.remove();
    for (const sessionId of pendingSessions) {
      respondToHostKey(sessionId, false).catch(() => {});
    }
    pendingSessions.clear();
  };
}

export function useSSHHostKeyPrompt({
  connectionId,
  onTrust,
  onError,
}: Options): void {
  useEffect(
    () =>
      subscribeToSSHHostKeyPrompts({
        connectionId,
        onTrust,
        onError,
      }),
    [connectionId, onError, onTrust],
  );
}

function formatFingerprint(fingerprint: string): string {
  return fingerprint.match(/.{1,2}/g)?.join(':') ?? fingerprint;
}
