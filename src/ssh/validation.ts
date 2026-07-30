import type {
  SSHConnectionInput,
  SSHCredential,
} from './types';

export type ValidatedSSHConnectionInput = {
  name: string;
  host: string;
  port: number;
  username: string;
  authType: SSHConnectionInput['authType'];
  credential: SSHCredential;
};

export type SSHValidationResult =
  | { valid: true; value: ValidatedSSHConnectionInput }
  | { valid: false; message: string };

const HOST_CHARACTERS = /^[a-zA-Z0-9.:-]+$/;
const PORT = /^\d+$/;

export function validateSSHConnectionInput(
  input: SSHConnectionInput,
): SSHValidationResult {
  const name = input.name.trim();
  if (!name) return { valid: false, message: 'Enter a name.' };

  const host = input.host.trim();
  if (!host) return { valid: false, message: 'Enter a host or IP address.' };
  if (!HOST_CHARACTERS.test(host)) {
    return { valid: false, message: 'Enter a valid host or IP address.' };
  }

  const portText = input.port.trim();
  if (!portText) return { valid: false, message: 'Enter a port.' };
  if (!PORT.test(portText)) {
    return { valid: false, message: 'Port must be between 1 and 65535.' };
  }
  const port = Number(portText);
  if (!Number.isInteger(port) || port < 1 || port > 65_535) {
    return { valid: false, message: 'Port must be between 1 and 65535.' };
  }

  const username = input.username.trim();
  if (!username) return { valid: false, message: 'Enter a username.' };

  if (input.authType === 'password') {
    if (!input.password.trim()) {
      return { valid: false, message: 'Enter a password.' };
    }
    return {
      valid: true,
      value: {
        name,
        host,
        port,
        username,
        authType: input.authType,
        credential: {
          type: 'password',
          password: input.password,
        },
      },
    };
  }

  if (!input.privateKey.trim()) {
    return { valid: false, message: 'Enter a private key.' };
  }
  return {
    valid: true,
    value: {
      name,
      host,
      port,
      username,
      authType: input.authType,
      credential: {
        type: 'privateKey',
        privateKey: input.privateKey,
        passphrase: input.passphrase.trim()
          ? input.passphrase
          : undefined,
      },
    },
  };
}
