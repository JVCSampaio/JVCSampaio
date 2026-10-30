import { create } from "zustand";
import { backend } from "@/lib/backend";
import type { EngineEvent, InstallStep, Item, Overview, Playlist, Progress, Settings, SyncStatus, ToolsStatus } from "@/lib/types";
import { toast } from "./toasts";

interface ToolsState {
  status: ToolsStatus | null;
  installing: boolean;
  step: InstallStep | null;
  error: string | null;
}

interface LibraryState {
  ready: boolean;
  loadError: string | null;
  overview: Overview | null;
  /** Every item the engine knows: the library and the download queue, by id. */
  items: Record<string, Item>;
  playlists: Playlist[];
  progress: Record<string, Progress>;
  sync: SyncStatus | null;
  tools: ToolsState;
  settings: Settings | null;
  init(): Promise<void>;
  reload(): Promise<void>;
  reloadPlaylists(): Promise<void>;
  saveSettings(next: Settings): Promise<void>;
}

export const useLibrary = create<LibraryState>()((set, get) => ({
  ready: false,
  loadError: null,
  overview: null,
  items: {},
  playlists: [],
  progress: {},
  sync: null,
  tools: { status: null, installing: false, step: null, error: null },
  settings: null,

  async init() {
    const api = await backend();
    api.onEvent(handleEvent);
    try {
      const [overview, settings, sync, tools] = await Promise.all([
        api.overview(),
        api.settingsGet(),
        api.syncStatus(),
        api.toolsStatus(),
      ]);
      set({ overview, settings, sync, tools });
      await get().reload();
      set({ ready: true });
    } catch (e) {
      set({ loadError: String(e), ready: true });
    }
  },

  async reload() {
    const api = await backend();
    const [library, queue, playlists] = await Promise.all([api.library(), api.queue(), api.playlists()]);
    const items: Record<string, Item> = {};
    for (const item of [...queue, ...library]) items[item.id] = item;
    set({ items, playlists });
  },

  async reloadPlaylists() {
    const api = await backend();
    set({ playlists: await api.playlists() });
  },

  async saveSettings(next) {
    const api = await backend();
    try {
      const saved = await api.settingsSet(next);
      set({ settings: saved });
    } catch (e) {
      toast(String(e), "error");
    }
  },
}));

function handleEvent(event: EngineEvent) {
  const set = useLibrary.setState;
  const get = useLibrary.getState;
  switch (event.type) {
    case "item": {
      const progress = { ...get().progress };
      if (event.item.status !== "downloading") delete progress[event.item.id];
      set({ items: { ...get().items, [event.item.id]: event.item }, progress });
      break;
    }
    case "progress": {
      const { id, fraction, speed, eta, stage } = event;
      set({ progress: { ...get().progress, [id]: { fraction, speed, eta, stage } } });
      break;
    }
    case "removed": {
      const items = { ...get().items };
      delete items[event.id];
      set({ items });
      break;
    }
    case "library":
      void get().reload();
      break;
    case "playlists":
      void get().reloadPlaylists();
      break;
    case "sync":
      set({ sync: event.status });
      break;
    case "tools":
      set({ tools: { status: event.installing ? get().tools.status : event.status, installing: event.installing, step: event.step, error: event.error } });
      break;
    case "notice":
      toast(event.message);
      break;
    case "settings":
      set({ settings: event.settings });
      break;
  }
}

// Derived lists. Components memoize them on the items record.

export function libraryItems(items: Record<string, Item>): Item[] {
  return Object.values(items)
    .filter((i) => i.status === "done")
    .sort((a, b) => (b.downloadedAt ?? 0) - (a.downloadedAt ?? 0));
}

export function pendingItems(items: Record<string, Item>): Item[] {
  return Object.values(items).filter((i) => i.status === "queued" || i.status === "downloading" || i.status === "failed" || i.status === "skipped");
}

export function isAccountConnected(settings: Settings | null): boolean {
  return !!settings && settings.account.mode !== "none";
}
