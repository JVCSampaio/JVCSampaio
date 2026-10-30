import { ArrowClockwise, CornersOut, Flame, X } from "@phosphor-icons/react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Segmented, Skeleton } from "@/components/ui/Controls";
import { Cover, RowCover } from "@/components/ui/Cover";
import { backend } from "@/lib/backend";
import { cn, duration } from "@/lib/format";
import type { Lyrics } from "@/lib/types";
import { useLibrary } from "@/state/library";
import { clearUpcoming, coverUrl, jump, removeFromQueue, seek, setCinema, setPanel, usePlayer, type Panel } from "@/state/player";
import { useStage } from "@/state/stage";

/** The right-hand panel: what plays (cover or the video itself), its lyrics or the queue. */
export function NowPlayingPanel() {
  const panel = usePlayer((s) => s.panel);
  const id = usePlayer((s) => s.queue[s.index] ?? null);
  const item = useLibrary((s) => (id ? s.items[id] : undefined));
  if (!panel) return null;

  return (
    <aside className="relative flex min-h-0 w-[380px] flex-col border-l border-border bg-surface" aria-label="Tocando agora">
      {item && item.kind !== "video" && <Ambient src={coverUrl(item)} />}
      <div className="relative flex items-center justify-between px-5 pt-4">
        <span className="text-[13px] font-medium text-faint-foreground">Tocando agora</span>
        <IconButton label="Fechar o painel" size="sm" onClick={() => setPanel(panel)}>
          <X className="h-4 w-4" />
        </IconButton>
      </div>
      {item ? (
        <div className="relative flex min-h-0 flex-1 flex-col px-5 pb-4 pt-3">
          {item.kind === "video" ? <VideoSlot /> : <Cover src={coverUrl(item)} kind="music" className="w-full" rounded="rounded-surface" />}
          <div className="mt-4 min-w-0">
            <h2 className="line-clamp-2 text-xl font-bold leading-tight text-foreground" data-selectable>
              {item.title}
            </h2>
            <p className="mt-1 truncate text-[15px] text-muted-foreground">{[item.artist || item.channel, item.album].filter(Boolean).join(", ")}</p>
          </div>
          <Segmented<Exclude<Panel, null>>
            label="Ver"
            className="mt-4 self-start"
            value={panel}
            onChange={(v) => usePlayer.setState({ panel: v })}
            options={[
              { value: "lyrics", label: "Letra" },
              { value: "queue", label: "Fila" },
            ]}
          />
          <div className="mt-3 min-h-0 flex-1 overflow-y-auto">{panel === "lyrics" ? <LyricsView id={item.id} /> : <QueueView />}</div>
        </div>
      ) : (
        <p className="relative px-5 pt-6 text-sm text-muted-foreground">Nada tocando.</p>
      )}
    </aside>
  );
}

/** The cover, blurred and dark, behind the panel: the color of what plays. */
function Ambient({ src }: { src: string | null }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  if (!src || failed) return null;
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden>
      <img src={src} alt="" onError={() => setFailed(true)} className="h-full w-full scale-150 object-cover opacity-25 blur-3xl saturate-150" />
      <div className="absolute inset-0 bg-linear-to-b from-surface/40 via-surface/80 to-surface" />
    </div>
  );
}

/** Reserves the 16:9 box where the shared video element is shown. */
function VideoSlot() {
  const ref = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const report = () => useStage.getState().setSlot(el.getBoundingClientRect());
    report();
    const ro = new ResizeObserver(report);
    ro.observe(el);
    window.addEventListener("resize", report);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", report);
      useStage.getState().setSlot(null);
    };
  }, []);
  return (
    <div ref={ref} className="group relative aspect-video w-full overflow-hidden rounded-surface bg-background">
      <button
        type="button"
        onClick={() => void setCinema(true)}
        aria-label="Ver em tela cheia"
        className="absolute right-2 top-2 z-30 flex h-8 w-8 items-center justify-center rounded-control bg-background/80 text-foreground opacity-80 hover:opacity-100 focus-visible:opacity-100"
      >
        <CornersOut className="h-4 w-4" />
      </button>
    </div>
  );
}

type LyricsState = { status: "loading" } | { status: "error"; message: string } | { status: "done"; lyrics: Lyrics | null };

function LyricsView({ id }: { id: string }) {
  const [state, setState] = useState<LyricsState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const position = usePlayer((s) => s.position);
  const listRef = useRef<HTMLOListElement>(null);

  useEffect(() => {
    let alive = true;
    setState({ status: "loading" });
    backend()
      .then((api) => api.lyrics(id))
      .then((lyrics) => alive && setState({ status: "done", lyrics }))
      .catch((e) => alive && setState({ status: "error", message: String(e) }));
    return () => {
      alive = false;
    };
  }, [id, attempt]);

  const lines = state.status === "done" ? state.lyrics?.lines ?? [] : [];
  const ms = position * 1000;
  let current = -1;
  for (let i = 0; i < lines.length; i++) if (lines[i].ms <= ms + 250) current = i;

  useEffect(() => {
    const el = listRef.current?.children[current] as HTMLElement | undefined;
    el?.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [current]);

  if (state.status === "loading") {
    return (
      <div className="space-y-3 pt-2">
        {[72, 88, 60, 80, 54].map((width, i) => (
          <div key={i} style={{ width: `${width}%` }}>
            <Skeleton className="h-5" />
          </div>
        ))}
      </div>
    );
  }
  if (state.status === "error") {
    return (
      <div className="pt-2 text-sm text-muted-foreground">
        <p>Não deu para buscar a letra agora.</p>
        <Button variant="ghost" size="sm" className="-ml-3 mt-2" icon={<ArrowClockwise className="h-4 w-4" />} onClick={() => setAttempt((a) => a + 1)}>
          Tentar de novo
        </Button>
      </div>
    );
  }
  const lyrics = state.lyrics;
  if (!lyrics) return <p className="pt-2 text-sm text-muted-foreground">Sem letra para essa música no LRCLIB.</p>;
  if (lyrics.instrumental) return <p className="pt-2 text-sm text-muted-foreground">Música instrumental.</p>;
  if (!lines.length) {
    return (
      <p className="whitespace-pre-line pt-2 text-[17px] leading-relaxed text-foreground" data-selectable>
        {lyrics.plain}
      </p>
    );
  }
  return (
    <ol ref={listRef} className="space-y-3 py-2 pr-2">
      {lines.map((line, i) => (
        <li key={i}>
          <button
            type="button"
            onClick={() => seek(line.ms / 1000)}
            className={cn(
              "text-left text-[19px] font-bold leading-snug transition-[color] [transition-duration:var(--dur-base)]",
              i === current ? "text-foreground" : "text-faint-foreground hover:text-muted-foreground",
            )}
          >
            {line.text || "♪"}
          </button>
        </li>
      ))}
    </ol>
  );
}

function QueueView() {
  const queue = usePlayer((s) => s.queue);
  const index = usePlayer((s) => s.index);
  const playing = usePlayer((s) => s.playing);
  const items = useLibrary((s) => s.items);
  const upcoming = queue.slice(index + 1, index + 101);
  const wide = upcoming.some((qid) => items[qid]?.kind === "video");

  return (
    <div className="pb-2">
      <ol className="space-y-0.5">
        {upcoming.map((qid, k) => {
          const it = items[qid];
          if (!it) return null;
          const at = index + 1 + k;
          return (
            <li key={`${qid}-${at}`} className="group flex items-center gap-3 rounded-control px-2 py-1.5 hover:bg-raised">
              <button type="button" onClick={() => jump(at)} className="flex min-w-0 flex-1 items-center gap-3 text-left">
                <RowCover src={coverUrl(it)} kind={it.kind === "video" ? "video" : "music"} wide={wide} />
                <span className="min-w-0">
                  <span className="block truncate text-[15px] text-foreground">{it.title}</span>
                  <span className="block truncate text-[13px] text-muted-foreground">
                    {it.artist}, {duration(it.duration)}
                  </span>
                </span>
              </button>
              <IconButton
                label="Tirar da fila"
                size="sm"
                onClick={() => removeFromQueue(at)}
                className="[@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 focus-visible:opacity-100"
              >
                <X className="h-4 w-4" />
              </IconButton>
            </li>
          );
        })}
      </ol>
      {upcoming.length === 0 ? (
        <p className="pt-2 text-sm text-muted-foreground">
          {playing ? <Flame weight="fill" className="mr-1 inline h-4 w-4 text-primary" /> : null}
          Depois desta, a fila acaba.
        </p>
      ) : (
        <Button variant="ghost" size="sm" className="mt-2" onClick={clearUpcoming}>
          Limpar a fila
        </Button>
      )}
    </div>
  );
}
