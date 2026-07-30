import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { SSHConnection, SSHLiveSession } from './types';

type SSHState = {
  hasHydrated: boolean;
  connections: SSHConnection[];
  sessions: Record<string, SSHLiveSession>;
};

type SSHActions = {
  setHasHydrated: (value: boolean) => void;
  upsertConnection: (connection: SSHConnection) => void;
  removeConnection: (connectionId: string) => void;
  setSession: (
    connectionId: string,
    session: SSHLiveSession,
  ) => void;
  clearSession: (connectionId: string) => void;
};

export type SSHStore = SSHState & SSHActions;

export const useSSHStore = create<SSHStore>()(
  persist(
    (set) => ({
      hasHydrated: false,
      connections: [],
      sessions: {},
      setHasHydrated: (value) => set({ hasHydrated: value }),
      upsertConnection: (connection) =>
        set((state) => {
          const index = state.connections.findIndex(
            (candidate) => candidate.id === connection.id,
          );
          if (index < 0) {
            return { connections: [...state.connections, connection] };
          }
          const connections = state.connections.slice();
          connections[index] = connection;
          return { connections };
        }),
      removeConnection: (connectionId) =>
        set((state) => {
          const { [connectionId]: _removed, ...sessions } = state.sessions;
          return {
            connections: state.connections.filter(
              (connection) => connection.id !== connectionId,
            ),
            sessions,
          };
        }),
      setSession: (connectionId, session) =>
        set((state) => ({
          sessions: {
            ...state.sessions,
            [connectionId]: session,
          },
        })),
      clearSession: (connectionId) =>
        set((state) => {
          if (!(connectionId in state.sessions)) return state;
          const { [connectionId]: _removed, ...sessions } = state.sessions;
          return { sessions };
        }),
    }),
    {
      name: 'muxy.ssh.connections.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        connections: state.connections,
      }),
      merge: (persisted, current) => ({
        ...current,
        connections: readPersistedConnections(persisted),
        sessions: {},
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    },
  ),
);

function readPersistedConnections(persisted: unknown): SSHConnection[] {
  if (!persisted || typeof persisted !== 'object') return [];
  if (!('connections' in persisted)) return [];
  if (!Array.isArray(persisted.connections)) return [];

  const seen = new Set<string>();
  return persisted.connections.filter(
    (connection): connection is SSHConnection => {
      if (!isSSHConnection(connection)) return false;
      if (seen.has(connection.id)) return false;
      seen.add(connection.id);
      return true;
    },
  );
}

function isSSHConnection(value: unknown): value is SSHConnection {
  if (!value || typeof value !== 'object') return false;
  if (!('id' in value) || !isUUID(value.id)) return false;
  if (!('name' in value) || !isNonEmptyString(value.name)) return false;
  if (!('host' in value) || !isValidHost(value.host)) return false;
  if (!('port' in value) || !isValidPort(value.port)) return false;
  if (!('username' in value) || !isNonEmptyString(value.username)) {
    return false;
  }
  if (
    !('authType' in value) ||
    (value.authType !== 'password' && value.authType !== 'privateKey')
  ) {
    return false;
  }
  if (!('createdAt' in value) || !isISODate(value.createdAt)) return false;
  if (!('updatedAt' in value) || !isISODate(value.updatedAt)) return false;
  return true;
}

function isUUID(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      value,
    )
  );
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isValidHost(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    /^[a-zA-Z0-9.:-]+$/.test(value)
  );
}

function isValidPort(value: unknown): value is number {
  return (
    typeof value === 'number' &&
    Number.isInteger(value) &&
    value >= 1 &&
    value <= 65_535
  );
}

function isISODate(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return false;
  return date.toISOString() === value;
}
