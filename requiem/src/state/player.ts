import { create } from "zustand";
import { backend } from "@/lib/backend";
import type { Item } from "@/lib/types";
import { useLibrary } from "./library";
import { toast } from "./toasts";

export type Repeat = "off" | "all" | "one";
export type Panel = "lyrics" | "queue" | null;

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
}

/** One media element for the whole app: a <video> plays audio files too, so songs and videos
 *  share the queue, the controls and the OS media keys. It never moves in the DOM. */
export const media: HTMLVideoElement = document.createElement("video");
media.preload = "auto";
media.playsInline = true;
media.setAttribute("aria-label", "Vídeo tocando agora");

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

let loadedId: string | null = null;
let pendingSeek: number | null = null;
let counted: string | null = null;

async function load(autoplay: boolean) {
  const id = currentId();
  const item = itemById(id);
  if (!id || !item) {
    media.removeAttribute("src");
    media.load();
    loadedId = null;
    set({ playing: false, position: 0, duration: 0 });
    return;
  }
  if (loadedId !== id) {
    loadedId = id;
    counted = null;
    const url = mediaUrl(id) || (await mockSource(item));
    media.src = url;
    set({ position: 0, duration: item.duration ?? 0, buffering: true });
    updateSession(item);
  }
  if (autoplay) {
    try {
      await media.play();
    } catch (e) {
      if ((e as DOMException)?.name !== "AbortError") set({ playing: false });
    }
  }
}

async function mockSource(item: Item): Promise<string> {
  const { silentWav } = await import("@/lib/silence");
  return silentWav(Math.min(item.duration ?? 180, 600));
}

/** Plays a list from one of its items, or from a random one ("Aleatório"). */
export function playList(ids: string[], start: number | "random" = 0, options: { shuffle?: boolean } = {}) {
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
    set({ queue: [startId, ...rest], original: playable, index: 0, shuffle: true });
  } else {
    set({ queue: playable, original: null, index: playable.indexOf(startId), shuffle: false });
  }
  if (itemById(startId)?.kind === "video" && get().panel === null) set({ panel: "queue" });
  void load(true);
}

export function playItem(id: string) {
  playList([id], 0, { shuffle: false });
}

export function toggle() {
  if (!currentId()) return;
  if (media.paused) {
    if (loadedId !== currentId()) void load(true);
    else void media.play().catch(() => {});
  } else media.pause();
}

export function next(auto = false) {
  const { queue, index, repeat } = get();
  if (!queue.length) return;
  if (auto && repeat === "one") {
    media.currentTime = 0;
    void media.play();
    return;
  }
  if (index + 1 < queue.length) set({ index: index + 1 });
  else if (repeat === "all" || !auto) set({ index: 0 });
  else {
    set({ playing: false });
    return;
  }
  void load(true);
}

export function previous() {
  const { index, queue, repeat } = get();
  if (media.currentTime > 3 || !queue.length) {
    media.currentTime = 0;
    return;
  }
  if (index > 0) set({ index: index - 1 });
  else if (repeat === "all") set({ index: queue.length - 1 });
  else {
    media.currentTime = 0;
    return;
  }
  void load(true);
}

export function jump(index: number) {
  if (index < 0 || index >= get().queue.length) return;
  set({ index });
  void load(true);
}

export function seek(seconds: number) {
  if (!Number.isFinite(seconds)) return;
  media.currentTime = Math.max(0, Math.min(seconds, media.duration || seconds));
  set({ position: media.currentTime });
}

export function seekBy(delta: number) {
  seek(media.currentTime + delta);
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
  // A squared curve sounds even to the ear across the slider.
  media.volume = volume * volume;
  media.muted = muted;
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

/** Adds after the current item (next) or at the end. */
export function enqueue(ids: string[], options: { next?: boolean } = {}) {
  const playable = ids.filter((id) => itemById(id)?.status === "done");
  if (!playable.length) return;
  const { queue, index } = get();
  if (!queue.length) {
    playList(playable, 0, { shuffle: false });
    return;
  }
  const at = options.next ? index + 1 : queue.length;
  set({ queue: [...queue.slice(0, at), ...playable, ...queue.slice(at)] });
  toast(options.next ? "Toca em seguida." : playable.length === 1 ? "Adicionada à fila." : `${playable.length} itens adicionados à fila.`);
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

function updateSession(item: Item) {
  if (!("mediaSession" in navigator)) return;
  const cover = coverUrl(item);
  navigator.mediaSession.metadata = new MediaMetadata({
    title: item.title,
    artist: item.artist,
    album: item.album ?? "",
    artwork: cover ? [{ src: cover }] : [],
  });
}

let installed = false;

/** Wires the media element, OS media keys, tray actions and persistence. Call once. */
export async function installPlayer() {
  if (installed) return;
  installed = true;

  media.addEventListener("play", () => set({ playing: true }));
  media.addEventListener("pause", () => set({ playing: false }));
  media.addEventListener("waiting", () => set({ buffering: true }));
  media.addEventListener("playing", () => set({ buffering: false }));
  media.addEventListener("canplay", () => set({ buffering: false }));
  media.addEventListener("durationchange", () => {
    if (Number.isFinite(media.duration)) set({ duration: media.duration });
  });
  media.addEventListener("loadedmetadata", () => {
    if (pendingSeek != null) {
      media.currentTime = pendingSeek;
      pendingSeek = null;
    }
  });
  media.addEventListener("timeupdate", () => {
    const position = media.currentTime;
    set({ position });
    const id = currentId();
    const threshold = Math.min(30, (media.duration || 60) * 0.5);
    if (id && counted !== id && position >= threshold) {
      counted = id;
      void backend().then((api) => api.itemPlayed(id));
    }
  });
  media.addEventListener("ended", () => next(true));
  media.addEventListener("error", () => {
    if (!media.getAttribute("src")) return;
    set({ playing: false, buffering: false });
    const item = itemById(currentId());
    toast(item ? `Não deu para tocar "${item.title}". O arquivo pode ter sido movido.` : "Não deu para tocar esse arquivo.", "error");
  });

  if ("mediaSession" in navigator) {
    const ms = navigator.mediaSession;
    ms.setActionHandler("play", () => toggle());
    ms.setActionHandler("pause", () => toggle());
    ms.setActionHandler("nexttrack", () => next());
    ms.setActionHandler("previoustrack", () => previous());
    ms.setActionHandler("seekto", (d) => d.seekTime != null && seek(d.seekTime));
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
  if (known.length) {
    const savedId = saved.queue?.[saved.index ?? 0];
    const index = Math.max(0, savedId ? known.indexOf(savedId) : 0);
    set({ queue: known, index });
    pendingSeek = savedId && known[index] === savedId ? saved.position ?? 0 : 0;
    await load(false);
    set({ position: pendingSeek ?? 0 });
  }

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

  // An item deleted from the library leaves the queue.
  useLibrary.subscribe((lib, prev) => {
    if (lib.items === prev.items) return;
    const { queue, index } = get();
    const keep = queue.filter((id) => lib.items[id]?.status === "done");
    if (keep.length === queue.length) return;
    const current = queue[index];
    const stillThere = keep.includes(current);
    set({ queue: keep, index: stillThere ? keep.indexOf(current) : Math.min(index, Math.max(0, keep.length - 1)) });
    if (!stillThere) {
      media.pause();
      void load(false);
    }
  });
}
