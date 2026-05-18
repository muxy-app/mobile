import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

type State = {
  hasHydrated: boolean;
  hasOnboarded: boolean;
  useNerdFont: boolean;
  autoFocusTerminal: boolean;
  demoMode: boolean;
};

type Actions = {
  setHasHydrated: (value: boolean) => void;
  setOnboarded: (value: boolean) => void;
  setUseNerdFont: (value: boolean) => void;
  setAutoFocusTerminal: (value: boolean) => void;
  setDemoMode: (value: boolean) => void;
};

export type SettingsStore = State & Actions;

export const useSettingsStore = create<SettingsStore>()(
  persist(
    (set) => ({
      hasHydrated: false,
      hasOnboarded: false,
      useNerdFont: true,
      autoFocusTerminal: false,
      demoMode: false,
      setHasHydrated: (value) => set({ hasHydrated: value }),
      setOnboarded: (value) => set({ hasOnboarded: value }),
      setUseNerdFont: (value) => set({ useNerdFont: value }),
      setAutoFocusTerminal: (value) => set({ autoFocusTerminal: value }),
      setDemoMode: (value) => set({ demoMode: value }),
    }),
    {
      name: 'muxy.settings.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        useNerdFont: state.useNerdFont,
        autoFocusTerminal: state.autoFocusTerminal,
        hasOnboarded: state.hasOnboarded,
        demoMode: state.demoMode,
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    },
  ),
);
