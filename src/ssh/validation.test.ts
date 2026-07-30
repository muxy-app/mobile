import type { SSHConnectionInput } from './types';
import { validateSSHConnectionInput } from './validation';

const validInput: SSHConnectionInput = {
  name: 'Production',
  host: 'prod.example.com',
  port: '22',
  username: 'deploy',
  authType: 'password',
  password: ' secret ',
  privateKey: '',
  passphrase: '',
};

describe('validateSSHConnectionInput', () => {
  it('normalizes connection fields without changing the password', () => {
    const result = validateSSHConnectionInput({
      ...validInput,
      name: ' Production ',
      host: ' 2001:db8::1 ',
      port: ' 2222 ',
      username: ' deploy ',
    });

    expect(result).toEqual({
      valid: true,
      value: {
        name: 'Production',
        host: '2001:db8::1',
        port: 2222,
        username: 'deploy',
        authType: 'password',
        credential: {
          type: 'password',
          password: ' secret ',
        },
      },
    });
  });

  it.each([
    ['', 'Enter a host or IP address.'],
    ['host name', 'Enter a valid host or IP address.'],
    ['host/path', 'Enter a valid host or IP address.'],
  ])('rejects invalid host %p', (host, message) => {
    expect(
      validateSSHConnectionInput({ ...validInput, host }),
    ).toEqual({ valid: false, message });
  });

  it.each(['', '0', '65536', '22.5', '-22', 'ssh'])(
    'rejects invalid port %p',
    (port) => {
      expect(
        validateSSHConnectionInput({ ...validInput, port }),
      ).toEqual({
        valid: false,
        message:
          port === ''
            ? 'Enter a port.'
            : 'Port must be between 1 and 65535.',
      });
    },
  );

  it('rejects an empty username', () => {
    expect(
      validateSSHConnectionInput({
        ...validInput,
        username: '   ',
      }),
    ).toEqual({
      valid: false,
      message: 'Enter a username.',
    });
  });

  it('requires the selected authentication secret', () => {
    expect(
      validateSSHConnectionInput({
        ...validInput,
        password: ' ',
      }),
    ).toEqual({
      valid: false,
      message: 'Enter a password.',
    });

    expect(
      validateSSHConnectionInput({
        ...validInput,
        authType: 'privateKey',
        privateKey: ' ',
      }),
    ).toEqual({
      valid: false,
      message: 'Enter a private key.',
    });
  });
});
