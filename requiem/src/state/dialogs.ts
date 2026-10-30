import { create } from "zustand";
import type { Item, Playlist } from "@/lib/types";

export type DialogRequest =
  | { type: "delete"; item: Item }
  | { type: "edit"; item: Item }
  | { type: "newPlaylist"; itemIds: string[] }
  | { type: "renamePlaylist"; playlist: Playlist }
  | { type: "deletePlaylist"; playlist: Playlist }
  | { type: "pasteList" };

interface DialogState {
  open: DialogRequest | null;
  show(request: DialogRequest): void;
  close(): void;
}

export const useDialogs = create<DialogState>()((set) => ({
  open: null,
  show: (open) => set({ open }),
  close: () => set({ open: null }),
}));

export const showDialog = (request: DialogRequest) => useDialogs.getState().show(request);
