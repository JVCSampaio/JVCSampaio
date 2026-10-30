import { Flame, Play } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { Cover } from "@/components/ui/Cover";
import { RightClick } from "@/components/ui/Menu";
import { cn, duration } from "@/lib/format";
import type { Item } from "@/lib/types";
import { useLibrary } from "@/state/library";
import { coverUrl, playList, usePlayer } from "@/state/player";
import { itemMenu } from "./itemMenu";

export function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section>
      <div className="mb-4 flex items-end justify-between gap-4">
        <h2 className="text-xl font-bold text-foreground">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function PlayingMark({ id }: { id: string }) {
  const current = usePlayer((s) => s.queue[s.index] === id);
  const playing = usePlayer((s) => s.playing);
  if (!current) return null;
  return <Flame weight="fill" aria-label="Tocando agora" className={cn("h-4 w-4 shrink-0 text-primary", playing && "flame-live")} />;
}

/** A song as a square tile. Click plays the row from it. */
export function SongTile({ item, row, index }: { item: Item; row: Item[]; index: number }) {
  const playlists = useLibrary((s) => s.playlists);
  return (
    <RightClick entries={itemMenu(item, playlists)}>
      <button
        type="button"
        onClick={() => playList(row.map((i) => i.id), index)}
        className="group w-[168px] shrink-0 rounded-surface p-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised focus-visible:bg-raised"
      >
        <div className="relative">
          <Cover src={coverUrl(item)} kind="music" className="w-full" rounded="rounded-control" />
          <span className="absolute bottom-2 right-2 flex h-9 w-9 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg shadow-black/40 transition-opacity [transition-duration:var(--dur-fast)] [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 group-focus-visible:opacity-100">
            <Play weight="fill" className="h-4 w-4" />
          </span>
        </div>
        <div className="mt-2 flex items-center gap-1.5">
          <PlayingMark id={item.id} />
          <span className="truncate text-[15px] font-medium text-foreground">{item.title}</span>
        </div>
        <div className="truncate text-[13px] text-muted-foreground">{item.artist}</div>
      </button>
    </RightClick>
  );
}

/** A video as a 16:9 tile with its length on the image. */
export function VideoTile({ item, row, index, width = "w-[264px]" }: { item: Item; row: Item[]; index: number; width?: string }) {
  const playlists = useLibrary((s) => s.playlists);
  return (
    <RightClick entries={itemMenu(item, playlists)}>
      <button
        type="button"
        onClick={() => playList(row.map((i) => i.id), index)}
        className={cn("group shrink-0 rounded-surface p-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised focus-visible:bg-raised", width)}
      >
        <div className="relative">
          <Cover src={coverUrl(item)} kind="video" className="w-full" rounded="rounded-control" />
          {item.duration ? (
            <span className="absolute bottom-1.5 right-1.5 max-w-[calc(100%-12px)] rounded-[4px] bg-background/85 px-1.5 text-[12px] tabular-nums text-foreground">
              {duration(item.duration)}
            </span>
          ) : null}
        </div>
        <div className="mt-2 flex items-start gap-1.5">
          <PlayingMark id={item.id} />
          <span className="line-clamp-2 text-[15px] font-medium leading-snug text-foreground">{item.title}</span>
        </div>
        <div className="mt-0.5 truncate text-[13px] text-muted-foreground">{item.channel || item.artist}</div>
      </button>
    </RightClick>
  );
}

/** A horizontal row that scrolls on its own, never the page. */
export function Shelf({ children }: { children: ReactNode }) {
  return <div className="-mx-2 flex gap-1 overflow-x-auto pb-2">{children}</div>;
}
