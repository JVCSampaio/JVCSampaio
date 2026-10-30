import { CaretDown, CaretUp, Clock, DotsThree, Flame } from "@phosphor-icons/react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { RowCover } from "@/components/ui/Cover";
import { Dropdown, RightClick } from "@/components/ui/Menu";
import { cn, duration } from "@/lib/format";
import type { Item, Playlist } from "@/lib/types";
import { useLibrary } from "@/state/library";
import { coverUrl, playList, usePlayer } from "@/state/player";
import { itemMenu } from "./itemMenu";
import { LikeButton } from "./LikeButton";

const ROW = 56;

export type SortKey = "added" | "title" | "album" | "duration";
export interface TrackSort {
  key: SortKey;
  dir: "asc" | "desc";
}

/** A column header click: ascending, then descending, then back to the order of arrival. */
function nextSort(current: TrackSort, key: SortKey): TrackSort {
  if (current.key !== key) return { key, dir: "asc" };
  if (current.dir === "asc") return { key, dir: "desc" };
  return { key: "added", dir: "desc" };
}

const collator = new Intl.Collator("pt-BR", { sensitivity: "base", numeric: true });

export function sortTracks(items: Item[], sort: TrackSort): Item[] {
  if (sort.key === "added") return items;
  const sign = sort.dir === "asc" ? 1 : -1;
  const value = (i: Item) => (sort.key === "title" ? i.title : sort.key === "album" ? i.album ?? "" : "");
  return [...items].sort((a, b) =>
    sort.key === "duration" ? sign * ((a.duration ?? 0) - (b.duration ?? 0)) : sign * collator.compare(value(a), value(b)),
  );
}

function HeaderCell({ label, sortKey, sort, onSort, end }: { label: ReactNode; sortKey: SortKey; sort?: TrackSort; onSort?: (s: TrackSort) => void; end?: boolean }) {
  if (!sort || !onSort) return <span className={cn(end && "flex justify-end")}>{label}</span>;
  const active = sort.key === sortKey;
  const Caret = sort.dir === "asc" ? CaretUp : CaretDown;
  return (
    <button
      type="button"
      onClick={() => onSort(nextSort(sort, sortKey))}
      className={cn(
        "flex items-center gap-1 transition-[color] [transition-duration:var(--dur-fast)] hover:text-foreground",
        end && "justify-end",
        active && "text-foreground",
      )}
    >
      {label}
      {active && <Caret weight="bold" className="h-3 w-3" />}
    </button>
  );
}

/** The song list of the library, an artist, an album or a playlist. Virtualized: thousands of
 *  rows scroll like ten. Double click or Enter plays from that row on. */
export function TrackList({
  items,
  inPlaylist,
  showAlbum = true,
  sort,
  onSort,
}: {
  items: Item[];
  inPlaylist?: Playlist;
  showAlbum?: boolean;
  sort?: TrackSort;
  onSort?: (sort: TrackSort) => void;
}) {
  const listRef = useRef<HTMLDivElement>(null);
  const [scroller, setScroller] = useState<HTMLElement | null>(null);
  const [margin, setMargin] = useState(0);
  const playlists = useLibrary((s) => s.playlists);
  const playing = usePlayer((s) => s.playing);
  const nowId = usePlayer((s) => s.queue[s.index] ?? null);

  useEffect(() => {
    const main = document.getElementById("main-scroll");
    setScroller(main);
    // Distance from the top of the scrolling area to the list, independent of the scroll.
    const measure = () => {
      if (!main || !listRef.current) return;
      setMargin(listRef.current.getBoundingClientRect().top - main.getBoundingClientRect().top + main.scrollTop);
    };
    measure();
    // Anything above the list growing or shrinking (a banner, a hero) moves it.
    const ro = new ResizeObserver(measure);
    const content = document.getElementById("main-content");
    if (content) ro.observe(content);
    return () => ro.disconnect();
  }, []);

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => scroller,
    estimateSize: () => ROW,
    overscan: 12,
    scrollMargin: margin,
  });

  const ids = items.map((i) => i.id);
  const wide = items.some((i) => i.kind === "video");
  const cols = showAlbum
    ? "grid-cols-[36px_minmax(0,1.7fr)_minmax(0,1fr)_32px_56px_36px]"
    : "grid-cols-[36px_minmax(0,1fr)_32px_56px_36px]";

  return (
    <div>
      <div className={cn("grid items-center gap-4 border-b border-border px-3 pb-2 text-[13px] text-faint-foreground", cols)}>
        <span className="text-right tabular-nums">#</span>
        <HeaderCell label="Título" sortKey="title" sort={sort} onSort={onSort} />
        {showAlbum && <HeaderCell label="Álbum" sortKey="album" sort={sort} onSort={onSort} />}
        <span />
        <HeaderCell label={<Clock className="h-4 w-4" aria-label="Duração" />} sortKey="duration" sort={sort} onSort={onSort} end />
        <span />
      </div>
      <div ref={listRef} className="relative mt-2" style={{ height: virtualizer.getTotalSize() }}>
        {virtualizer.getVirtualItems().map((row) => {
          const item = items[row.index];
          const current = item.id === nowId;
          const menu = itemMenu(item, playlists, inPlaylist);
          return (
            <RightClick key={item.id} entries={menu}>
              <div
                role="button"
                tabIndex={0}
                aria-label={`${item.title}, ${item.artist}`}
                onDoubleClick={() => playList(ids, row.index)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") playList(ids, row.index);
                }}
                className={cn(
                  "group absolute left-0 right-0 grid items-center gap-4 rounded-control px-3",
                  "transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised focus-visible:bg-raised",
                  cols,
                )}
                style={{ height: ROW, transform: `translateY(${row.start - margin}px)` }}
              >
                <span className="flex justify-end text-right text-sm tabular-nums text-faint-foreground">
                  {current ? (
                    <Flame weight="fill" className={cn("h-4 w-4 text-primary", playing && "flame-live")} aria-label="Tocando agora" />
                  ) : (
                    row.index + 1
                  )}
                </span>
                <div className="flex min-w-0 items-center gap-3">
                  <RowCover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} wide={wide} />
                  <div className="min-w-0">
                    <div className={cn("truncate text-[15px] font-medium", current ? "text-primary" : "text-foreground")}>{item.title}</div>
                    <div className="truncate text-[13px] text-muted-foreground">{item.artist || item.channel}</div>
                  </div>
                </div>
                {showAlbum && <div className="truncate text-[13px] text-muted-foreground">{item.album ?? ""}</div>}
                <span className="flex justify-end">
                  <LikeButton item={item} reveal />
                </span>
                <span className="text-right text-[13px] tabular-nums text-muted-foreground">{duration(item.duration)}</span>
                <span className="flex justify-end">
                  <Dropdown
                    entries={menu}
                    trigger={
                      <button
                        type="button"
                        aria-label={`Mais opções para ${item.title}`}
                        className="flex h-8 w-8 items-center justify-center rounded-control text-muted-foreground hover:bg-border hover:text-foreground focus-visible:opacity-100 [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 [@media(hover:hover)]:group-focus-within:opacity-100 data-[state=open]:opacity-100"
                      >
                        <DotsThree weight="bold" className="h-5 w-5" />
                      </button>
                    }
                  />
                </span>
              </div>
            </RightClick>
          );
        })}
      </div>
    </div>
  );
}
