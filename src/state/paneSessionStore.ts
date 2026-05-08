import { create } from 'zustand';

export type PaneSession =
  | { kind: 'idle' }
  | { kind: 'taking-over'; paneId: string }
  | { kind: 'streaming'; paneId: string }
  | { kind: 'lost'; paneId: string; takenBy?: string }
  | { kind: 'failed'; paneId: string; reason: string };

type State = {
  session: PaneSession;
};

type Actions = {
  setSession: (session: PaneSession) => void;
};

export type PaneSessionStore = State & Actions;

const initialState: State = { session: { kind: 'idle' } };

export const usePaneSessionStore = create<PaneSessionStore>((set) => ({
  ...initialState,
  setSession: (session) => set({ session }),
}));
