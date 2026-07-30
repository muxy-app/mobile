import { Alert } from 'react-native';

import {
  addSSHHostKeyPromptListener,
  respondToHostKey,
} from '@/ssh';

import { subscribeToSSHHostKeyPrompts } from './useSSHHostKeyPrompt';

jest.mock('@/ssh', () => ({
  addSSHHostKeyPromptListener: jest.fn(),
  respondToHostKey: jest.fn(),
}));

const mockAddListener = jest.mocked(addSSHHostKeyPromptListener);
const mockRespondToHostKey = jest.mocked(respondToHostKey);
type SSHHostKeyPromptEvent = Parameters<
  Parameters<typeof addSSHHostKeyPromptListener>[0]
>[0];
const event: SSHHostKeyPromptEvent = {
  connectionId: 'c748af25-8015-4fba-8f17-f8e53e89db99',
  sessionId: 'session-1',
  fingerprint:
    '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
  keyType: 'ssh-ed25519',
};

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

describe('subscribeToSSHHostKeyPrompts', () => {
  let listener: (prompt: SSHHostKeyPromptEvent) => void;
  let remove: jest.Mock;
  let alert: jest.SpiedFunction<typeof Alert.alert>;
  let onTrust: jest.MockedFunction<
    (fingerprint: string) => Promise<void>
  >;
  let onError: jest.MockedFunction<(message: string) => void>;

  beforeEach(() => {
    remove = jest.fn();
    mockAddListener.mockImplementation((nextListener) => {
      listener = nextListener;
      return { remove };
    });
    mockRespondToHostKey.mockResolvedValue(undefined);
    alert = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    onTrust = jest.fn().mockResolvedValue(undefined);
    onError = jest.fn();
  });

  afterEach(() => {
    jest.restoreAllMocks();
    mockAddListener.mockReset();
    mockRespondToHostKey.mockReset();
  });

  it('trusts a prompted host after persisting its fingerprint', async () => {
    const unsubscribe = subscribeToSSHHostKeyPrompts({
      connectionId: event.connectionId,
      onTrust,
      onError,
    });

    listener(event);
    const trustButton = alert.mock.calls[0]?.[2]?.find(
      (button) => button.text === 'Trust',
    );
    trustButton?.onPress?.();
    await flushPromises();

    expect(onTrust).toHaveBeenCalledWith(event.fingerprint);
    expect(mockRespondToHostKey).toHaveBeenCalledWith(
      event.sessionId,
      true,
    );
    expect(onError).not.toHaveBeenCalled();

    unsubscribe();
    expect(remove).toHaveBeenCalledTimes(1);
  });

  it('rejects a prompted host when the user cancels', () => {
    const unsubscribe = subscribeToSSHHostKeyPrompts({
      connectionId: event.connectionId,
      onTrust,
      onError,
    });

    listener(event);
    const cancelButton = alert.mock.calls[0]?.[2]?.find(
      (button) => button.text === 'Cancel',
    );
    cancelButton?.onPress?.();

    expect(mockRespondToHostKey).toHaveBeenCalledWith(
      event.sessionId,
      false,
    );
    expect(onTrust).not.toHaveBeenCalled();

    unsubscribe();
  });

  it('rejects a host if its fingerprint cannot be persisted', async () => {
    onTrust.mockRejectedValue(new Error('Secure storage failed.'));
    subscribeToSSHHostKeyPrompts({
      connectionId: event.connectionId,
      onTrust,
      onError,
    });

    listener(event);
    const trustButton = alert.mock.calls[0]?.[2]?.find(
      (button) => button.text === 'Trust',
    );
    trustButton?.onPress?.();
    await flushPromises();

    expect(mockRespondToHostKey).toHaveBeenCalledWith(
      event.sessionId,
      false,
    );
    expect(onError).toHaveBeenCalledWith('Secure storage failed.');
  });
});
