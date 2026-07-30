import {
  addSSHClosedListener,
  addSSHDataListener,
  addSSHHostKeyPromptListener,
  addSSHStateChangeListener,
  connect,
  disconnect,
  resize,
  respondToHostKey,
  testConnection,
  write,
  type SSHConnectionConfig,
} from '../../modules/muxy-ssh';

import type { SSHConnection, SSHCredential } from './types';

export {
  addSSHClosedListener,
  addSSHDataListener,
  addSSHHostKeyPromptListener,
  addSSHStateChangeListener,
  connect,
  disconnect,
  resize,
  respondToHostKey,
  testConnection,
  write,
};

export function buildSSHNativeConfig(
  connectionId: string,
  connection: Pick<SSHConnection, 'host' | 'port' | 'username' | 'authType'>,
  credential: SSHCredential,
  dimensions: { cols: number; rows: number },
  knownHostFingerprint?: string,
): SSHConnectionConfig {
  if (connection.authType !== credential.type) {
    throw new Error('The saved SSH credentials do not match this connection.');
  }

  return {
    connectionId,
    host: connection.host,
    port: connection.port,
    username: connection.username,
    auth: credential,
    cols: dimensions.cols,
    rows: dimensions.rows,
    termType: 'xterm-256color',
    knownHostFingerprint,
  };
}
