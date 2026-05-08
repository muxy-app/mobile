import { create } from 'zustand';

import type { Workspace } from '@/transport';

import { mapAreas, mergeWorkspaceUpdate } from './workspaceTree';

export type WorkspaceFetchPhase = 'idle' | 'loading' | 'loaded' | 'error';

type State = {
  workspace: Workspace | null;
  fetchPhase: WorkspaceFetchPhase;
  fetchError: string | null;
};

type Actions = {
  setWorkspace: (workspace: Workspace | null) => void;
  applyWorkspaceUpdate: (workspace: Workspace) => void;
  setFetchPhase: (phase: WorkspaceFetchPhase, error?: string | null) => void;
  selectTabLocal: (areaId: string, tabId: string) => void;
  clear: () => void;
};

export type WorkspaceStore = State & Actions;

const initialState: State = {
  workspace: null,
  fetchPhase: 'idle',
  fetchError: null,
};

export const useWorkspaceStore = create<WorkspaceStore>((set, get) => ({
  ...initialState,
  setWorkspace: (workspace) => set({ workspace }),

  applyWorkspaceUpdate: (next) => {
    const prev = get().workspace;
    set({ workspace: prev ? mergeWorkspaceUpdate(prev, next) : next });
  },

  setFetchPhase: (phase, error = null) => set({ fetchPhase: phase, fetchError: error }),

  selectTabLocal: (areaId, tabId) => {
    const ws = get().workspace;
    if (!ws) return;
    const root = mapAreas(ws.root, (area) =>
      area.id === areaId ? { ...area, activeTabID: tabId } : area,
    );
    set({ workspace: { ...ws, root, focusedAreaID: areaId } });
  },

  clear: () => set(initialState),
}));
