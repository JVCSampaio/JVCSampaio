import { create } from "zustand";
import { backend } from "@/lib/backend";
import type { Entry } from "@/lib/types";
import { useLibrary } from "./library";
import { clearUpcoming, currentId, enqueue, itemById, next, onNewQueue, playItem, usePlayer } from "./player";

export type RadioMode = "radio" | "autoplay";

interface RadioState {
  /** "radio": started from a song. "autoplay": the queue was ending and similar songs follow. */
  mode: RadioMode | null;
  /** The song it grew from. */
  seedId: string | null;
  /** Songs it put in the queue, to mark them there. */
  added: string[];
  /** Songs it asked to download; each joins the queue when its file lands. */
  preparing: Entry[];
  loading: boolean;
  error: string | null;
}

const OFF: RadioState = { mode: null, seedId: null, added: [], preparing: [], loading: false, error: null };

export const useRadio = create<RadioState>()(() => OFF);

const set = useRadio.setState;
const get = useRadio.getState;

/** Songs ready or on their way after the one playing. */
const AHEAD = 2;

// Every start and stop moves `run`, so answers meant for an older radio are dropped.
let run = 0;
let candidates: Entry[] = [];
let used = new Set<string>();
let fetchedFrom: string | null = null;
let feeding = false;

const autoplayOn = () => useLibrary.getState().settings?.autoplay !== false;

/** Starts a radio from a song in the library: it plays now and similar songs follow it. */
export function startRadio(id: string) {
  if (itemById(id)?.status !== "done") return;
  if (currentId() === id) clearUpcoming();
  else playItem(id);
  begin("radio", id);
}

export function stopRadio() {
  if (!get().mode) return;
  run++;
  candidates = [];
  set(OFF);
}

export function retryRadio() {
  const { mode, seedId, added } = get();
  if (!mode || !seedId) return;
  fetchedFrom = null;
  void fetchMore(added[added.length - 1] ?? seedId);
}

function begin(mode: RadioMode, seedId: string) {
  run++;
  candidates = [];
  fetchedFrom = null;
  used = new Set([...usePlayer.getState().queue, seedId]);
  set({ ...OFF, mode, seedId });
  void fetchMore(seedId);
}

/** Lists more songs, from the newest one the radio added so it drifts like a station. A seed
 *  is asked once: when it brings nothing new, the radio stops growing instead of asking again. */
async function fetchMore(seed: string) {
  if (get().loading || fetchedFrom === seed) return;
  fetchedFrom = seed;
  const mine = run;
  set({ loading: true, error: null });
  try {
    const found = await (await backend()).radio(seed, [...used]);
    if (mine !== run) return;
    candidates.push(...found.filter((e) => !used.has(e.id) && !candidates.some((c) => c.id === e.id)));
    set({ loading: false });
    if (!candidates.length && !get().preparing.length && !get().added.length) {
      set({ error: "Não achei músicas parecidas com esta." });
      return;
    }
    feed();
  } catch (e) {
    if (mine === run) set({ loading: false, error: String(e) });
  }
}

function feed() {
  const { mode, seedId } = get();
  if (!mode || !seedId || feeding) return;
  feeding = true;
  try {
    const { queue, index } = usePlayer.getState();
    let ahead = queue.length - 1 - index + get().preparing.length;
    while (ahead < AHEAD && candidates.length) {
      const entry = candidates.shift()!;
      if (used.has(entry.id)) continue;
      used.add(entry.id);
      ahead++;
      if (itemById(entry.id)?.status === "done") append(entry.id);
      else void prepare(entry);
    }
  } finally {
    feeding = false;
  }
  if (candidates.length < 3) {
    const { added } = get();
    void fetchMore(added[added.length - 1] ?? seedId);
  }
}

async function prepare(entry: Entry) {
  const mine = run;
  set((s) => ({ preparing: [...s.preparing, entry] }));
  try {
    const item = await (await backend()).download(entry, "music", "radio", "soon");
    if (mine === run && item.status === "done") landed(entry.id);
  } catch {
    if (mine !== run) return;
    set((s) => ({ preparing: s.preparing.filter((p) => p.id !== entry.id) }));
    feed();
  }
}

function landed(id: string) {
  if (!get().preparing.some((p) => p.id === id)) return;
  set((s) => ({ preparing: s.preparing.filter((p) => p.id !== id) }));
  append(id);
}

function append(id: string) {
  const stopped = usePlayer.getState().ended;
  set((s) => ({ added: [...s.added, id] }));
  enqueue([id], { silent: true });
  // The queue ran out while this song downloaded: go on with it.
  if (stopped) next();
}

let installed = false;

/** Wires the radio to the player and the library. Call once. */
export function installRadio() {
  if (installed) return;
  installed = true;

  onNewQueue(stopRadio);

  usePlayer.subscribe((p, prev) => {
    if (get().mode) {
      if (p.index !== prev.index || p.queue !== prev.queue) feed();
      return;
    }
    // Autoplay: the last song of the queue played for a while, so similar ones get ready.
    if (p.position === prev.position && p.index === prev.index) return;
    if (!autoplayOn() || p.repeat !== "off" || p.pending || p.index !== p.queue.length - 1) return;
    const item = itemById(p.queue[p.index] ?? null);
    if (item?.kind === "music" && p.position >= Math.min(15, (p.duration || 30) / 2)) begin("autoplay", item.id);
  });

  useLibrary.subscribe((lib, prev) => {
    if (lib.settings !== prev.settings && !autoplayOn() && get().mode === "autoplay") stopRadio();
    if (lib.items === prev.items || !get().preparing.length) return;
    let dropped = false;
    for (const entry of get().preparing) {
      const item = lib.items[entry.id];
      if (item?.status === "done") landed(entry.id);
      else if ((!item && prev.items[entry.id]) || item?.status === "failed" || item?.status === "skipped" || item?.status === "removed") {
        set((s) => ({ preparing: s.preparing.filter((p) => p.id !== entry.id) }));
        dropped = true;
      }
    }
    if (dropped) feed();
  });
}
