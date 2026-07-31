import AsyncStorage from '@react-native-async-storage/async-storage';
import { useEffect } from 'react';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import { client, useDevicesStore, useWorkspaceStore } from '@/state';
import type {
  VCSBranches,
  VCSDiff,
  VCSMergeMethod,
  VCSPRCreated,
  VCSStatus,
  Worktree,
} from '@/transport';

type Slice<T> = {
  data: T | null;
  loading: boolean;
  error: string | null;
};

type ProjectGitState = {
  status: Slice<VCSStatus>;
  branches: Slice<VCSBranches>;
  diffsByPath: Record<string, Slice<VCSDiff>>;
};

type WorktreeSlice = Slice<Worktree[]> & {
  stale: boolean;
};

type State = {
  byProject: Record<string, ProjectGitState>;
};

type Actions = {
  refreshStatus: (projectId: string) => Promise<void>;
  refreshBranches: (projectId: string) => Promise<void>;
  refreshWorktrees: (projectId: string) => Promise<void>;

  commit: (projectId: string, message: string, stageAll: boolean) => Promise<void>;
  push: (projectId: string) => Promise<void>;
  pull: (projectId: string) => Promise<void>;
  switchBranch: (projectId: string, branch: string) => Promise<void>;
  createBranch: (projectId: string, name: string) => Promise<void>;
  createPR: (
    projectId: string,
    input: { title: string; body: string; baseBranch?: string; draft: boolean },
  ) => Promise<VCSPRCreated>;
  mergePullRequest: (
    projectId: string,
    input: { number: number; method: VCSMergeMethod; deleteBranch: boolean },
  ) => Promise<void>;
  addWorktree: (
    projectId: string,
    input: { name: string; branch: string; createBranch: boolean },
  ) => Promise<void>;
  removeWorktree: (projectId: string, worktreeId: string) => Promise<void>;
  selectWorktree: (projectId: string, worktreeId: string) => Promise<void>;
  loadDiff: (projectId: string, filePath: string, forceFull: boolean) => Promise<void>;
};

export type GitStore = State & Actions;

const EMPTY_SLICE: Slice<never> = { data: null, loading: false, error: null };

const emptySlice = <T>(): Slice<T> => EMPTY_SLICE as Slice<T>;

const emptyProject = (): ProjectGitState => ({
  status: emptySlice<VCSStatus>(),
  branches: emptySlice<VCSBranches>(),
  diffsByPath: {},
});

type SliceKey = Exclude<keyof ProjectGitState, 'diffsByPath'>;

type SlicePatch = { data?: unknown; loading?: boolean; error?: string | null };

function patchSlice(
  state: State,
  projectId: string,
  key: SliceKey,
  patch: SlicePatch,
): Pick<State, 'byProject'> {
  const project = state.byProject[projectId] ?? emptyProject();
  return {
    byProject: {
      ...state.byProject,
      [projectId]: {
        ...project,
        [key]: { ...project[key], ...patch },
      },
    },
  };
}

type DiffSlicePatch = { data?: VCSDiff; loading?: boolean; error?: string | null };

function patchDiffSlice(
  state: State,
  projectId: string,
  filePath: string,
  patch: DiffSlicePatch,
): Pick<State, 'byProject'> {
  const project = state.byProject[projectId] ?? emptyProject();
  const existing = project.diffsByPath[filePath] ?? emptySlice<VCSDiff>();
  return {
    byProject: {
      ...state.byProject,
      [projectId]: {
        ...project,
        diffsByPath: {
          ...project.diffsByPath,
          [filePath]: { ...existing, ...patch },
        },
      },
    },
  };
}

function clearDiffs(state: State, projectId: string): Pick<State, 'byProject'> {
  const project = state.byProject[projectId];
  if (!project || Object.keys(project.diffsByPath).length === 0) return state;
  return {
    byProject: {
      ...state.byProject,
      [projectId]: { ...project, diffsByPath: {} },
    },
  };
}

const EMPTY_WORKTREE_SLICE: WorktreeSlice = {
  data: null,
  loading: false,
  error: null,
  stale: true,
};

function worktreeScope(connectionId: string, projectId: string): string {
  return JSON.stringify([connectionId, projectId]);
}

function activeWorktreeScope(projectId: string): string {
  const connectionId = useDevicesStore.getState().activeDeviceId;
  if (!connectionId) throw new Error('No active device');
  return worktreeScope(connectionId, projectId);
}

type WorktreeCacheState = {
  hasHydrated: boolean;
  worktreesByScope: Record<string, WorktreeSlice>;
};

type WorktreeCacheActions = {
  setHasHydrated: (value: boolean) => void;
  patchWorktrees: (scope: string, patch: Partial<WorktreeSlice>) => void;
};

export type WorktreeCacheStore = WorktreeCacheState & WorktreeCacheActions;

export const useWorktreeCacheStore = create<WorktreeCacheStore>()(
  persist(
    (set) => ({
      hasHydrated: false,
      worktreesByScope: {},
      setHasHydrated: (value) => set({ hasHydrated: value }),
      patchWorktrees: (scope, patch) =>
        set((state) => ({
          worktreesByScope: {
            ...state.worktreesByScope,
            [scope]: {
              ...(state.worktreesByScope[scope] ?? EMPTY_WORKTREE_SLICE),
              ...patch,
            },
          },
        })),
    }),
    {
      name: 'muxy.git.worktrees.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        worktreesByScope: Object.fromEntries(
          Object.entries(state.worktreesByScope)
            .filter(([, slice]) => slice.data !== null)
            .map(([scope, slice]) => [
              scope,
              {
                data: slice.data,
                loading: false,
                error: null,
                stale: true,
              },
            ]),
        ),
      }),
      merge: (persisted, current) => ({
        ...current,
        worktreesByScope: readPersistedWorktrees(persisted),
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    },
  ),
);

export const useGitStore = create<GitStore>((set) => {
  const runFetch = async <T>(
    projectId: string,
    key: SliceKey,
    fetcher: () => Promise<T>,
  ): Promise<void> => {
    set((s) => patchSlice(s, projectId, key, { loading: true, error: null }));
    try {
      const data = await fetcher();
      set((s) => patchSlice(s, projectId, key, { data, loading: false }));
    } catch (err) {
      const message = err instanceof Error ? err.message : `Failed to load ${key}`;
      set((s) => patchSlice(s, projectId, key, { loading: false, error: message }));
    }
  };

  const refreshStatus = (projectId: string) =>
    runFetch(projectId, 'status', async () => {
      const res = await client.request('vcsRefresh', {
        type: 'vcsRefresh',
        value: { projectID: projectId },
      });
      return res.value;
    });

  const refreshBranches = (projectId: string) =>
    runFetch(projectId, 'branches', async () => {
      const res = await client.request('vcsListBranches', {
        type: 'vcsListBranches',
        value: { projectID: projectId },
      });
      return res.value;
    });

  const refreshWorktrees = async (projectId: string) => {
    const scope = activeWorktreeScope(projectId);
    const patchWorktrees = useWorktreeCacheStore.getState().patchWorktrees;
    patchWorktrees(scope, { loading: true, error: null });
    try {
      const res = await client.request('listWorktrees', {
        type: 'listWorktrees',
        value: { projectID: projectId },
      });
      patchWorktrees(scope, {
        data: res.value,
        loading: false,
        error: null,
        stale: false,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load worktrees';
      patchWorktrees(scope, { loading: false, error: message });
    }
  };

  return {
    byProject: {},

    refreshStatus,
    refreshBranches,
    refreshWorktrees,

    commit: async (projectId, message, stageAll) => {
      await client.request('vcsCommit', {
        type: 'vcsCommit',
        value: { projectID: projectId, message, stageAll },
      });
      set((s) => clearDiffs(s, projectId));
      await refreshStatus(projectId);
    },

    push: async (projectId) => {
      await client.request('vcsPush', {
        type: 'vcsPush',
        value: { projectID: projectId },
      });
      await refreshStatus(projectId);
    },

    pull: async (projectId) => {
      await client.request('vcsPull', {
        type: 'vcsPull',
        value: { projectID: projectId },
      });
      set((s) => clearDiffs(s, projectId));
      await refreshStatus(projectId);
    },

    switchBranch: async (projectId, branch) => {
      await client.request('vcsSwitchBranch', {
        type: 'vcsSwitchBranch',
        value: { projectID: projectId, branch },
      });
      set((s) => clearDiffs(s, projectId));
      await refreshStatus(projectId);
      await refreshBranches(projectId);
    },

    createBranch: async (projectId, name) => {
      await client.request('vcsCreateBranch', {
        type: 'vcsCreateBranch',
        value: { projectID: projectId, name },
      });
      await refreshStatus(projectId);
      await refreshBranches(projectId);
    },

    createPR: async (projectId, input) => {
      const res = await client.request('vcsCreatePR', {
        type: 'vcsCreatePR',
        value: {
          projectID: projectId,
          title: input.title,
          body: input.body,
          baseBranch: input.baseBranch,
          draft: input.draft,
        },
      });
      await refreshStatus(projectId);
      return res.value;
    },

    mergePullRequest: async (projectId, input) => {
      await client.request('vcsMergePullRequest', {
        type: 'vcsMergePullRequest',
        value: {
          projectID: projectId,
          number: input.number,
          method: input.method,
          deleteBranch: input.deleteBranch,
        },
      });
      await refreshStatus(projectId);
    },

    addWorktree: async (projectId, input) => {
      const scope = activeWorktreeScope(projectId);
      const res = await client.request('vcsAddWorktree', {
        type: 'vcsAddWorktree',
        value: {
          projectID: projectId,
          name: input.name,
          branch: input.branch,
          createBranch: input.createBranch,
        },
      });
      useWorktreeCacheStore.getState().patchWorktrees(scope, {
        data: res.value,
        loading: false,
        error: null,
        stale: false,
      });
      await refreshStatus(projectId);
      await refreshBranches(projectId);
    },

    removeWorktree: async (projectId, worktreeId) => {
      await client.request('vcsRemoveWorktree', {
        type: 'vcsRemoveWorktree',
        value: { projectID: projectId, worktreeID: worktreeId },
      });
      await refreshWorktrees(projectId);
    },

    selectWorktree: async (projectId, worktreeId) => {
      await client.request('selectWorktree', {
        type: 'selectWorktree',
        value: { projectID: projectId, worktreeID: worktreeId },
      });
      set((s) => clearDiffs(s, projectId));
      useWorkspaceStore.getState().setActiveWorktreeLocal(projectId, worktreeId);
      await Promise.all([refreshStatus(projectId), refreshWorktrees(projectId)]);
    },

    loadDiff: async (projectId, filePath, forceFull) => {
      set((s) => patchDiffSlice(s, projectId, filePath, { loading: true, error: null }));
      try {
        const res = await client.request('vcsGetDiff', {
          type: 'vcsGetDiff',
          value: { projectID: projectId, filePath, forceFull },
        });
        set((s) => patchDiffSlice(s, projectId, filePath, { data: res.value, loading: false }));
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to load diff';
        set((s) => patchDiffSlice(s, projectId, filePath, { loading: false, error: message }));
      }
    },
  };
});

function readPersistedWorktrees(persisted: unknown): Record<string, WorktreeSlice> {
  if (!persisted || typeof persisted !== 'object') return {};
  if (!('worktreesByScope' in persisted)) return {};
  if (!persisted.worktreesByScope || typeof persisted.worktreesByScope !== 'object') return {};
  if (Array.isArray(persisted.worktreesByScope)) return {};

  return Object.fromEntries(
    Object.entries(persisted.worktreesByScope).flatMap(([scope, value]) => {
      if (!isWorktreeScope(scope)) return [];
      if (!value || typeof value !== 'object') return [];
      if (!('data' in value) || !Array.isArray(value.data)) return [];
      if (!value.data.every(isWorktree)) return [];
      return [[scope, { data: value.data, loading: false, error: null, stale: true }]];
    }),
  );
}

function isWorktreeScope(value: string): boolean {
  try {
    const scope = JSON.parse(value);
    return (
      Array.isArray(scope) &&
      scope.length === 2 &&
      scope.every((entry) => typeof entry === 'string' && entry.length > 0)
    );
  } catch {
    return false;
  }
}

function isWorktree(value: unknown): value is Worktree {
  if (!value || typeof value !== 'object') return false;
  return (
    'id' in value &&
    typeof value.id === 'string' &&
    'name' in value &&
    typeof value.name === 'string' &&
    'path' in value &&
    typeof value.path === 'string' &&
    'branch' in value &&
    typeof value.branch === 'string' &&
    'isPrimary' in value &&
    typeof value.isPrimary === 'boolean' &&
    'canBeRemoved' in value &&
    typeof value.canBeRemoved === 'boolean' &&
    'createdAt' in value &&
    typeof value.createdAt === 'string'
  );
}

export function selectStatus(projectId: string) {
  return (s: GitStore): Slice<VCSStatus> => s.byProject[projectId]?.status ?? emptySlice();
}

export function selectBranches(projectId: string) {
  return (s: GitStore): Slice<VCSBranches> => s.byProject[projectId]?.branches ?? emptySlice();
}

export function selectWorktrees(connectionId: string | null, projectId: string) {
  return (state: WorktreeCacheStore): WorktreeSlice => {
    if (!connectionId) return EMPTY_WORKTREE_SLICE;
    return state.worktreesByScope[worktreeScope(connectionId, projectId)] ?? EMPTY_WORKTREE_SLICE;
  };
}

export function useGitStatus(projectId: string) {
  const slice = useGitStore(selectStatus(projectId));
  const refresh = useGitStore((s) => s.refreshStatus);
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);

  useEffect(() => {
    if (!projectId || connectionPhase !== 'connected') return;
    if (slice.data === null && !slice.loading && slice.error === null) {
      refresh(projectId);
    }
  }, [projectId, connectionPhase, refresh, slice.data, slice.loading, slice.error]);

  return {
    status: slice.data,
    loading: slice.loading,
    error: slice.error,
    reload: () => refresh(projectId),
  };
}

export function useGitBranches(projectId: string) {
  const slice = useGitStore(selectBranches(projectId));
  const refresh = useGitStore((s) => s.refreshBranches);
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);

  useEffect(() => {
    if (!projectId || connectionPhase !== 'connected') return;
    if (slice.data === null && !slice.loading && slice.error === null) {
      refresh(projectId);
    }
  }, [projectId, connectionPhase, refresh, slice.data, slice.loading, slice.error]);

  return {
    branches: slice.data,
    loading: slice.loading,
    error: slice.error,
    reload: () => refresh(projectId),
  };
}

export function selectDiff(projectId: string, filePath: string) {
  return (s: GitStore): Slice<VCSDiff> =>
    s.byProject[projectId]?.diffsByPath[filePath] ?? emptySlice();
}

export function useGitDiff(projectId: string, filePath: string) {
  const slice = useGitStore(selectDiff(projectId, filePath));
  const load = useGitStore((s) => s.loadDiff);
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);

  useEffect(() => {
    if (!projectId || !filePath || connectionPhase !== 'connected') return;
    if (slice.data === null && !slice.loading && slice.error === null) {
      load(projectId, filePath, false);
    }
  }, [projectId, filePath, connectionPhase, load, slice.data, slice.loading, slice.error]);

  return {
    diff: slice.data,
    loading: slice.loading,
    error: slice.error,
    reload: () => load(projectId, filePath, false),
    loadFull: () => load(projectId, filePath, true),
  };
}

export function useGitWorktrees(projectId: string) {
  const connectionId = useDevicesStore((state) => state.activeDeviceId);
  const connectionPhase = useDevicesStore((state) => state.connectionPhase);
  const hasHydrated = useWorktreeCacheStore((state) => state.hasHydrated);
  const slice = useWorktreeCacheStore(selectWorktrees(connectionId, projectId));
  const refresh = useGitStore((s) => s.refreshWorktrees);

  useEffect(() => {
    if (!hasHydrated || !projectId || !connectionId || connectionPhase !== 'connected') return;
    if (slice.stale && !slice.loading && slice.error === null) {
      refresh(projectId);
    }
  }, [
    hasHydrated,
    projectId,
    connectionId,
    connectionPhase,
    refresh,
    slice.stale,
    slice.loading,
    slice.error,
  ]);

  return {
    worktrees: slice.data,
    loading: slice.loading,
    error: slice.error,
    reload: () => refresh(projectId),
  };
}
