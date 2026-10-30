import { CaretDown, CaretUp, Clock, DotsSixVertical, DotsThree, Flame } from "@phosphor-icons/react";
import { defaultRangeExtractor, useVirtualizer } from "@tanstack/react-virtual";
import { useRef, type ReactNode } from "react";
import { RowCover } from "@/components/ui/Cover";
import { backend } from "@/lib/backend";
import { Dropdown, RightClick } from "@/components/ui/Menu";
import { cn, duration } from "@/lib/format";
import type { Item, Playlist } from "@/lib/types";
import { useMainScroll } from "@/lib/useMainScroll";
import { dropSide, moveId, useReorder } from "@/lib/useReorder";
import { useLibrary } from "@/state/library";
import { coverUrl, playList, usePlayer } from "@/state/player";
import { toast } from "@/state/toasts";
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

/** Puts a playlist in a new order at once and tells the engine; if the engine refuses, the
 *  saved order comes back. */
function reorderPlaylist(playlist: Playlist, itemIds: string[]) {
  useLibrary.setState((s) => ({ playlists: s.playlists.map((p) => (p.id === playlist.id ? { ...p, itemIds } : p)) }));
  void backend()
    .then((api) => api.playlistReorder(playlist.id, itemIds))
    .catch((e) => {
      toast(String(e), "error");
      void useLibrary.getState().reloadPlaylists();
    });
}

/** The song list of the library, an artist, an album or a playlist. Virtualized: thousands of
 *  rows scroll like ten. Double click or Enter plays from that row on. In a playlist shown in
 *  its own order, rows are dragged by their number to reorder it. */
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
  const { scroller, margin } = useMainScroll(listRef);
  const playlists = useLibrary((s) => s.playlists);
  const playing = usePlayer((s) => s.playing);
  const nowId = usePlayer((s) => s.queue[s.index] ?? null);
  const reorderable = !!inPlaylist && (!sort || sort.key === "added");
  const { drag, handle, keyMove } = useReorder({
    count: items.length,
    indexAt: (y) => Math.floor((y - (listRef.current?.getBoundingClientRect().top ?? 0)) / ROW),
    scroller: () => scroller,
    onMove: (from, to) => {
      if (inPlaylist) reorderPlaylist(inPlaylist, moveId(inPlaylist.itemIds, items[from].id, items[to].id, to > from));
    },
  });
  // The dragged row holds the pointer: it stays drawn even when scrolled out of view.
  const dragging = useRef<number | null>(null);
  dragging.current = drag?.from ?? null;

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => scroller,
    estimateSize: () => ROW,
    overscan: 12,
    scrollMargin: margin,
    rangeExtractor: (range) => {
      const rows = defaultRangeExtractor(range);
      const kept = dragging.current;
      return kept == null || rows.includes(kept) ? rows : [...rows, kept].sort((a, b) => a - b);
    },
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
          const side = dropSide(drag, row.index);
          return (
            <RightClick key={item.id} entries={menu}>
              <div
                role="button"
                tabIndex={0}
                aria-label={`${item.title}, ${item.artist}`}
                aria-keyshortcuts={reorderable ? "Alt+ArrowUp Alt+ArrowDown" : undefined}
                onDoubleClick={() => playList(ids, row.index)}
                onKeyDown={(e) => {
                  if (reorderable && keyMove(row.index, e)) return;
                  if (e.key === "Enter") playList(ids, row.index);
                }}
                className={cn(
                  "group absolute left-0 right-0 grid items-center gap-4 rounded-control px-3",
                  "transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised focus-visible:bg-raised",
                  drag?.from === row.index && "opacity-50",
                  cols,
                )}
                style={{ height: ROW, transform: `translateY(${row.start - margin}px)` }}
              >
                {side && <span aria-hidden className={cn("absolute inset-x-3 h-0.5 rounded-full bg-primary", side === "above" ? "-top-px" : "-bottom-px")} />}
                <span className="relative flex h-full items-center justify-end text-right text-sm tabular-nums text-faint-foreground">
                  {current ? (
                    <Flame weight="fill" className={cn("h-4 w-4 text-primary", playing && "flame-live")} aria-label="Tocando agora" />
                  ) : (
                    <span className={cn(reorderable && "[@media(hover:hover)]:group-hover:invisible")}>{row.index + 1}</span>
                  )}
                  {reorderable && (
                    <span
                      {...handle(row.index)}
                      aria-hidden
                      data-drag-handle
                      className="absolute inset-y-0 -left-2 -right-1 flex cursor-grab touch-none items-center justify-end text-muted-foreground active:cursor-grabbing [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100"
                    >
                      <DotsSixVertical weight="bold" className="h-4 w-4" />
                    </span>
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
