import { create } from 'zustand';
import { AssetLoader, isAssetEra, type AssetEra } from '@/renderer/AssetLoader';

const STORAGE_KEY = 'habnut.era';

/**
 * The visual era the player is viewing the hotel in.
 *
 * This is a rendering choice, not a world choice: switching era reloads the
 * artwork and leaves the player standing in the same room, on the same world,
 * with the same people around them.
 */
interface EraStore {
  era: AssetEra;
  /** True while a pack is loading, so the UI can show progress. */
  switching: boolean;
  setEra: (era: AssetEra) => Promise<void>;
  toggle: () => Promise<void>;
}

/** Reads the saved preference, falling back to modern. */
function storedEra(): AssetEra {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && isAssetEra(saved)) return saved;
  } catch {
    // Private browsing and blocked site data both throw here; the default is fine.
  }
  return 'modern';
}

function persistEra(era: AssetEra): void {
  try {
    localStorage.setItem(STORAGE_KEY, era);
  } catch {
    // A preference that cannot be saved is still applied for this session.
  }
}

export const useEraStore = create<EraStore>((set, get) => ({
  era: storedEra(),
  switching: false,

  async setEra(era) {
    if (era === get().era && !get().switching) return;

    set({ switching: true });
    try {
      await AssetLoader.setEra(era);
      persistEra(era);
      set({ era, switching: false });
    } catch {
      // Leave the previous era in place rather than stranding the player with
      // no artwork at all.
      set({ switching: false });
    }
  },

  async toggle() {
    await get().setEra(get().era === 'modern' ? 'classic' : 'modern');
  },
}));

/**
 * Applies the saved era to the asset loader at start-up.
 *
 * Called once before the room is first rendered so the player's choice is in
 * effect from the first frame rather than flashing the default era first.
 */
export async function initEra(): Promise<void> {
  await AssetLoader.setEra(useEraStore.getState().era);
}
