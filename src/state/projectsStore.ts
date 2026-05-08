import { create } from 'zustand';

import type { Project } from '@/transport';

export type ProjectsFetchPhase = 'idle' | 'loading' | 'loaded' | 'error';

type State = {
  projects: Project[];
  logos: Record<string, string>;
  fetchPhase: ProjectsFetchPhase;
  fetchError: string | null;
};

type Actions = {
  setProjects: (projects: Project[]) => void;
  setLogo: (projectId: string, dataUri: string) => void;
  setFetchPhase: (phase: ProjectsFetchPhase, error?: string | null) => void;
  clear: () => void;
};

export type ProjectsStore = State & Actions;

const initialState: State = {
  projects: [],
  logos: {},
  fetchPhase: 'idle',
  fetchError: null,
};

export const useProjectsStore = create<ProjectsStore>((set) => ({
  ...initialState,

  setProjects: (projects) => set({ projects: [...projects].sort((a, b) => a.sortOrder - b.sortOrder) }),

  setLogo: (projectId, dataUri) =>
    set((s) => ({ logos: { ...s.logos, [projectId]: dataUri } })),

  setFetchPhase: (phase, error = null) => set({ fetchPhase: phase, fetchError: error }),

  clear: () => set(initialState),
}));
