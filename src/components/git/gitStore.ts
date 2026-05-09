import { useEffect } from 'react';
import { create } from 'zustand';

import { client, useDevicesStore } from '@/state';
import type {
  VCSBranches,
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
};

export type GitStore = State & Actions;

const EMPTY_SLICE: Slice<never> = { data: null, loading: false, error: null };

const emptySlice = <T>(): Slice<T> => EMPTY_SLICE as Slice<T>;

const emptyProject = (): ProjectGitState => ({
  status: emptySlice<VCSStatus>(),
  branches: emptySlice<VCSBranches>(),
  worktrees: emptySlice<Worktree[]>(),
});

type SliceKey = keyof ProjectGitState;

type SlicePatch = { data?: unknown; loading?: boolean; error?: string | null };

function patchSlice(
  state: State,
  projectId: string,
  key: SliceKey,
  patch: SlicePatch,
): State {
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

  const refreshWorktrees = (projectId: string) =>
    runFetch(projectId, 'worktrees', async () => {
      const res = await client.request('listWorktrees', {
        type: 'listWorktrees',
        value: { projectID: projectId },
      });
      return res.value;
    });

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
      await refreshStatus(projectId);
    },

    switchBranch: async (projectId, branch) => {
      await client.request('vcsSwitchBranch', {
        type: 'vcsSwitchBranch',
        value: { projectID: projectId, branch },
      });
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
    },
  };
});

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

export function useGitWorktrees(projectId: string) {
  const slice = useGitStore(selectWorktrees(projectId));
  const refresh = useGitStore((s) => s.refreshWorktrees);
  const connectionPhase = useDevicesStore((s) => s.connectionPhase);

  useEffect(() => {
    if (!projectId || connectionPhase !== 'connected') return;
    if (slice.data === null && !slice.loading && slice.error === null) {
      refresh(projectId);
    }
  }, [projectId, connectionPhase, refresh, slice.data, slice.loading, slice.error]);

  return {
    worktrees: slice.data,
    loading: slice.loading,
    error: slice.error,
    reload: () => refresh(projectId),
  };
}
