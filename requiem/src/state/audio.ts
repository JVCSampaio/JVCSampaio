import { create } from "zustand";
import { BANDS } from "@/lib/eq";
import { dbToGain } from "@/lib/loudness";

/**
 * The sound path. Three media elements: the <video> for videos (drawn on screen by MediaStage)
 * and two hidden <audio> for songs, so one song can fade in while the other fades out. With Web
 * Audio, each element feeds its own two gains (the song's normalization, the fade) into one
 * chain: equalizer, limiter, volume. If that chain stays silent while time passes (a web view
 * that will not hand cross-origin audio to Web Audio), the elements are replaced by plain ones
 * and volume, normalization and fades work on each element's own volume, without boosts or
 * equalizer.
 */

export type DeckName = "video" | "a" | "b";
export type AudioMode = "graph" | "plain";

export interface Deck {
  name: DeckName;
  el: HTMLMediaElement;
  /** The item whose file is loaded here. */
  id: string | null;
  source: MediaElementAudioSourceNode | null;
  track: GainNode | null;
  fade: GainNode | null;
  /** The same two values, kept for plain mode, where they multiply into el.volume. */
  trackDb: number;
  fadeLevel: number;
}

interface AudioState {
  mode: AudioMode;
  /** Why the chain was given up, for Ajustes. */
  reason: string | null;
  /** The element MediaStage draws; it changes when the chain is given up. */
  video: HTMLVideoElement;
}

type Handler = (deck: Deck, event: Event) => void;

const EVENTS = [
  "play",
  "pause",
  "playing",
  "waiting",
  "canplay",
  "durationchange",
  "loadedmetadata",
  "timeupdate",
  "ended",
  "error",
  "ratechange",
] as const;

function makeElement(kind: "video" | "audio", cors: boolean): HTMLMediaElement {
  const el = document.createElement(kind);
  // Set before any src, or Web Audio only hears silence from the media server.
  if (cors) el.crossOrigin = "anonymous";
  el.preload = "auto";
  if (el instanceof HTMLVideoElement) {
    el.playsInline = true;
    el.setAttribute("aria-label", "Vídeo tocando agora");
    el.className = "h-full w-full bg-black object-contain";
  }
  return el;
}

function newDeck(name: DeckName, cors: boolean): Deck {
  return {
    name,
    el: makeElement(name === "video" ? "video" : "audio", cors),
    id: null,
    source: null,
    track: null,
    fade: null,
    trackDb: 0,
    fadeLevel: 1,
  };
}

// The URL can force a mode in the browser preview: ?audio=plain, or rehearse a broken chain
// with ?audio=silent (it hears only zeros) or ?audio=stall (it never pulls the songs along).
const forced = typeof location !== "undefined" ? new URLSearchParams(location.search).get("audio") : null;

export const decks: Record<DeckName, Deck> = {
  video: newDeck("video", forced !== "plain"),
  a: newDeck("a", forced !== "plain"),
  b: newDeck("b", forced !== "plain"),
};

export const useAudio = create<AudioState>()(() => ({
  mode: forced === "plain" ? "plain" : "graph",
  reason: null,
  video: decks.video.el as HTMLVideoElement,
}));

let ctx: AudioContext | null = null;
let bus: GainNode | null = null;
let preamp: GainNode | null = null;
let bands: BiquadFilterNode[] = [];
let master: GainNode | null = null;
let analyser: AnalyserNode | null = null;
let handler: Handler | null = null;
let masterLevel = 1;
let suspendTimer: number | undefined;
const fades = new Map<DeckName, number>();

const mode = () => useAudio.getState().mode;

function bind(deck: Deck) {
  for (const type of EVENTS) deck.el.addEventListener(type, (event) => handler?.(deck, event));
}

/** Creates the chain and wires the elements' events to `onEvent`. Call once. */
export function installAudio(onEvent: Handler) {
  handler = onEvent;
  Object.values(decks).forEach(bind);
  if (mode() === "plain") return;
  try {
    const Context = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Context) throw new Error("Web Audio indisponível");
    ctx = new Context({ latencyHint: "playback" });
    bus = ctx.createGain();
    preamp = ctx.createGain();
    bands = BANDS.map((frequency, i) => {
      const band = ctx!.createBiquadFilter();
      band.type = i === 0 ? "lowshelf" : i === BANDS.length - 1 ? "highshelf" : "peaking";
      band.frequency.value = frequency;
      band.Q.value = 1.1;
      band.gain.value = 0;
      return band;
    });
    const limiter = ctx.createDynamicsCompressor();
    limiter.threshold.value = -1;
    limiter.knee.value = 0;
    limiter.ratio.value = 20;
    limiter.attack.value = 0.002;
    limiter.release.value = 0.1;
    master = ctx.createGain();
    analyser = ctx.createAnalyser();
    analyser.fftSize = 2048;

    let chain: AudioNode = bus.connect(preamp);
    for (const band of bands) chain = chain.connect(band);
    chain.connect(limiter).connect(master).connect(ctx.destination);
    bus.connect(analyser);

    for (const deck of Object.values(decks)) {
      deck.source = ctx.createMediaElementSource(deck.el);
      deck.track = ctx.createGain();
      deck.fade = ctx.createGain();
      deck.source.connect(deck.track).connect(deck.fade).connect(bus);
    }
    if (forced === "silent") bus.gain.value = 0;
    if (forced === "stall") bus.disconnect();
  } catch (e) {
    toPlain(`Web Audio não abriu: ${String(e)}`);
  }
}

/** Starts the chain before something plays; the first click of the session allows it. */
export async function wake() {
  window.clearTimeout(suspendTimer);
  if (ctx && ctx.state !== "running") await ctx.resume().catch(() => {});
}

/** Lets the chain sleep after a while in pause, to spare the processor. */
export function rest() {
  window.clearTimeout(suspendTimer);
  if (!ctx) return;
  suspendTimer = window.setTimeout(() => {
    const playing = Object.values(decks).some((d) => !d.el.paused);
    if (!playing && ctx?.state === "running") void ctx.suspend().catch(() => {});
  }, 30_000);
}

function plainVolume(deck: Deck) {
  if (mode() !== "plain") return;
  deck.el.volume = Math.max(0, Math.min(1, masterLevel * dbToGain(Math.min(0, deck.trackDb)) * deck.fadeLevel));
}

/** The listener's volume: a squared curve sounds even across the slider. */
export function setMaster(volume: number, muted: boolean) {
  masterLevel = muted ? 0 : volume * volume;
  if (master && ctx) master.gain.setTargetAtTime(masterLevel, ctx.currentTime, 0.015);
  Object.values(decks).forEach(plainVolume);
}

/** A song's normalization gain, reached over `seconds`. */
export function setTrackGain(deck: Deck, db: number, seconds = 0) {
  deck.trackDb = db;
  if (deck.track && ctx) {
    const now = ctx.currentTime;
    deck.track.gain.cancelScheduledValues(now);
    if (seconds > 0) deck.track.gain.setTargetAtTime(dbToGain(db), now, seconds / 3);
    else deck.track.gain.setValueAtTime(dbToGain(db), now);
  }
  plainVolume(deck);
}

/** Sets a deck's fade level at once, ending any fade in progress. */
export function setFade(deck: Deck, level: number) {
  window.clearInterval(fades.get(deck.name));
  fades.delete(deck.name);
  deck.fadeLevel = level;
  if (deck.fade && ctx) {
    deck.fade.gain.cancelScheduledValues(ctx.currentTime);
    deck.fade.gain.setValueAtTime(level, ctx.currentTime);
  }
  plainVolume(deck);
}

/** Equal-power shape: two songs crossing keep the same total loudness. */
function shape(from: number, to: number, t: number) {
  return to > from ? from + (to - from) * Math.sin((t * Math.PI) / 2) : to + (from - to) * Math.cos((t * Math.PI) / 2);
}

/** Fades a deck from `from` to `to` over `seconds`, then calls `done`. */
export function fadeTo(deck: Deck, from: number, to: number, seconds: number, done?: () => void) {
  setFade(deck, from);
  const ms = Math.max(10, seconds * 1000);
  if (deck.fade && ctx && mode() === "graph") {
    // The curve starts a moment after the value set above, or the two would overlap.
    const at = ctx.currentTime + 0.01;
    const gain = deck.fade.gain;
    try {
      gain.setValueCurveAtTime(new Float32Array(48).map((_, i) => shape(from, to, i / 47)), at, Math.max(0.01, seconds));
    } catch {
      gain.linearRampToValueAtTime(to, at + seconds);
    }
    deck.fadeLevel = to;
  }
  const start = performance.now();
  const timer = window.setInterval(() => {
    const t = Math.min(1, (performance.now() - start) / ms);
    if (mode() === "plain") {
      deck.fadeLevel = shape(from, to, t);
      plainVolume(deck);
    }
    if (t >= 1) {
      window.clearInterval(timer);
      fades.delete(deck.name);
      deck.fadeLevel = to;
      plainVolume(deck);
      done?.();
    }
  }, 25);
  fades.set(deck.name, timer);
}

/** The equalizer: ten band gains and a preamp, all in dB. Off is flat. */
export function setEq(on: boolean, gains: number[], preampDb: number) {
  if (!ctx || !preamp) return;
  const now = ctx.currentTime;
  bands.forEach((band, i) => band.gain.setTargetAtTime(on ? gains[i] ?? 0 : 0, now, 0.03));
  preamp.gain.setTargetAtTime(on ? dbToGain(preampDb) : 1, now, 0.03);
}

/** The chain's state ("running", "suspended", "closed"), or null without one. */
export function chainState(): string | null {
  return ctx?.state ?? null;
}

/** Loudness of what reaches the chain right now (root mean square), or null without a chain. */
export function level(): number | null {
  if (!analyser || ctx?.state !== "running") return null;
  const data = new Float32Array(analyser.fftSize);
  analyser.getFloatTimeDomainData(data);
  let sum = 0;
  for (const v of data) sum += v * v;
  return Math.sqrt(sum / data.length);
}

/** Gives the chain up: new plain elements take over where the old ones were. */
export function toPlain(reason: string) {
  if (mode() === "plain" && useAudio.getState().reason) return;
  console.warn(`Requiem: sem Web Audio (${reason})`);
  for (const deck of Object.values(decks)) {
    const old = deck.el;
    const fresh = makeElement(deck.name === "video" ? "video" : "audio", false);
    const src = old.getAttribute("src");
    const wasPlaying = !old.paused;
    const at = old.currentTime;
    fresh.playbackRate = old.playbackRate;
    old.pause();
    old.removeAttribute("src");
    old.load();
    old.replaceWith(fresh);
    deck.el = fresh;
    deck.source = null;
    deck.track = null;
    deck.fade = null;
    bind(deck);
    if (src) {
      fresh.src = src;
      fresh.addEventListener("loadedmetadata", () => (fresh.currentTime = at), { once: true });
      if (wasPlaying) void fresh.play().catch(() => {});
    }
  }
  void ctx?.close().catch(() => {});
  ctx = null;
  bus = preamp = master = analyser = null;
  bands = [];
  useAudio.setState({ mode: "plain", reason, video: decks.video.el as HTMLVideoElement });
  Object.values(decks).forEach(plainVolume);
}
