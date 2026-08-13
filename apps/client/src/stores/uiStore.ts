import { create } from 'zustand';

export type Panel =
  | 'navigator'
  | 'catalogue'
  | 'inventory'
  | 'friends'
  | 'groups'
  | 'profile'
  | 'trade'
  | 'marketplace'
  | 'achievements'
  | 'quests'
  | 'garden'
  | 'games'
  | 'rp'
  | null;

interface UiStore {
  activePanel: Panel;
  chatInputFocused: boolean;

  openPanel: (panel: Panel) => void;
  closePanel: () => void;
  togglePanel: (panel: Panel) => void;
  setChatInputFocused: (v: boolean) => void;
}

export const useUiStore = create<UiStore>((set, get) => ({
  activePanel: null,
  chatInputFocused: false,

  openPanel(panel) {
    set({ activePanel: panel });
  },

  closePanel() {
    set({ activePanel: null });
  },

  togglePanel(panel) {
    set((s) => ({ activePanel: s.activePanel === panel ? null : panel }));
  },

  setChatInputFocused(v) {
    set({ chatInputFocused: v });
  },
}));
