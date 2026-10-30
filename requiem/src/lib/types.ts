// Mirrors of the Rust engine's serde types (requiem-core).

export type Kind = "music" | "video";
export type ItemStatus = "queued" | "downloading" | "done" | "failed" | "skipped" | "removed";

export interface Item {
  id: string;
  kind: Kind | "auto";
  title: string;
  artist: string;
  album: string | null;
  channel: string;
  duration: number | null;
  source: string;
  status: ItemStatus;
  error: string | null;
  filePath: string | null;
  thumbPath: string | null;
  thumbnailUrl: string | null;
  fileSize: number | null;
  width: number | null;
  height: number | null;
  addedAt: number;
  seenAt: number | null;
  downloadedAt: number | null;
  playCount: number;
  lastPlayedAt: number | null;
}

export interface Entry {
  id: string;
  title: string;
  artist: string;
  channel: string;
  duration: number | null;
  thumbnail: string;
  isShort: boolean;
}

export interface Playlist {
  id: number;
  name: string;
  itemIds: string[];
  createdAt: number;
}

export type Account =
  | { mode: "none" }
  | { mode: "app" }
  | { mode: "browser"; browser: string }
  | { mode: "file" };

export interface Settings {
  libraryDir: string;
  account: Account;
  autoSync: boolean;
  syncIntervalMinutes: number;
  syncHistory: boolean;
  syncLikes: boolean;
  syncMusicLikes: boolean;
  firstSyncLimit: number;
  downloadMusic: boolean;
  downloadVideos: boolean;
  downloadShorts: boolean;
  maxMusicMinutes: number;
  maxVideoMinutes: number;
  videoHeight: number;
  audioFormat: "m4a" | "mp3";
  closeToTray: boolean;
  launchAtLogin: boolean;
}

export interface SyncStatus {
  running: boolean;
  lastRunAt: number | null;
  lastFound: number;
  lastError: string | null;
  nextRunAt: number | null;
}

export interface ToolsStatus {
  ytdlp: string | null;
  deno: string | null;
  ffmpeg: string | null;
  ready: boolean;
}

export interface ToolsReport {
  status: ToolsStatus;
  installing: boolean;
  step: InstallStep | null;
  error: string | null;
}

export interface InstallStep {
  tool: string;
  label: string;
  index: number;
  total: number;
  fraction: number | null;
}

export interface Progress {
  fraction: number | null;
  speed: number | null;
  eta: number | null;
  stage: string;
}

export type EngineEvent =
  | { type: "item"; item: Item }
  | ({ type: "progress"; id: string } & Progress)
  | { type: "removed"; id: string }
  | { type: "library" }
  | { type: "playlists" }
  | { type: "sync"; status: SyncStatus }
  | ({ type: "tools" } & ToolsReport)
  | { type: "notice"; message: string }
  | { type: "settings"; settings: Settings };

export type Resolved =
  | { type: "video"; entry: Entry }
  | { type: "playlist"; title: string; entries: Entry[] }
  | { type: "query"; text: string };

export interface AccountTest {
  ok: boolean;
  message: string;
  sample: Entry[];
}

export interface Overview {
  version: string;
  mediaBase: string;
  dataDir: string;
  libraryDir: string;
  platform: string;
}

export interface LyricLine {
  ms: number;
  text: string;
}

export interface Lyrics {
  lines: LyricLine[];
  plain: string | null;
  instrumental: boolean;
}
