import type { Item } from "./types";

/** Everything plays near this loudness, like the streaming services. */
export const TARGET = -14;
/** Most masters of the last decades sit around here: a song not yet measured starts at the
 *  gain such a song would get, and moves when its loudness arrives. */
const TYPICAL = -11;
const MAX_BOOST = 9;
/** Boosts stop where the loudest sample would pass this. */
const CEILING = -1;

export type NormalizeBy = "track" | "album";

/** Loudness of several songs heard as one program: the energy mean, weighted by length. */
export function combined(songs: Array<Pick<Item, "loudness" | "duration">>): number | null {
  let energy = 0;
  let weight = 0;
  for (const s of songs) {
    if (s.loudness == null) continue;
    const w = Math.max(1, s.duration ?? 1);
    energy += w * 10 ** (s.loudness / 10);
    weight += w;
  }
  return weight ? 10 * Math.log10(energy / weight) : null;
}

/** The gain in dB that brings an item to the target. In album mode the whole album gets one
 *  gain, so its songs keep their differences. */
export function gainFor(item: Item, by: NormalizeBy, album: Item[] = []): number {
  const together = by === "album" && item.album && album.length > 1;
  const loudness = together ? combined(album) : item.loudness;
  const peaks = (together ? album : [item]).map((s) => s.peak).filter((p): p is number => p != null);
  let gain = TARGET - (loudness ?? TYPICAL);
  gain = Math.min(gain, MAX_BOOST);
  if (peaks.length) gain = Math.min(gain, CEILING - Math.max(...peaks));
  return Math.round(gain * 10) / 10;
}

export const dbToGain = (db: number) => 10 ** (db / 20);
