import type { Item } from "./types";

/** Lowercase, without accents or symbols, so "sao" finds "São" and "acdc" finds "AC/DC". */
export function fold(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]+/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}

const folded = new WeakMap<Item, { title: string; all: string }>();

function words(item: Item) {
  let cached = folded.get(item);
  if (!cached) {
    const title = fold(item.title);
    cached = { title, all: ` ${title} ${fold(item.artist || item.channel)} ${fold(item.album ?? "")} ` };
    folded.set(item, cached);
  }
  return cached;
}

/** Library songs and videos where every typed word starts a word of the title, the artist or
 *  the album. A title that starts with what was typed comes first, then the most played. */
export function searchLibrary(items: Iterable<Item>, query: string, limit = 4): Item[] {
  const typed = fold(query);
  const terms = typed.split(" ").filter(Boolean);
  if (!terms.length) return [];
  const found: Array<{ item: Item; rank: number }> = [];
  for (const item of items) {
    if (item.status !== "done") continue;
    const { title, all } = words(item);
    if (!terms.every((t) => all.includes(` ${t}`))) continue;
    found.push({ item, rank: title.startsWith(typed) ? 0 : title.includes(typed) ? 1 : 2 });
  }
  return found
    .sort((a, b) => a.rank - b.rank || b.item.playCount - a.item.playCount)
    .slice(0, limit)
    .map((f) => f.item);
}
