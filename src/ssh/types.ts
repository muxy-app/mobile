export type SSHAuthType = 'password' | 'privateKey';

export type SSHConnection = {
  id: string;
  name: string;
  host: string;
  port: number;
  username: string;
  authType: SSHAuthType;
  createdAt: string;
  updatedAt: string;
};

export type SSHCredential =
  | {
      type: 'password';
      password: string;
    }
  | {
      type: 'privateKey';
      privateKey: string;
      passphrase?: string;
    };

export type SSHLiveSession = {
  sessionId: string | null;
  state:
    | 'idle'
    | 'connecting'
    | 'connected'
    | 'disconnected'
    | 'failed';
  error: string | null;
};

export type SSHConnectionInput = {
  name: string;
  host: string;
  port: string;
  username: string;
  authType: SSHAuthType;
  password: string;
  privateKey: string;
  passphrase: string;
};
