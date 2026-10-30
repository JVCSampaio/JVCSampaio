import { create } from "zustand";
import { backend, isTauri } from "@/lib/backend";
import { key } from "@/lib/format";
import { gainFor } from "@/lib/loudness";
import type { Entry, Item, Kind, Settings } from "@/lib/types";
import { chainState, decks, fadeTo, installAudio, level, rest, setEq, setFade, setMaster, setTrackGain, toPlain, useAudio, wake, type Deck } from "./audio";
import { useLibrary } from "./library";
import { toast } from "./toasts";

export type Repeat = "off" | "all" | "one";
export type Panel = "lyrics" | "queue" | null;
export type Sleep = { at: number } | { endOfItem: true } | null;

interface PlayerState {
  /** Item ids in play order. */
  queue: string[];
  /** The order before shuffle, to restore it. */
  original: string[] | null;
  index: number;
  playing: boolean;
  buffering: boolean;
  position: number;
  duration: number;
  volume: number;
  muted: boolean;
  shuffle: boolean;
  repeat: Repeat;
  panel: Panel;
  cinema: boolean;
  /** A search result asked to play before it was downloaded: it downloads ahead of everything
   *  and starts by itself when the file lands. */
  pending: { entry: Entry; kind: Kind } | null;
  /** The last item finished and nothing came after it. */
  ended: boolean;
  /** Playback speed, 1 for normal. Pitch stays where it is. */
  speed: number;
  /** The sleep timer: pause at a time, or when the item playing ends. */
  sleep: Sleep;
}

const STORAGE = "requiem.player.v1";

function restored(): Partial<PlayerState> {
  try {
    const raw = localStorage.getItem(STORAGE);
    if (!raw) return {};
    const saved = JSON.parse(raw);
    return {
      queue: Array.isArray(saved.queue) ? saved.queue : [],
      index: typeof saved.index === "number" ? saved.index : 0,
      position: typeof saved.position === "number" ? saved.position : 0,
      volume: typeof saved.volume === "number" ? saved.volume : 0.8,
      muted: !!saved.muted,
      shuffle: !!saved.shuffle,
      repeat: saved.repeat === "all" || saved.repeat === "one" ? saved.repeat : "off",
    };
  } catch {
    return {};
  }
}

export const usePlayer = create<PlayerState>()(() => ({
  queue: [],
  original: null,
  index: 0,
  playing: false,
  buffering: false,
  position: 0,
  duration: 0,
  volume: 0.8,
  muted: false,
  shuffle: false,
  repeat: "off",
  panel: null,
  cinema: false,
  pending: null,
  ended: false,
  speed: 1,
  sleep: null,
}));

const set = usePlayer.setState;
const get = usePlayer.getState;

export function currentId(): string | null {
  const { queue, index } = get();
  return queue[index] ?? null;
}

export function itemById(id: string | null): Item | null {
  return id ? useLibrary.getState().items[id] ?? null : null;
}

export function mediaUrl(id: string): string {
  const base = useLibrary.getState().overview?.mediaBase ?? "";
  return base ? `${base}/m/${id}` : "";
}

export function coverUrl(item: Pick<Item, "id" | "thumbPath" | "thumbnailUrl"> | null | undefined): string | null {
  if (!item) return null;
  const base = useLibrary.getState().overview?.mediaBase ?? "";
  if (item.thumbPath && base) return `${base}/c/${item.id}`;
  return item.thumbnailUrl || null;
}

// Told when the person starts a new list, so a radio playing the old one stops.
const newQueueListeners = new Set<() => void>();
export function onNewQueue(listener: () => void) {
  newQueueListeners.add(listener);
}

// The element of what plays now; the other decks are idle, preloaded or fading out.
let active: Deck = decks.a;
let fadingOut: Deck | null = null;
let loadedId: string | null = null;
let pendingSeek: number | null = null;
let counted: string | null = null;

// The listening log: seconds really heard per play (seeks do not count), and where a long item
// (a podcast, a lecture) was left, to start there next time.
const LONG_ITEM = 20 * 60;
let playId: number | null = null;
let heard = 0;
let lastPosition = 0;
let lastReport = 0;
let lastResumeSave = 0;
const resumeAt = new Map<string, number | null>();

function reportHeard() {
  if (playId == null || heard <= 0) return;
  const play = playId;
  const seconds = Math.round(heard);
  void backend().then((api) => api.playSeconds(play, seconds));
}

function saveResume(id: string, position: number, total: number) {
  if (total <= LONG_ITEM) return;
  const value = position > 10 && position < total - 30 ? Math.round(position) : null;
  if (resumeAt.get(id) === value) return;
  resumeAt.set(id, value);
  void backend().then((api) => api.itemResume(id, value));
}

function startListening() {
  reportHeard();
  playId = null;
  heard = 0;
  lastPosition = 0;
  counted = null;
}

// Sound settings and normalization

const QUIET: Settings["audio"] = {
  normalize: true,
  normalizeBy: "track",
  eqOn: false,
  eqPreset: "flat",
  eqGains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  eqPreamp: 0,
  crossfadeSeconds: 0,
  gapless: true,
};

function sound(): Settings["audio"] {
  return useLibrary.getState().settings?.audio ?? QUIET;
}

const sameAlbum = (a: Item, b: Item) => !!a.album && !!b.album && key(a.album) === key(b.album) && key(a.artist) === key(b.artist);

/** The gain that evens this item out with the rest, in dB. */
function normalization(item: Item): number {
  const s = sound();
  if (!s.normalize) return 0;
  const album = s.normalizeBy === "album" && item.album ? Object.values(useLibrary.getState().items).filter((i) => i.status === "done" && sameAlbum(i, item)) : [];
  return gainFor(item, s.normalizeBy, album);
}

/** Asks the engine to measure what is about to play, when it was not measured yet. */
function measureSoon(ids: Array<string | null | undefined>) {
  const wanted = ids.filter((id): id is string => !!id && !!itemById(id) && itemById(id)!.loudness == null);
  if (wanted.length) void backend().then((api) => api.loudnessSoon(wanted).catch(() => {}));
}

// Loading and playing

function deckFor(item: Item): Deck {
  if (item.kind === "video") return decks.video;
  if (decks.a.id === item.id) return decks.a;
  if (decks.b.id === item.id) return decks.b;
  // A song takes the audio deck that is not playing, so the one playing can fade out.
  return active === decks.a ? decks.b : decks.a;
}

function silenceOthers(keep: Deck) {
  for (const deck of Object.values(decks)) {
    if (deck !== keep && !deck.el.paused) deck.el.pause();
  }
  fadingOut = null;
}

function pauseAll() {
  cancelTransition();
  for (const deck of Object.values(decks)) deck.el.pause();
  fadingOut = null;
}

async function source(deck: Deck, item: Item) {
  if (deck.id === item.id && deck.el.getAttribute("src")) return;
  deck.id = item.id;
  deck.el.src = mediaUrl(item.id) || (await mockSource(item));
}

async function mockSource(item: Item): Promise<string> {
  const { previewWav } = await import("@/lib/silence");
  return previewWav(Math.min(item.duration ?? 180, 600));
}

async function load(autoplay: boolean) {
  cancelTransition();
  const id = currentId();
  const item = itemById(id);
  if (get().ended) set({ ended: false });
  if (!id || !item) {
    for (const deck of Object.values(decks)) {
      deck.el.pause();
      deck.el.removeAttribute("src");
      deck.el.load();
      deck.id = null;
    }
    loadedId = null;
    set({ playing: false, position: 0, duration: 0 });
    return;
  }
  const deck = deckFor(item);
  silenceOthers(deck);
  if (loadedId !== id || active !== deck) {
    if (loadedId && loadedId !== id) saveResume(loadedId, active.el.currentTime, active.el.duration || 0);
    if (loadedId !== id) startListening();
    loadedId = id;
    active = deck;
    const saved = resumeAt.has(id) ? resumeAt.get(id) : item.resumeAt;
    if (pendingSeek == null && saved && (item.duration ?? 0) > LONG_ITEM) pendingSeek = saved;
    const fresh = deck.id !== id;
    await source(deck, item);
    if (!fresh) {
      // Still loaded from before (preloaded, or played a moment ago): start it over.
      deck.el.currentTime = pendingSeek ?? 0;
      pendingSeek = null;
    }
    deck.el.playbackRate = get().speed;
    setTrackGain(deck, normalization(item));
    setFade(deck, 1);
    set({ position: deck.el.currentTime || 0, duration: item.duration ?? 0, buffering: fresh });
    updateSession(item);
    measureSoon([id, get().queue[upcomingIndex() ?? -1]]);
  }
  if (autoplay) {
    try {
      await wake();
      await deck.el.play();
    } catch (e) {
      if ((e as DOMException)?.name !== "AbortError") set({ playing: false });
    }
  }
}

/** Plays a list from one of its items, or from a random one ("Aleatório"). */
export function playList(ids: string[], start: number | "random" = 0, options: { shuffle?: boolean } = {}) {
  // Inside the click itself: some web views only let sound start from a person's gesture.
  void wake();
  const playable = ids.filter((id) => itemById(id)?.status === "done");
  if (!playable.length) return;
  const shuffle = options.shuffle ?? get().shuffle;
  const requested = start === "random" ? undefined : ids[start];
  const startId =
    requested && playable.includes(requested)
      ? requested
      : start === "random"
        ? playable[Math.floor(Math.random() * playable.length)]
        : playable[0];
  if (shuffle) {
    const rest = shuffled(playable.filter((id) => id !== startId));
    set({ queue: [startId, ...rest], original: playable, index: 0, shuffle: true, pending: null });
  } else {
    set({ queue: playable, original: null, index: playable.indexOf(startId), shuffle: false, pending: null });
  }
  if (itemById(startId)?.kind === "video" && get().panel === null) set({ panel: "queue" });
  newQueueListeners.forEach((listener) => listener());
  void load(true);
}

export function playItem(id: string) {
  playList([id], 0, { shuffle: false });
}

/** Plays a search result. A song already in the library starts at once. Anything else
 *  downloads ahead of everything else and starts by itself when the file lands; meanwhile the
 *  player shows it as being prepared. */
export async function playEntry(entry: Entry, kind: Kind, source = "search") {
  if (itemById(entry.id)?.status === "done") {
    playItem(entry.id);
    return;
  }
  set({ pending: { entry, kind } });
  pauseAll();
  try {
    const item = await (await backend()).download(entry, kind, source, "now");
    if (item.status === "done" && get().pending?.entry.id === entry.id) playItem(item.id);
  } catch (e) {
    if (get().pending?.entry.id === entry.id) set({ pending: null });
    toast(String(e), "error");
  }
}

/** Gives up waiting for the prepared song; its download goes on. */
export function dropPending() {
  if (get().pending) set({ pending: null });
}

export function toggle() {
  dropPending();
  if (!currentId()) return;
  if (active.el.paused) {
    if (loadedId !== currentId()) void load(true);
    else void wake().then(() => active.el.play().catch(() => {}));
  } else {
    pauseAll();
  }
}

/** The position the queue moves to when the item playing ends, if any. */
function upcomingIndex(): number | null {
  const { queue, index, repeat } = get();
  if (repeat === "one" || !queue.length) return null;
  if (index + 1 < queue.length) return index + 1;
  if (repeat === "all" && queue.length > 1) return 0;
  return null;
}

export function next(auto = false) {
  if (!auto) {
    dropPending();
    void wake();
  }
  const { queue, index, repeat } = get();
  if (!queue.length) return;
  if (auto && repeat === "one") {
    active.el.currentTime = 0;
    void active.el.play();
    return;
  }
  if (index + 1 < queue.length) set({ index: index + 1 });
  else if (repeat === "all" || !auto) set({ index: 0 });
  else {
    set({ playing: false, ended: true });
    return;
  }
  void load(true);
}

export function previous() {
  dropPending();
  void wake();
  const { index, queue, repeat } = get();
  if (active.el.currentTime > 3 || !queue.length) {
    seek(0);
    return;
  }
  if (index > 0) set({ index: index - 1 });
  else if (repeat === "all") set({ index: queue.length - 1 });
  else {
    seek(0);
    return;
  }
  void load(true);
}

export function jump(index: number) {
  if (index < 0 || index >= get().queue.length) return;
  dropPending();
  void wake();
  set({ index });
  void load(true);
}

export function seek(seconds: number) {
  if (!Number.isFinite(seconds)) return;
  cancelTransition();
  if (fadingOut) silenceOthers(active);
  const el = active.el;
  el.currentTime = Math.max(0, Math.min(seconds, el.duration || seconds));
  set({ position: el.currentTime });
  sessionPosition();
}

export function seekBy(delta: number) {
  seek(active.el.currentTime + delta);
}

export function setVolume(volume: number) {
  const v = Math.max(0, Math.min(1, volume));
  set({ volume: v, muted: v === 0 ? get().muted : false });
  applyVolume();
}

export function toggleMute() {
  set({ muted: !get().muted });
  applyVolume();
}

function applyVolume() {
  const { volume, muted } = get();
  setMaster(volume, muted);
}

export function setSpeed(speed: number) {
  set({ speed });
  for (const deck of Object.values(decks)) deck.el.playbackRate = speed;
}

// The sleep timer

let sleepTimer: number | undefined;

/** Pauses after `minutes`, at the end of what plays ("end"), or never (null). */
export function setSleep(choice: number | "end" | null) {
  window.clearTimeout(sleepTimer);
  if (choice === null) set({ sleep: null });
  else if (choice === "end") set({ sleep: { endOfItem: true } });
  else {
    set({ sleep: { at: Date.now() + choice * 60_000 } });
    sleepTimer = window.setTimeout(fallAsleep, choice * 60_000);
  }
}

/** The sound fades out over a few seconds, then everything pauses. */
function fallAsleep() {
  window.clearTimeout(sleepTimer);
  set({ sleep: null });
  const deck = active;
  if (deck.el.paused) return;
  cancelTransition();
  fadeTo(deck, deck.fadeLevel, 0, 8, () => {
    pauseAll();
    setFade(deck, 1);
  });
}

export function toggleShuffle() {
  const { shuffle, queue, index, original } = get();
  const current = queue[index];
  if (!shuffle) {
    const rest = shuffled(queue.filter((_, i) => i !== index));
    set({ shuffle: true, original: queue, queue: current ? [current, ...rest] : rest, index: 0 });
  } else {
    const back = original ?? queue;
    set({ shuffle: false, original: null, queue: back, index: Math.max(0, back.indexOf(current)) });
  }
}

export function cycleRepeat() {
  const order: Repeat[] = ["off", "all", "one"];
  set({ repeat: order[(order.indexOf(get().repeat) + 1) % order.length] });
}

/** Adds after the current item (next) or at the end. `silent` skips the confirmation, for
 *  songs a radio adds by itself. */
export function enqueue(ids: string[], options: { next?: boolean; silent?: boolean } = {}) {
  const playable = ids.filter((id) => itemById(id)?.status === "done");
  if (!playable.length) return;
  const { queue, index } = get();
  if (!queue.length) {
    playList(playable, 0, { shuffle: false });
    return;
  }
  const at = options.next ? index + 1 : queue.length;
  set({ queue: [...queue.slice(0, at), ...playable, ...queue.slice(at)] });
  if (options.silent) return;
  toast(options.next ? "Toca em seguida." : playable.length === 1 ? "Adicionada à fila." : `${playable.length} itens adicionados à fila.`);
}

/** Moves the queue entry at `from` to `to` (positions in the whole queue). What plays keeps
 *  playing; only its position changes when it is moved or something crosses it. */
export function moveInQueue(from: number, to: number) {
  const { queue, index } = get();
  if (from === to || from < 0 || to < 0 || from >= queue.length || to >= queue.length) return;
  const next = [...queue];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  let current = index;
  if (from === index) current = to;
  else if (from < index && to >= index) current = index - 1;
  else if (from > index && to <= index) current = index + 1;
  set({ queue: next, index: current });
}

export function removeFromQueue(i: number) {
  const { queue, index } = get();
  if (i === index) return;
  set({ queue: queue.filter((_, j) => j !== i), index: i < index ? index - 1 : index });
}

export function clearUpcoming() {
  const { queue, index } = get();
  set({ queue: queue.slice(0, index + 1) });
}

export function setPanel(panel: Panel) {
  set({ panel: get().panel === panel ? null : panel });
}

export async function setCinema(on: boolean) {
  set({ cinema: on });
  const api = await backend();
  await api.setFullscreen(on).catch(() => {});
}

function shuffled<T>(list: T[]): T[] {
  const a = [...list];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Crossfade and gapless: the next song starts on the other audio deck before this one ends.

/** Songs of one album that follow each other overlap by this much, so no silence slips in. */
const GAPLESS_OVERLAP = 0.05;
let transitionTimer: number | undefined;
let scheduled = false;

function cancelTransition() {
  window.clearTimeout(transitionTimer);
  scheduled = false;
}

/** How the item playing hands over to the next one: a fade of some seconds, a seamless join
 *  within an album, or nothing special (the end event moves on). */
function handover(current: Item, upcoming: Item): number | null {
  if (current.kind !== "music" || upcoming.kind !== "music" || upcoming.id === current.id) return null;
  const s = sound();
  if (s.gapless && sameAlbum(current, upcoming)) return 0;
  return s.crossfadeSeconds > 0 ? s.crossfadeSeconds : null;
}

function watchTransition(deck: Deck) {
  if (scheduled || deck.name === "video" || deck.el.paused || "endOfItem" in (get().sleep ?? {})) return;
  const total = deck.el.duration;
  if (!Number.isFinite(total) || total <= 0) return;
  const at = upcomingIndex();
  const current = itemById(currentId());
  const upcoming = at == null ? null : itemById(get().queue[at]);
  if (!current || !upcoming) return;
  const fade = handover(current, upcoming);
  if (fade == null) return;
  const remaining = (total - deck.el.currentTime) / (deck.el.playbackRate || 1);
  const other = deck === decks.a ? decks.b : decks.a;
  if (remaining < fade + 15 && other.id !== upcoming.id) void preload(other, upcoming);
  const lead = fade > 0 ? fade : GAPLESS_OVERLAP;
  if (remaining > lead + 0.6) return;
  scheduled = true;
  transitionTimer = window.setTimeout(() => void handOver(deck, at!, fade), Math.max(0, (remaining - lead) * 1000));
}

async function preload(deck: Deck, item: Item) {
  if (deck === active) return;
  deck.el.pause();
  await source(deck, item);
  deck.el.preload = "auto";
  if (deck.el.readyState >= 1) deck.el.currentTime = 0;
  setTrackGain(deck, normalization(item));
  setFade(deck, 0);
}

async function handOver(from: Deck, at: number, fade: number) {
  scheduled = false;
  const upcoming = itemById(get().queue[at]);
  if (from !== active || !upcoming || from.el.paused) return;
  const to = from === decks.a ? decks.b : decks.a;
  await source(to, upcoming);
  if (to.el.readyState >= 1) to.el.currentTime = 0;
  to.el.playbackRate = get().speed;
  setTrackGain(to, normalization(upcoming));
  setFade(to, 0);

  // The queue moves one step; the old song keeps sounding while it fades out.
  if (loadedId) saveResume(loadedId, from.el.currentTime, from.el.duration || 0);
  startListening();
  loadedId = upcoming.id;
  active = to;
  fadingOut = from;
  set({ index: at, position: 0, duration: upcoming.duration ?? 0, ended: false });
  updateSession(upcoming);
  measureSoon([get().queue[upcomingIndex() ?? -1]]);

  await wake();
  void to.el.play().catch(() => {});
  const seconds = fade > 0 ? fade : GAPLESS_OVERLAP;
  fadeTo(to, 0, 1, fade > 0 ? seconds : 0.01);
  fadeTo(from, 1, 0, seconds, () => {
    if (fadingOut === from) {
      from.el.pause();
      fadingOut = null;
    }
    setFade(from, 1);
  });
}

// The system's media controls (keyboard keys, the Windows volume panel, MPRIS on Linux)

function updateSession(item: Item) {
  if (!("mediaSession" in navigator)) return;
  const cover = coverUrl(item);
  navigator.mediaSession.metadata = new MediaMetadata({
    title: item.title,
    artist: item.artist || item.channel,
    album: item.album ?? "",
    artwork: cover ? [96, 256, 512].map((size) => ({ src: cover, sizes: `${size}x${size}` })) : [],
  });
}

let lastSessionUpdate = 0;

function sessionPosition() {
  if (!("mediaSession" in navigator) || !navigator.mediaSession.setPositionState) return;
  const el = active.el;
  if (!Number.isFinite(el.duration) || el.duration <= 0) return;
  try {
    navigator.mediaSession.setPositionState({ duration: el.duration, position: Math.min(el.currentTime, el.duration), playbackRate: el.playbackRate || 1 });
  } catch {
    // Some web views reject a position a moment before the duration is known.
  }
}

// Web Audio check: the first song of the session must reach the chain with sound in it. A web
// view that hands Web Audio only silence would leave the app mute, so the chain is given up.
let verified = false;
let silentFor = 0;
let lastCheck = 0;

function checkChain(deck: Deck) {
  if (verified || useAudio.getState().mode !== "graph" || deck.el.paused) return;
  const t = deck.el.currentTime;
  const rms = level();
  if (rms == null || t < 1) return;
  if (rms > 0) {
    verified = true;
    return;
  }
  const step = t - lastCheck;
  lastCheck = t;
  if (step > 0 && step < 1.5) silentFor += step;
  if (silentFor >= 6) toPlain("a cadeia de áudio só recebeu silêncio");
}

/** The other way a chain fails: the element says it plays but its time never moves, because
 *  nothing pulls the sound through. Watched once a second until the chain is verified. */
let stallWatch: number | undefined;
function watchStall() {
  window.clearInterval(stallWatch);
  if (verified || useAudio.getState().mode !== "graph") return;
  let last = active.el.currentTime;
  let still = 0;
  stallWatch = window.setInterval(() => {
    const el = active.el;
    if (verified || useAudio.getState().mode !== "graph" || el.paused) {
      window.clearInterval(stallWatch);
      return;
    }
    if (el.currentTime !== last || el.readyState < 3) {
      last = el.currentTime;
      still = 0;
      return;
    }
    still += 1;
    if (still >= 5) {
      window.clearInterval(stallWatch);
      toPlain("o som parou dentro da cadeia de áudio");
    }
  }, 1000);
}

/** A file the element refuses: when the media server answers but not to a cross-origin read,
 *  the chain is what fails, so the app goes on without it and tries again. */
let retried = false;
async function recover(deck: Deck): Promise<boolean> {
  if (retried || useAudio.getState().mode !== "graph" || !deck.id) return false;
  const url = mediaUrl(deck.id);
  if (!url) return false;
  const corsWorks = await fetch(url, { headers: { Range: "bytes=0-1" } })
    .then((r) => r.ok)
    .catch(() => false);
  if (corsWorks) return false;
  retried = true;
  toPlain("o servidor de mídia não aceitou leitura do Web Audio");
  loadedId = null;
  await load(true);
  return true;
}

function onMediaEvent(deck: Deck, event: Event) {
  if (deck !== active) return;
  const el = deck.el;
  switch (event.type) {
    case "play":
      set({ playing: true });
      if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "playing";
      watchStall();
      break;
    case "pause": {
      set({ playing: false });
      if ("mediaSession" in navigator) navigator.mediaSession.playbackState = "paused";
      reportHeard();
      const id = currentId();
      if (id) saveResume(id, el.currentTime, el.duration || 0);
      rest();
      break;
    }
    case "waiting":
      set({ buffering: true });
      break;
    case "playing":
    case "canplay":
      set({ buffering: false });
      break;
    case "durationchange":
      if (Number.isFinite(el.duration)) set({ duration: el.duration });
      sessionPosition();
      break;
    case "ratechange":
      sessionPosition();
      break;
    case "loadedmetadata":
      if (pendingSeek != null) {
        el.currentTime = pendingSeek;
        pendingSeek = null;
      }
      break;
    case "timeupdate":
      onTime(deck);
      break;
    case "ended": {
      const id = currentId();
      if (id) saveResume(id, el.duration || 0, el.duration || 0);
      reportHeard();
      if (get().sleep && "endOfItem" in get().sleep!) {
        setSleep(null);
        set({ playing: false });
        return;
      }
      next(true);
      break;
    }
    case "error":
      if (!el.getAttribute("src")) return;
      void recover(deck).then((handled) => {
        if (handled) return;
        set({ playing: false, buffering: false });
        const item = itemById(currentId());
        toast(item ? `Não deu para tocar "${item.title}". O arquivo pode ter sido movido.` : "Não deu para tocar esse arquivo.", "error");
      });
      break;
  }
}

function onTime(deck: Deck) {
  const el = deck.el;
  const position = el.currentTime;
  set({ position });
  const step = position - lastPosition;
  if (!el.paused && step > 0 && step < 1.5 * (el.playbackRate || 1)) heard += step / (el.playbackRate || 1);
  lastPosition = position;
  const id = currentId();
  const threshold = Math.min(30, (el.duration || 60) * 0.5);
  if (id && counted !== id && position >= threshold) {
    counted = id;
    void backend()
      .then((api) => api.itemPlayed(id))
      .then((play) => {
        if (currentId() === id) playId = play;
      });
  }
  const now = Date.now();
  if (playId != null && now - lastReport > 15_000) {
    lastReport = now;
    reportHeard();
  }
  if (id && now - lastResumeSave > 10_000) {
    lastResumeSave = now;
    saveResume(id, position, el.duration || 0);
  }
  if (now - lastSessionUpdate > 5_000) {
    lastSessionUpdate = now;
    sessionPosition();
  }
  const sleep = get().sleep;
  if (sleep && "at" in sleep && now >= sleep.at) fallAsleep();
  checkChain(deck);
  watchTransition(deck);
}

function applySound(previous?: Settings["audio"]) {
  const s = sound();
  setEq(s.eqOn, s.eqGains, s.eqPreamp);
  if (previous && previous.normalize === s.normalize && previous.normalizeBy === s.normalizeBy) return;
  for (const deck of Object.values(decks)) {
    const item = itemById(deck.id);
    if (item) setTrackGain(deck, normalization(item), 0.5);
  }
}

/** What the sound path is doing, for the CI tour and the preview's test scripts. */
export function soundState() {
  return {
    mode: useAudio.getState().mode,
    chain: chainState(),
    reason: useAudio.getState().reason,
    level: level(),
    active: active.name,
    decks: Object.values(decks).map((d) => ({
      name: d.name,
      id: d.id,
      paused: d.el.paused,
      time: d.el.currentTime,
      volume: d.el.volume,
      trackDb: d.trackDb,
      fade: d.fadeLevel,
    })),
  };
}

export function seekFromEnd(seconds: number) {
  seek((active.el.duration || 0) - seconds);
}

let installed = false;

/** Wires the media elements, OS media keys, tray actions and persistence. Call once. */
export async function installPlayer() {
  if (installed) return;
  installed = true;

  installAudio(onMediaEvent);
  if (!isTauri) {
    // The browser preview lets its test scripts look at the sound path.
    Object.assign(window, { requiemSeekFromEnd: seekFromEnd, requiemSound: soundState });
  }

  if ("mediaSession" in navigator) {
    const ms = navigator.mediaSession;
    const actions: Array<[MediaSessionAction, MediaSessionActionHandler]> = [
      ["play", () => active.el.paused && toggle()],
      ["pause", () => !active.el.paused && toggle()],
      ["stop", () => pauseAll()],
      ["nexttrack", () => next()],
      ["previoustrack", () => previous()],
      ["seekto", (d) => d.seekTime != null && seek(d.seekTime)],
      ["seekbackward", (d) => seekBy(-(d.seekOffset ?? 10))],
      ["seekforward", (d) => seekBy(d.seekOffset ?? 10)],
    ];
    for (const [action, run] of actions) {
      try {
        ms.setActionHandler(action, run);
      } catch {
        // An action this web view does not know.
      }
    }
  }

  const api = await backend();
  api.onTray((action) => {
    if (action === "toggle") toggle();
    if (action === "next") next();
  });

  // Resume where the person stopped, paused.
  const saved = restored();
  const known = (saved.queue ?? []).filter((id) => itemById(id)?.status === "done");
  set({ volume: saved.volume ?? 0.8, muted: saved.muted ?? false, shuffle: saved.shuffle ?? false, repeat: saved.repeat ?? "off" });
  applyVolume();
  applySound();
  if (known.length) {
    const savedId = saved.queue?.[saved.index ?? 0];
    const index = Math.max(0, savedId ? known.indexOf(savedId) : 0);
    set({ queue: known, index });
    pendingSeek = savedId && known[index] === savedId ? saved.position ?? 0 : 0;
    await load(false);
    set({ position: pendingSeek ?? 0 });
  }
  // Nothing plays yet: the chain can sleep until the first play.
  rest();

  let timer: number | undefined;
  usePlayer.subscribe((s) => {
    window.clearTimeout(timer);
    timer = window.setTimeout(() => {
      const { queue, index, position, volume, muted, shuffle, repeat } = s;
      try {
        localStorage.setItem(STORAGE, JSON.stringify({ queue: queue.slice(0, 500), index, position, volume, muted, shuffle, repeat }));
      } catch {
        // Storage can be unavailable; playback does not depend on it.
      }
    }, 1000);
  });

  // An item deleted from the library leaves the queue, a song being prepared starts the moment
  // its file lands, and a loudness measured while its song plays moves the gain gently.
  useLibrary.subscribe((lib, prev) => {
    if (lib.settings?.audio !== prev.settings?.audio) applySound(prev.settings?.audio);
    if (lib.items === prev.items) return;
    for (const deck of Object.values(decks)) {
      const now = deck.id ? lib.items[deck.id] : undefined;
      const before = deck.id ? prev.items[deck.id] : undefined;
      if (now && before && (now.loudness !== before.loudness || now.peak !== before.peak)) setTrackGain(deck, normalization(now), 2);
    }
    const pending = get().pending;
    if (pending) {
      const item = lib.items[pending.entry.id];
      if (item?.status === "done") playItem(item.id);
      else if (item?.status === "failed" || item?.status === "skipped") {
        set({ pending: null });
        toast(item.error ? `Não deu para tocar "${item.title}". ${item.error}` : `Não deu para tocar "${item.title}".`, "error");
      } else if (!item && prev.items[pending.entry.id]) set({ pending: null });
    }
    const { queue, index } = get();
    const keep = queue.filter((id) => lib.items[id]?.status === "done");
    if (keep.length === queue.length) return;
    const current = queue[index];
    const stillThere = keep.includes(current);
    set({ queue: keep, index: stillThere ? keep.indexOf(current) : Math.min(index, Math.max(0, keep.length - 1)) });
    if (!stillThere) {
      pauseAll();
      void load(false);
    }
  });
}
