import { useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import { client, useDevicesStore } from '@/state';
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
  worktrees: Slice<Worktree[]>;
  diffsByPath: Record<string, Slice<VCSDiff>>;
};

type State = {
  hasHydrated: boolean;
  byProject: Record<string, ProjectGitState>;
};

type Actions = {
  setHasHydrated: (value: boolean) => void;
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

type PersistedGitState = Pick<State, 'byProject'>;

const EMPTY_SLICE: Slice<never> = { data: null, loading: false, error: null };

const emptySlice = <T>(): Slice<T> => EMPTY_SLICE as Slice<T>;

const emptyProject = (): ProjectGitState => ({
  status: emptySlice<VCSStatus>(),
  branches: emptySlice<VCSBranches>(),
  worktrees: emptySlice<Worktree[]>(),
  diffsByPath: {},
});

type SliceKey = Exclude<keyof ProjectGitState, 'diffsByPath'>;

type SlicePatch = { data?: unknown; loading?: boolean; error?: string | null };

function patchSlice(
  state: State,
  projectId: string,
  key: SliceKey,
  patch: SlicePatch,
): State {
  const project = state.byProject[projectId] ?? emptyProject();
  return {
    hasHydrated: state.hasHydrated,
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
): State {
  const project = state.byProject[projectId] ?? emptyProject();
  const existing = project.diffsByPath[filePath] ?? emptySlice<VCSDiff>();
  return {
    hasHydrated: state.hasHydrated,
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

function clearDiffs(state: State, projectId: string): State {
  const project = state.byProject[projectId];
  if (!project || Object.keys(project.diffsByPath).length === 0) return state;
  return {
    hasHydrated: state.hasHydrated,
    byProject: {
      ...state.byProject,
      [projectId]: { ...project, diffsByPath: {} },
    },
  };
}

function cachedWorktreeProjects(byProject: Record<string, ProjectGitState>): Record<string, ProjectGitState> {
  return Object.fromEntries(
    Object.entries(byProject)
      .filter(([, project]) => project.worktrees.data !== null)
      .map(([projectId, project]) => [
        projectId,
        {
          ...emptyProject(),
          worktrees: {
            data: project.worktrees.data,
            loading: false,
            error: null,
          },
        },
      ]),
  );
}

function mergeCachedWorktreeProjects(
  cached: Record<string, ProjectGitState>,
  current: Record<string, ProjectGitState>,
): Record<string, ProjectGitState> {
  return Object.entries(cached).reduce<Record<string, ProjectGitState>>((next, [projectId, project]) => {
    const currentProject = next[projectId] ?? emptyProject();
    next[projectId] = {
      ...currentProject,
      worktrees: {
        ...currentProject.worktrees,
        data: project.worktrees.data,
        loading: false,
        error: null,
      },
    };
    return next;
  }, { ...current });
}

export const useGitStore = create<GitStore>()(
  persist<GitStore, [], [], PersistedGitState>(
    (set) => {
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

      const refreshWorktrees = (projectId: string) =>
        runFetch(projectId, 'worktrees', async () => {
          const res = await client.request('listWorktrees', {
            type: 'listWorktrees',
            value: { projectID: projectId },
          });
          return res.value;
        });

      return {
        hasHydrated: false,
        byProject: {},

        setHasHydrated: (value) => set({ hasHydrated: value }),

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
          const res = await client.request('vcsAddWorktree', {
            type: 'vcsAddWorktree',
            value: {
              projectID: projectId,
              name: input.name,
              branch: input.branch,
              createBranch: input.createBranch,
            },
          });
          set((s) =>
            patchSlice(s, projectId, 'worktrees', { data: res.value, loading: false, error: null }),
          );
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
    },
    {
      name: 'muxy.git.worktrees.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({ byProject: cachedWorktreeProjects(state.byProject) }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
      merge: (persisted, current) => ({
        ...current,
        byProject: mergeCachedWorktreeProjects(
          (persisted as PersistedGitState | undefined)?.byProject ?? {},
          current.byProject,
        ),
      }),
    },
  ),
);

export function selectStatus(projectId: string) {
  return (s: GitStore): Slice<VCSStatus> => s.byProject[projectId]?.status ?? emptySlice();
}

export function selectBranches(projectId: string) {
  return (s: GitStore): Slice<VCSBranches> => s.byProject[projectId]?.branches ?? emptySlice();
}

export function selectWorktrees(projectId: string) {
  return (s: GitStore): Slice<Worktree[]> => s.byProject[projectId]?.worktrees ?? emptySlice();
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
  const slice = useGitStore(selectWorktrees(projectId));
  const refresh = useGitStore((s) => s.refreshWorktrees);
  const hasHydrated = useGitStore((s) => s.hasHydrated);
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);

  useEffect(() => {
    if (!hasHydrated) return;
    if (!projectId || connectionPhase !== 'connected') return;
    if (slice.data === null && !slice.loading && slice.error === null) {
      refresh(projectId);
    }
  }, [projectId, connectionPhase, refresh, hasHydrated, slice.data, slice.loading, slice.error]);

  return {
    worktrees: slice.data,
    loading: slice.loading,
    error: slice.error,
    reload: () => refresh(projectId),
  };
}
