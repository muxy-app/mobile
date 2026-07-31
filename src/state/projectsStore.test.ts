import AsyncStorage from '@react-native-async-storage/async-storage';

import type { Project } from '@/transport';

import { useProjectsStore } from './projectsStore';

jest.mock(
  '@react-native-async-storage/async-storage',
  () =>
    jest.requireActual(
      '@react-native-async-storage/async-storage/jest/async-storage-mock',
    ),
);

const firstConnectionID = '11111111-1111-4111-8111-111111111111';
const secondConnectionID = '22222222-2222-4222-8222-222222222222';
const workWorkspaceID = '33333333-3333-4333-8333-333333333333';
const personalWorkspaceID = '44444444-4444-4444-8444-444444444444';

function project(id: string, sortOrder: number, workspaceID: string): Project {
  return {
    id,
    name: id,
    path: `/projects/${id}`,
    sortOrder,
    createdAt: '2026-07-31T10:00:00Z',
    workspaceID,
    workspaceName: workspaceID,
  };
}

function resetStore(): void {
  useProjectsStore.setState({
    hasHydrated: true,
    projects: [],
    logos: {},
    selectedWorkspaceIDs: {},
    fetchPhase: 'idle',
    fetchError: null,
  });
}

describe('projects store', () => {
  beforeEach(async () => {
    resetStore();
    await AsyncStorage.clear();
  });

  it('keeps workspace selections scoped to each connection', () => {
    const store = useProjectsStore.getState();

    store.setSelectedWorkspaceID(firstConnectionID, workWorkspaceID);
    store.setSelectedWorkspaceID(secondConnectionID, personalWorkspaceID);

    expect(useProjectsStore.getState().selectedWorkspaceIDs).toEqual({
      [firstConnectionID]: workWorkspaceID,
      [secondConnectionID]: personalWorkspaceID,
    });

    useProjectsStore.getState().setSelectedWorkspaceID(firstConnectionID, null);

    expect(useProjectsStore.getState().selectedWorkspaceIDs).toEqual({
      [secondConnectionID]: personalWorkspaceID,
    });
  });

  it('preserves selections when transient project state is cleared', () => {
    useProjectsStore.getState().setSelectedWorkspaceID(firstConnectionID, workWorkspaceID);
    useProjectsStore.setState({
      projects: [project('project-1', 0, workWorkspaceID)],
      logos: { 'project-1': 'data:image/png;base64,logo' },
      fetchPhase: 'error',
      fetchError: 'Failed',
    });

    useProjectsStore.getState().clear();

    expect(useProjectsStore.getState()).toMatchObject({
      hasHydrated: true,
      projects: [],
      logos: {},
      selectedWorkspaceIDs: { [firstConnectionID]: workWorkspaceID },
      fetchPhase: 'idle',
      fetchError: null,
    });
  });

  it('sorts projects and prunes only a stale selection for the active connection', () => {
    useProjectsStore.setState({
      selectedWorkspaceIDs: {
        [firstConnectionID]: personalWorkspaceID,
        [secondConnectionID]: workWorkspaceID,
      },
    });

    useProjectsStore
      .getState()
      .setProjects(
        [project('later', 2, workWorkspaceID), project('earlier', 1, workWorkspaceID)],
        firstConnectionID,
      );

    expect(useProjectsStore.getState().projects.map((candidate) => candidate.id)).toEqual([
      'earlier',
      'later',
    ]);
    expect(useProjectsStore.getState().selectedWorkspaceIDs).toEqual({
      [secondConnectionID]: workWorkspaceID,
    });
  });

  it('keeps a saved selection while a connection has no projects', () => {
    useProjectsStore.getState().setSelectedWorkspaceID(firstConnectionID, workWorkspaceID);

    useProjectsStore.getState().setProjects([], firstConnectionID);

    expect(useProjectsStore.getState().selectedWorkspaceIDs).toEqual({
      [firstConnectionID]: workWorkspaceID,
    });
  });

  it('writes only workspace selections to persisted storage', async () => {
    useProjectsStore.getState().setSelectedWorkspaceID(firstConnectionID, workWorkspaceID);
    useProjectsStore.getState().setSelectedWorkspaceID(secondConnectionID, personalWorkspaceID);

    const persisted = await AsyncStorage.getItem('muxy.projects.v1');

    expect(JSON.parse(persisted ?? '')).toEqual({
      state: {
        selectedWorkspaceIDs: {
          [firstConnectionID]: workWorkspaceID,
          [secondConnectionID]: personalWorkspaceID,
        },
      },
      version: 0,
    });
  });
});
