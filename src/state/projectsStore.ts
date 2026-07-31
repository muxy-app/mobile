import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { Project } from '@/transport';

export type ProjectsFetchPhase = 'idle' | 'loading' | 'loaded' | 'error';

type State = {
  hasHydrated: boolean;
  projects: Project[];
  logos: Record<string, string>;
  selectedWorkspaceIDs: Record<string, string>;
  fetchPhase: ProjectsFetchPhase;
  fetchError: string | null;
};

type Actions = {
  setHasHydrated: (value: boolean) => void;
  setProjects: (projects: Project[], connectionID: string) => void;
  setLogo: (projectId: string, dataUri: string) => void;
  setSelectedWorkspaceID: (connectionID: string, workspaceID: string | null) => void;
  setFetchPhase: (phase: ProjectsFetchPhase, error?: string | null) => void;
  clear: () => void;
};

export type ProjectsStore = State & Actions;

const transientInitialState = {
  projects: [],
  logos: {},
  fetchPhase: 'idle',
  fetchError: null,
} satisfies Omit<State, 'hasHydrated' | 'selectedWorkspaceIDs'>;

export const useProjectsStore = create<ProjectsStore>()(
  persist(
    (set) => ({
      ...transientInitialState,
      hasHydrated: false,
      selectedWorkspaceIDs: {},

      setHasHydrated: (value) => set({ hasHydrated: value }),

      setProjects: (projects, connectionID) =>
        set((state) => {
          const sortedProjects = [...projects].sort((a, b) => a.sortOrder - b.sortOrder);
          const selectedWorkspaceID = state.selectedWorkspaceIDs[connectionID];

          if (!selectedWorkspaceID) return { projects: sortedProjects };
          if (projects.length === 0) return { projects: sortedProjects };
          if (projects.some((project) => project.workspaceID === selectedWorkspaceID)) {
            return { projects: sortedProjects };
          }

          const { [connectionID]: _removed, ...selectedWorkspaceIDs } = state.selectedWorkspaceIDs;
          return { projects: sortedProjects, selectedWorkspaceIDs };
        }),

      setLogo: (projectId, dataUri) =>
        set((state) => ({ logos: { ...state.logos, [projectId]: dataUri } })),

      setSelectedWorkspaceID: (connectionID, workspaceID) =>
        set((state) => {
          if (workspaceID) {
            if (state.selectedWorkspaceIDs[connectionID] === workspaceID) return state;
            return {
              selectedWorkspaceIDs: {
                ...state.selectedWorkspaceIDs,
                [connectionID]: workspaceID,
              },
            };
          }

          if (!(connectionID in state.selectedWorkspaceIDs)) return state;
          const { [connectionID]: _removed, ...selectedWorkspaceIDs } = state.selectedWorkspaceIDs;
          return { selectedWorkspaceIDs };
        }),

      setFetchPhase: (phase, error = null) => set({ fetchPhase: phase, fetchError: error }),

      clear: () => set(transientInitialState),
    }),
    {
      name: 'muxy.projects.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        selectedWorkspaceIDs: state.selectedWorkspaceIDs,
      }),
      merge: (persisted, current) => ({
        ...current,
        selectedWorkspaceIDs: readPersistedWorkspaceSelections(persisted),
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    },
  ),
);

function readPersistedWorkspaceSelections(persisted: unknown): Record<string, string> {
  if (!persisted || typeof persisted !== 'object') return {};
  if (!('selectedWorkspaceIDs' in persisted)) return {};
  if (!persisted.selectedWorkspaceIDs || typeof persisted.selectedWorkspaceIDs !== 'object') {
    return {};
  }
  if (Array.isArray(persisted.selectedWorkspaceIDs)) return {};

  return Object.fromEntries(
    Object.entries(persisted.selectedWorkspaceIDs).filter(
      ([connectionID, workspaceID]) => isUUID(connectionID) && isUUID(workspaceID),
    ),
  );
}

function isUUID(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
  );
}
