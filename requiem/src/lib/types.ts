// Mirrors of the Rust engine's serde types (requiem-core).

export type Kind = "music" | "video";
/** How soon a download is needed: "now" for a song the person is waiting to hear, "soon" for
 *  the next songs of a radio. Plain downloads have none. */
export type Priority = "now" | "soon";
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
  likedAt: number | null;
  trackNumber: number | null;
  discNumber: number | null;
  year: number | null;
  /** Integrated loudness (LUFS) and sample peak (dBFS), once measured. */
  loudness: number | null;
  peak: number | null;
  /** Where a long item was left, in seconds. */
  resumeAt: number | null;
}

export interface Play {
  id: number;
  itemId: string;
  playedAt: number;
  seconds: number;
}

/** The Retrospectiva of a period. Top lists are [id or artist, plays, seconds]. */
export interface Stats {
  plays: number;
  seconds: number;
  songs: number;
  artists: number;
  topItems: [string, number, number][];
  topArtists: [string, number, number][];
  topHour: number | null;
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

/** How the player sounds; kept by the engine with the other settings. */
export interface AudioSettings {
  /** Even volume between songs, from each file's measured loudness. */
  normalize: boolean;
  /** "album" keeps the differences between the songs of an album. */
  normalizeBy: "track" | "album";
  eqOn: boolean;
  /** A preset's id, or "custom". */
  eqPreset: string;
  /** Ten band gains in dB, 31 Hz to 16 kHz. */
  eqGains: number[];
  eqPreamp: number;
  /** 0 plays songs one after the other. */
  crossfadeSeconds: number;
  /** Songs that follow each other on an album play with no gap. */
  gapless: boolean;
}

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
  /** Folders read into the library. null: the computer's Music folder. */
  musicFolders: string[] | null;
  /** When the queue runs out, similar songs keep playing. */
  autoplay: boolean;
  audio: AudioSettings;
}

export interface Update {
  version: string;
  url: string;
}

export interface LocalStatus {
  running: boolean;
  found: number;
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
  | { type: "settings"; settings: Settings }
  | ({ type: "local" } & LocalStatus)
  | ({ type: "update" } & Update)
  | ({ type: "import" } & ImportStatus);

export type Resolved =
  | { type: "video"; entry: Entry }
  | { type: "playlist"; title: string; entries: Entry[] }
  | { type: "spotify"; title: string; tracks: Track[] }
  | { type: "query"; text: string };

/** A song as Spotify (or a pasted list) names it. */
export interface Track {
  title: string;
  /** All artists, comma separated. */
  artist: string;
  duration: number | null;
}

/** A list brought from Spotify or pasted, while its songs are found on YouTube. */
export interface ImportStatus {
  id: string;
  title: string;
  total: number;
  done: number;
  found: number;
  /** Songs with no good enough match. */
  missing: Track[];
  finished: boolean;
  playlistId: number | null;
  error: string | null;
}

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
  localFolders: string[];
  local: LocalStatus;
  update: Update | null;
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
