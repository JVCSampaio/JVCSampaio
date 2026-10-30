import { CaretLeft, Play, Shuffle } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { Button, IconButton, KeyCombo } from "@/components/ui/Button";
import { Cover } from "@/components/ui/Cover";
import { cn } from "@/lib/format";
import type { Item } from "@/lib/types";
import { coverUrl, playList } from "@/state/player";
import { useRouter } from "@/state/router";

/** The top of an artist, an album or a playlist: its picture, its name, what it holds. */
export function Hero({ cover, label, title, meta, actions }: { cover: ReactNode; label: string; title: string; meta: ReactNode; actions?: ReactNode }) {
  return (
    <header className="flex items-end gap-8 px-8 pb-8 pt-4">
      {cover}
      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-medium text-muted-foreground">{label}</p>
        <h1 className="mt-1 line-clamp-2 font-display text-6xl font-semibold leading-[1.05] text-foreground" data-selectable>
          {title}
        </h1>
        <div className="mt-3 text-[15px] text-muted-foreground">{meta}</div>
        {actions && <div className="mt-6 flex items-center gap-2">{actions}</div>}
      </div>
    </header>
  );
}

/** Tocar plays in order from the first; Aleatório shuffles the same list. */
export function PlayActions({ items, extra }: { items: Item[]; extra?: ReactNode }) {
  const ids = items.map((i) => i.id);
  return (
    <>
      <Button variant="primary" size="lg" icon={<Play weight="fill" className="h-4 w-4" />} disabled={!ids.length} onClick={() => playList(ids, 0, { shuffle: false })}>
        Tocar
      </Button>
      <Button size="lg" icon={<Shuffle weight="bold" className="h-4 w-4" />} disabled={ids.length < 2} onClick={() => playList(ids, "random", { shuffle: true })}>
        Aleatório
      </Button>
      {extra}
    </>
  );
}

export function BackBar() {
  const canGoBack = useRouter((s) => s.history.length > 0);
  const back = useRouter((s) => s.back);
  return (
    <div className="h-12 px-6 pt-4">
      {canGoBack && (
        <IconButton label="Voltar" shortcut={<KeyCombo mod="Alt" arrow="left" />} onClick={back}>
          <CaretLeft weight="bold" className="h-5 w-5" />
        </IconButton>
      )}
    </div>
  );
}

/** Four covers in a square for a playlist, or one cover when there are fewer. */
export function Mosaic({ items, className }: { items: Item[]; className: string }) {
  const covers = items.map((i) => coverUrl(i)).filter((c): c is string => !!c);
  const unique = [...new Set(covers)];
  if (unique.length < 4) return <Cover src={unique[0]} kind="playlist" className={className} rounded="rounded-surface" />;
  return (
    <div className={cn("grid shrink-0 grid-cols-2 overflow-hidden rounded-surface", className)}>
      {unique.slice(0, 4).map((src) => (
        <Cover key={src} src={src} kind="music" className="w-full" rounded="rounded-none" />
      ))}
    </div>
  );
}
