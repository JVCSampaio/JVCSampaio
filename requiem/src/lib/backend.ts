import type {
  AccountTest,
  EngineEvent,
  Entry,
  Item,
  Kind,
  Lyrics,
  Overview,
  Play,
  Playlist,
  Priority,
  Resolved,
  Settings,
  Stats,
  SyncStatus,
  ToolsReport,
  ToolsStatus,
  Update,
} from "./types";

/** Everything the interface asks the engine. The Tauri build talks to Rust; a browser preview
 *  (npm run dev, screenshots) uses an in-memory stand-in with the same contract. */
export interface Backend {
  overview(): Promise<Overview>;
  toolsStatus(): Promise<ToolsReport>;
  toolsInstall(): Promise<void>;
  toolsUpdate(): Promise<ToolsStatus>;
  library(): Promise<Item[]>;
  queue(): Promise<Item[]>;
  playlists(): Promise<Playlist[]>;
  /** Counts a play; the answer identifies it for playSeconds. */
  itemPlayed(id: string): Promise<number>;
  playSeconds(play: number, seconds: number): Promise<void>;
  itemLike(id: string, liked: boolean): Promise<void>;
  itemResume(id: string, seconds: number | null): Promise<void>;
  itemEdit(id: string, title: string, artist: string, album: string | null): Promise<void>;
  history(limit: number): Promise<Play[]>;
  stats(since: number): Promise<Stats>;
  itemDelete(id: string, deleteFiles: boolean): Promise<void>;
  itemReveal(id: string): Promise<void>;
  search(query: string, kind: Kind): Promise<Entry[]>;
  resolve(text: string): Promise<Resolved>;
  /** `priority`: "now" for a song the person is waiting to hear, "soon" for the next songs of a radio. */
  download(entry: Entry, kind: Kind, source?: string, priority?: Priority): Promise<Item>;
  downloadMany(entries: Entry[], kind: Kind, playlist: string | null): Promise<number | null>;
  /** Songs like `seed` for a radio, leaving out the ones it already played. */
  radio(seed: string, played: string[]): Promise<Entry[]>;
  downloadRetry(id: string): Promise<void>;
  downloadCancel(id: string): Promise<void>;
  downloadsClear(): Promise<number>;
  playlistCreate(name: string, itemIds: string[]): Promise<number>;
  playlistRename(id: number, name: string): Promise<void>;
  playlistDelete(id: number): Promise<void>;
  playlistAdd(id: number, itemIds: string[]): Promise<void>;
  playlistRemove(id: number, itemId: string): Promise<void>;
  playlistReorder(id: number, itemIds: string[]): Promise<void>;
  settingsGet(): Promise<Settings>;
  settingsSet(settings: Settings): Promise<Settings>;
  syncStatus(): Promise<SyncStatus>;
  syncNow(): Promise<void>;
  accountLogin(): Promise<void>;
  accountTest(): Promise<AccountTest>;
  accountImport(path: string): Promise<void>;
  accountDisconnect(): Promise<void>;
  lyrics(id: string): Promise<Lyrics | null>;
  openLibraryFolder(): Promise<void>;
  libraryRescan(): Promise<void>;
  updateCheck(): Promise<Update | null>;
  updateOpen(): Promise<void>;
  pickFolder(current: string): Promise<string | null>;
  pickCookieFile(): Promise<string | null>;
  tourMark(name: string): Promise<boolean>;
  onEvent(handler: (event: EngineEvent) => void): () => void;
  onTray(handler: (action: string) => void): () => void;
  setFullscreen(on: boolean): Promise<void>;
}

export const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

async function tauriBackend(): Promise<Backend> {
  const { invoke } = await import("@tauri-apps/api/core");
  const { listen } = await import("@tauri-apps/api/event");
  const { open } = await import("@tauri-apps/plugin-dialog");
  const { getCurrentWindow } = await import("@tauri-apps/api/window");

  const on = <T,>(name: string, handler: (payload: T) => void) => {
    let stop: (() => void) | null = null;
    let cancelled = false;
    listen<T>(name, (e) => handler(e.payload)).then((unlisten) => {
      if (cancelled) unlisten();
      else stop = unlisten;
    });
    return () => {
      cancelled = true;
      stop?.();
    };
  };

  return {
    overview: () => invoke("overview"),
    toolsStatus: () => invoke("tools_status"),
    toolsInstall: () => invoke("tools_install"),
    toolsUpdate: () => invoke("tools_update"),
    library: () => invoke("library"),
    queue: () => invoke("queue"),
    playlists: () => invoke("playlists"),
    itemPlayed: (id) => invoke("item_played", { id }),
    playSeconds: (play, seconds) => invoke("play_seconds", { play, seconds }),
    itemLike: (id, liked) => invoke("item_like", { id, liked }),
    itemResume: (id, seconds) => invoke("item_resume", { id, seconds }),
    itemEdit: (id, title, artist, album) => invoke("item_edit", { id, title, artist, album }),
    history: (limit) => invoke("history", { limit }),
    stats: (since) => invoke("stats", { since }),
    itemDelete: (id, deleteFiles) => invoke("item_delete", { id, deleteFiles }),
    itemReveal: (id) => invoke("item_reveal", { id }),
    search: (query, kind) => invoke("search", { query, kind }),
    resolve: (text) => invoke("resolve", { text }),
    download: (entry, kind, source, priority) => invoke("download", { entry, kind, source: source ?? null, priority: priority ?? null }),
    downloadMany: (entries, kind, playlist) => invoke("download_many", { entries, kind, playlist }),
    radio: (seed, played) => invoke("radio", { seed, played }),
    downloadRetry: (id) => invoke("download_retry", { id }),
    downloadCancel: (id) => invoke("download_cancel", { id }),
    downloadsClear: () => invoke("downloads_clear"),
    playlistCreate: (name, itemIds) => invoke("playlist_create", { name, itemIds }),
    playlistRename: (id, name) => invoke("playlist_rename", { id, name }),
    playlistDelete: (id) => invoke("playlist_delete", { id }),
    playlistAdd: (id, itemIds) => invoke("playlist_add", { id, itemIds }),
    playlistRemove: (id, itemId) => invoke("playlist_remove", { id, itemId }),
    playlistReorder: (id, itemIds) => invoke("playlist_reorder", { id, itemIds }),
    settingsGet: () => invoke("settings_get"),
    settingsSet: (settings) => invoke("settings_set", { settings }),
    syncStatus: () => invoke("sync_status"),
    syncNow: () => invoke("sync_now"),
    accountLogin: () => invoke("account_login"),
    accountTest: () => invoke("account_test"),
    accountImport: (path) => invoke("account_import", { path }),
    accountDisconnect: () => invoke("account_disconnect"),
    lyrics: (id) => invoke("lyrics", { id }),
    openLibraryFolder: () => invoke("open_library_folder"),
    libraryRescan: () => invoke("library_rescan"),
    updateCheck: () => invoke("update_check"),
    updateOpen: () => invoke("update_open"),
    pickFolder: async (current) => {
      const picked = await open({ directory: true, defaultPath: current || undefined, title: "Onde guardar a biblioteca" });
      return typeof picked === "string" ? picked : null;
    },
    pickCookieFile: async () => {
      const picked = await open({ multiple: false, title: "Arquivo cookies.txt do YouTube", filters: [{ name: "cookies.txt", extensions: ["txt"] }] });
      return typeof picked === "string" ? picked : null;
    },
    tourMark: (name) => invoke("tour_mark", { name }),
    onEvent: (handler) => on<EngineEvent>("engine", handler),
    onTray: (handler) => on<string>("tray", handler),
    setFullscreen: (on) => getCurrentWindow().setFullscreen(on),
  };
}

let instance: Promise<Backend> | null = null;

export function backend(): Promise<Backend> {
  if (!instance) {
    instance = isTauri ? tauriBackend() : import("./mock").then((m) => m.createMockBackend());
  }
  return instance;
}
