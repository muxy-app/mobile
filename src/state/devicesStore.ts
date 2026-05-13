import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { DeviceTheme, Pairing } from '@/transport';

import { newDeviceID } from './ids';

export type DeviceEntry = {
  id: string;
  label: string;
  host: string;
  port: number;
  serviceName?: string;
  pairedAt: string;
  lastConnectedAt?: string;
  pairing?: Pairing;
  needsRepair?: boolean;
};

export type ConnectionPhase =
  | 'idle'
  | 'connecting'
  | 'authenticating'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'unauthorized';

export type ThemeSource = 'device' | 'system' | 'light' | 'dark';

type State = {
  hasHydrated: boolean;
  installDeviceID: string | null;
  devices: DeviceEntry[];
  activeDeviceId: string | null;
  themeSource: ThemeSource;
  lastAppliedTheme: DeviceTheme | null;
  connectionPhase: ConnectionPhase;
  connectionError: string | null;
};

type Actions = {
  setHasHydrated: (value: boolean) => void;
  ensureInstallDeviceID: () => string;
  upsertDevice: (entry: DeviceEntry) => void;
  removeDevice: (id: string) => void;
  setActiveDevice: (id: string | null) => void;
  setNeedsRepair: (id: string, needs: boolean) => void;
  setLastConnectedAt: (id: string, isoTs: string) => void;
  setResolvedAddress: (id: string, host: string, port: number) => void;
  setPairing: (id: string, pairing: Pairing) => void;
  setConnection: (phase: ConnectionPhase, error?: string | null) => void;
  setThemeSource: (source: ThemeSource) => void;
  setLastAppliedTheme: (theme: DeviceTheme | null) => void;
};

export type DevicesStore = State & Actions;

const initialState: State = {
  hasHydrated: false,
  installDeviceID: null,
  devices: [],
  activeDeviceId: null,
  themeSource: 'device',
  lastAppliedTheme: null,
  connectionPhase: 'idle',
  connectionError: null,
};

export const useDevicesStore = create<DevicesStore>()(
  persist(
    (set, get) => ({
      ...initialState,

      setHasHydrated: (value) => set({ hasHydrated: value }),

      ensureInstallDeviceID: () => {
        const existing = get().installDeviceID;
        if (existing) return existing;
        const fresh = newDeviceID();
        set({ installDeviceID: fresh });
        return fresh;
      },

      upsertDevice: (entry) =>
        set((s) => {
          const idx = s.devices.findIndex((d) => d.id === entry.id);
          const next = idx >= 0 ? s.devices.slice() : [...s.devices, entry];
          if (idx >= 0) next[idx] = entry;
          return { devices: next };
        }),

      removeDevice: (id) =>
        set((s) => ({
          devices: s.devices.filter((d) => d.id !== id),
          activeDeviceId: s.activeDeviceId === id ? null : s.activeDeviceId,
        })),

      setActiveDevice: (id) => set({ activeDeviceId: id }),

      setNeedsRepair: (id, needs) =>
        set((s) => ({
          devices: s.devices.map((d) => (d.id === id ? { ...d, needsRepair: needs } : d)),
        })),

      setLastConnectedAt: (id, iso) =>
        set((s) => ({
          devices: s.devices.map((d) => (d.id === id ? { ...d, lastConnectedAt: iso } : d)),
        })),

      setResolvedAddress: (id, host, port) =>
        set((s) => ({
          devices: s.devices.map((d) => (d.id === id ? { ...d, host, port } : d)),
        })),

      setPairing: (id, pairing) =>
        set((s) => ({
          devices: s.devices.map((d) => (d.id === id ? { ...d, pairing } : d)),
        })),

      setConnection: (phase, error = null) => set({ connectionPhase: phase, connectionError: error }),

      setThemeSource: (source) => set({ themeSource: source }),

      setLastAppliedTheme: (theme) => set({ lastAppliedTheme: theme }),
    }),
    {
      name: 'muxy.devices.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        installDeviceID: state.installDeviceID,
        devices: state.devices,
        themeSource: state.themeSource,
        lastAppliedTheme: state.lastAppliedTheme,
      }),
      merge: (persisted, current) => ({
        ...current,
        ...(persisted as Partial<DevicesStore>),
        activeDeviceId: null,
        connectionPhase: 'idle' as ConnectionPhase,
        connectionError: null,
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    },
  ),
);

export function selectActiveDevice(state: DevicesStore): DeviceEntry | null {
  if (!state.activeDeviceId) return null;
  return state.devices.find((d) => d.id === state.activeDeviceId) ?? null;
}
