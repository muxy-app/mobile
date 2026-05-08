import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

type State = {
  hasHydrated: boolean;
  hasOnboarded: boolean;
  useNerdFont: boolean;
};

type Actions = {
  setHasHydrated: (value: boolean) => void;
  setOnboarded: (value: boolean) => void;
  setUseNerdFont: (value: boolean) => void;
};

export type SettingsStore = State & Actions;

export const useSettingsStore = create<SettingsStore>()(
  persist(
    (set) => ({
      hasHydrated: false,
      hasOnboarded: false,
      useNerdFont: true,
      setHasHydrated: (value) => set({ hasHydrated: value }),
      setOnboarded: (value) => set({ hasOnboarded: value }),
      setUseNerdFont: (value) => set({ useNerdFont: value }),
    }),
    {
      name: 'muxy.settings.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        useNerdFont: state.useNerdFont,
        hasOnboarded: state.hasOnboarded,
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    },
  ),
);
