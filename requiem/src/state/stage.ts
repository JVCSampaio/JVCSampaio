import { create } from "zustand";

/** Where the shared video element should be drawn: the panel's slot, measured on screen. */
interface StageState {
  slot: DOMRect | null;
  setSlot(rect: DOMRect | null): void;
}

export const useStage = create<StageState>()((set) => ({
  slot: null,
  setSlot: (slot) => set({ slot }),
}));
