import type { SSHConnection, SSHLiveSession } from './types';
import { useSSHStore } from './store';

jest.mock(
  '@react-native-async-storage/async-storage',
  () =>
    jest.requireActual(
      '@react-native-async-storage/async-storage/jest/async-storage-mock',
    ),
);

const firstConnection: SSHConnection = {
  id: 'c748af25-8015-4fba-8f17-f8e53e89db99',
  name: 'Production',
  host: 'prod.example.com',
  port: 22,
  username: 'deploy',
  authType: 'privateKey',
  createdAt: '2026-07-30T10:00:00.000Z',
  updatedAt: '2026-07-30T10:00:00.000Z',
};

const secondConnection: SSHConnection = {
  id: '0af70f0f-ae6a-4d46-a07e-65df84b427b4',
  name: 'Staging',
  host: 'staging.example.com',
  port: 2222,
  username: 'developer',
  authType: 'password',
  createdAt: '2026-07-30T11:00:00.000Z',
  updatedAt: '2026-07-30T11:00:00.000Z',
};

function resetStore(): void {
  useSSHStore.setState({
    hasHydrated: true,
    connections: [],
    sessions: {},
  });
}

describe('SSH store', () => {
  beforeEach(resetStore);

  it('adds and edits connections in place', () => {
    useSSHStore.getState().upsertConnection(firstConnection);
    useSSHStore.getState().upsertConnection(secondConnection);

    const editedConnection = {
      ...firstConnection,
      name: 'Production Primary',
      updatedAt: '2026-07-30T12:00:00.000Z',
    };
    useSSHStore.getState().upsertConnection(editedConnection);

    expect(useSSHStore.getState().connections).toEqual([
      editedConnection,
      secondConnection,
    ]);
  });

  it('removes a connection and its live session', () => {
    useSSHStore.setState({
      connections: [firstConnection, secondConnection],
      sessions: {
        [firstConnection.id]: {
          sessionId: 'session-1',
          state: 'connected',
          error: null,
        },
        [secondConnection.id]: {
          sessionId: 'session-2',
          state: 'connected',
          error: null,
        },
      },
    });

    useSSHStore.getState().removeConnection(firstConnection.id);

    expect(useSSHStore.getState().connections).toEqual([
      secondConnection,
    ]);
    expect(useSSHStore.getState().sessions).toEqual({
      [secondConnection.id]: {
        sessionId: 'session-2',
        state: 'connected',
        error: null,
      },
    });
  });

  it('tracks session transitions and clears the final state', () => {
    const transitions: SSHLiveSession[] = [
      {
        sessionId: null,
        state: 'connecting',
        error: null,
      },
      {
        sessionId: 'session-1',
        state: 'connected',
        error: null,
      },
      {
        sessionId: 'session-1',
        state: 'failed',
        error: 'Connection lost.',
      },
    ];

    for (const transition of transitions) {
      useSSHStore
        .getState()
        .setSession(firstConnection.id, transition);
      expect(useSSHStore.getState().sessions[firstConnection.id]).toEqual(
        transition,
      );
    }

    useSSHStore.getState().clearSession(firstConnection.id);

    expect(useSSHStore.getState().sessions).toEqual({});
  });
});
