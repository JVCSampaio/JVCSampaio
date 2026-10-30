import {
  ArrowClockwise,
  ClockCounterClockwise,
  DownloadSimple,
  FilmStrip,
  MagnifyingGlass,
  MusicNotes,
  Play,
  WarningCircle,
  X,
} from "@phosphor-icons/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { ProgressBar, Segmented, Skeleton, TextField } from "@/components/ui/Controls";
import { Cover, RowCover } from "@/components/ui/Cover";
import { backend } from "@/lib/backend";
import { count, duration, percent, totalDuration } from "@/lib/format";
import { searchLibrary } from "@/lib/match";
import { WAIT } from "@/lib/motion";
import type { Entry, Item, Kind, Progress, Resolved } from "@/lib/types";
import { useDelayed } from "@/lib/useDelayed";
import { useLibrary } from "@/state/library";
import { coverUrl, playEntry, playItem, playList } from "@/state/player";
import { go } from "@/state/router";
import { toast } from "@/state/toasts";

type Phase =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "done"; query: string; result: Resolved; entries: Entry[] };

const RECENT = "requiem.search.recent";
const KIND = "requiem.search.kind";

const looksLikeLink = (text: string) => /^https?:\/\/|youtu\.?be|youtube\.com/i.test(text);

function readRecent(): string[] {
  try {
    const saved = JSON.parse(localStorage.getItem(RECENT) ?? "[]");
    return Array.isArray(saved) ? saved.filter((q) => typeof q === "string").slice(0, 8) : [];
  } catch {
    return [];
  }
}

function writeRecent(list: string[]) {
  try {
    localStorage.setItem(RECENT, JSON.stringify(list));
  } catch {
    // Recent searches are a convenience; losing them is fine.
  }
}

function readKind(): Kind {
  try {
    return localStorage.getItem(KIND) === "video" ? "video" : "music";
  } catch {
    return "music";
  }
}

export function SearchPage({ initial }: { initial?: string }) {
  const [text, setText] = useState(initial ?? "");
  const [submitted, setSubmitted] = useState((initial ?? "").trim());
  const [kind, setKind] = useState<Kind>(readKind);
  const [phase, setPhase] = useState<Phase>({ status: "idle" });
  const [attempt, setAttempt] = useState(0);
  const [recent, setRecent] = useState(readRecent);
  const seq = useRef(0);
  const link = looksLikeLink(submitted);
  const showSkeleton = useDelayed(phase.status === "loading", WAIT.showAfter);
  const items = useLibrary((s) => s.items);
  const mine = useMemo(() => (link ? [] : searchLibrary(Object.values(items), submitted)), [items, submitted, link]);

  // Words search when typing stops; a pasted link goes at once. Enter skips the wait.
  useEffect(() => {
    const t = text.trim();
    const timer = window.setTimeout(() => setSubmitted(t), looksLikeLink(t) ? 0 : 450);
    return () => window.clearTimeout(timer);
  }, [text]);

  useEffect(() => {
    try {
      localStorage.setItem(KIND, kind);
    } catch {
      // Same as above.
    }
  }, [kind]);

  // The kind changes a word search; for a link it only changes what gets downloaded.
  useEffect(() => {
    const query = submitted;
    const id = ++seq.current;
    if (!query) {
      setPhase({ status: "idle" });
      return;
    }
    setPhase({ status: "loading" });
    (async () => {
      const api = await backend();
      const result = await api.resolve(query);
      const entries =
        result.type === "query" ? await api.search(result.text, kind) : result.type === "video" ? [result.entry] : result.entries;
      if (id !== seq.current) return;
      setPhase({ status: "done", query, result, entries });
      if (result.type === "query" && entries.length) {
        const next = [query, ...readRecent().filter((q) => q.toLowerCase() !== query.toLowerCase())].slice(0, 8);
        writeRecent(next);
        setRecent(next);
      }
    })().catch((e) => {
      if (id === seq.current) setPhase({ status: "error", message: String(e) });
    });
  }, [submitted, link ? null : kind, attempt]);

  const run = (query: string) => {
    setText(query);
    setSubmitted(query.trim());
    setAttempt((a) => a + 1);
  };

  return (
    <div className="px-8 pb-12 pt-8">
      <h1 className="font-display text-5xl font-semibold text-foreground">Buscar</h1>
      <form
        className="mt-6 flex flex-wrap items-center gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          run(text);
        }}
      >
        <TextField
          id="search-input"
          autoFocus
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Uma música, um artista ou um link do YouTube"
          aria-label="Buscar no YouTube"
          spellCheck={false}
          className="h-12 min-w-80 max-w-2xl flex-1 [&_input]:text-[17px]"
          icon={<MagnifyingGlass className="h-5 w-5 shrink-0" />}
          end={
            text ? (
              <IconButton label="Limpar a busca" size="sm" onClick={() => run("")}>
                <X className="h-4 w-4" />
              </IconButton>
            ) : null
          }
        />
        <Segmented<Kind>
          label="Buscar por"
          value={kind}
          onChange={setKind}
          options={[
            { value: "music", label: "Músicas" },
            { value: "video", label: "Vídeos" },
          ]}
        />
      </form>

      <div className="mt-8" data-search-state={phase.status}>
        {phase.status !== "idle" && mine.length > 0 && <InLibrary items={mine} />}
        {phase.status === "idle" && (
          <Idle
            recent={recent}
            onPick={run}
            onForget={() => {
              writeRecent([]);
              setRecent([]);
            }}
          />
        )}
        {phase.status === "loading" && showSkeleton && <ResultsSkeleton video={kind === "video"} />}
        {phase.status === "error" && (
          <div className="flex max-w-xl items-start gap-3 text-[15px]">
            <WarningCircle weight="fill" className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
            <div>
              <p className="text-foreground">{phase.message}</p>
              <Button variant="ghost" size="sm" className="-ml-3 mt-2" icon={<ArrowClockwise className="h-4 w-4" />} onClick={() => setAttempt((a) => a + 1)}>
                Tentar de novo
              </Button>
            </div>
          </div>
        )}
        {phase.status === "done" && <Results phase={phase} kind={kind} titled={mine.length > 0} />}
      </div>
    </div>
  );
}

function Idle({ recent, onPick, onForget }: { recent: string[]; onPick: (q: string) => void; onForget: () => void }) {
  if (!recent.length) {
    return (
      <div className="max-w-xl pt-8">
        <h2 className="font-display text-4xl font-semibold text-foreground">O que vamos invocar?</h2>
        <p className="mt-2 text-[15px] text-muted-foreground">
          Digite o nome de uma música ou cole o link de um vídeo ou de uma playlist do YouTube. O que você baixar fica guardado no seu computador.
        </p>
      </div>
    );
  }
  return (
    <section className="max-w-2xl">
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-xl font-bold text-foreground">Buscas recentes</h2>
        <Button variant="ghost" size="sm" onClick={onForget}>
          Esquecer
        </Button>
      </div>
      <ul>
        {recent.map((q) => (
          <li key={q}>
            <button
              type="button"
              onClick={() => onPick(q)}
              className="flex h-10 w-full items-center gap-3 rounded-control px-3 text-left text-[15px] text-muted-foreground transition-[background-color,color] [transition-duration:var(--dur-fast)] hover:bg-raised hover:text-foreground"
            >
              <ClockCounterClockwise className="h-4 w-4 shrink-0" />
              <span className="truncate">{q}</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ResultsSkeleton({ video }: { video: boolean }) {
  return (
    <ul className="max-w-4xl space-y-1" aria-label="Buscando">
      {[64, 48, 72, 56, 40, 60].map((w, i) => (
        <li key={i} className="flex items-center gap-4 px-3 py-2">
          <Skeleton className={video ? "h-12 w-[85px]" : "h-12 w-12"} />
          <div className="flex-1 space-y-2">
            <div style={{ width: `${w}%` }}>
              <Skeleton className="h-4" />
            </div>
            <div style={{ width: `${w / 2}%` }}>
              <Skeleton className="h-3" />
            </div>
          </div>
          <Skeleton className="h-8 w-24" />
        </li>
      ))}
    </ul>
  );
}

/** Songs already in the library that match the words: they play at once, with no download. */
function InLibrary({ items }: { items: Item[] }) {
  const ids = items.map((i) => i.id);
  const wide = items.some((i) => i.kind === "video");
  return (
    <section className="mb-8 max-w-4xl" data-in-library>
      <h2 className="mb-2 px-3 text-[13px] font-medium text-faint-foreground">Na sua biblioteca</h2>
      <ul className="space-y-0.5">
        {items.map((item, i) => (
          <li key={item.id}>
            <button
              type="button"
              onClick={() => playList(ids, i, { shuffle: false })}
              className="grid w-full grid-cols-[auto_minmax(0,1fr)_56px_minmax(150px,auto)] items-center gap-4 rounded-control px-3 py-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised"
            >
              <RowCover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} wide={wide} />
              <span className="min-w-0">
                <span className="block truncate text-[15px] font-medium text-foreground">{item.title}</span>
                <span className="block truncate text-[13px] text-muted-foreground">
                  {[item.artist || item.channel, item.album].filter(Boolean).join(", ")}
                </span>
              </span>
              <span className="text-right text-[13px] tabular-nums text-muted-foreground">{duration(item.duration)}</span>
              <span />
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Results({ phase, kind, titled }: { phase: Extract<Phase, { status: "done" }>; kind: Kind; titled: boolean }) {
  const { result, entries, query } = phase;
  if (!entries.length) {
    return (
      <div className="max-w-xl">
        <p className="text-[15px] text-foreground">Nada encontrado para "{query}".</p>
        <p className="mt-1 text-sm text-muted-foreground">Tente outras palavras, o nome do artista junto, ou cole o link do vídeo.</p>
      </div>
    );
  }
  const source = result.type === "query" ? "search" : "link";
  return (
    <div className="max-w-4xl">
      {result.type === "playlist" && <PlaylistImport title={result.title} entries={entries} kind={kind} />}
      {result.type === "video" && <p className="mb-2 px-3 text-[13px] text-faint-foreground">Do link que você colou</p>}
      {result.type === "query" && titled && <h2 className="mb-2 px-3 text-[13px] font-medium text-faint-foreground">No YouTube</h2>}
      <ul className="space-y-0.5">
        {entries.map((entry) => (
          <ResultRow key={entry.id} entry={entry} kind={kind} source={source} />
        ))}
      </ul>
    </div>
  );
}

function PlaylistImport({ title, entries, kind }: { title: string; entries: Entry[]; kind: Kind }) {
  const [busy, setBusy] = useState(false);
  const total = entries.reduce((sum, e) => sum + (e.duration ?? 0), 0);
  const importAll = async () => {
    setBusy(true);
    try {
      const id = await (await backend()).downloadMany(entries, kind, title);
      toast(`${count(entries.length, "item foi", "itens foram")} para a fila.`, "info", id != null ? { label: "Abrir a playlist", run: () => go({ name: "playlist", id }) } : undefined);
    } catch (e) {
      toast(String(e), "error");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="mb-6 flex items-center gap-6 rounded-surface border border-border bg-card p-4">
      <Cover src={entries[0]?.thumbnail || null} kind="playlist" className="w-24" />
      <div className="min-w-0 flex-1">
        <p className="text-[13px] text-muted-foreground">Playlist do YouTube</p>
        <h2 className="truncate text-2xl font-bold text-foreground" data-selectable>
          {title}
        </h2>
        <p className="text-sm text-muted-foreground">
          {count(entries.length, "item", "itens")}
          {total > 0 ? `, ${totalDuration(total)}` : ""}
        </p>
      </div>
      <Button variant="primary" icon={<DownloadSimple weight="bold" className="h-4 w-4" />} disabled={busy} onClick={importAll}>
        Baixar todas e criar playlist
      </Button>
    </div>
  );
}

function ResultRow({ entry, kind, source }: { entry: Entry; kind: Kind; source: string }) {
  const item = useLibrary((s) => s.items[entry.id]);
  const progress = useLibrary((s) => s.progress[entry.id]);
  const video = kind === "video";
  const failed = item?.status === "failed";
  return (
    <li
      data-result={entry.id}
      onDoubleClick={() => void playEntry(entry, kind, source)}
      className="group grid grid-cols-[auto_minmax(0,1fr)_56px_minmax(150px,auto)] items-center gap-4 rounded-control px-3 py-2 transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised"
    >
      <button
        type="button"
        data-action="play"
        aria-label={`Tocar ${entry.title}`}
        onClick={() => void playEntry(entry, kind, source)}
        className="relative shrink-0 rounded-[4px]"
      >
        <Cover src={entry.thumbnail || null} kind={video ? "video" : "music"} className={video ? "w-[85px]" : "w-12"} rounded="rounded-[4px]" />
        <span className="absolute inset-0 flex items-center justify-center rounded-[4px] bg-background/60 text-foreground transition-opacity [transition-duration:var(--dur-fast)] [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 [@media(hover:hover)]:group-focus-within:opacity-100">
          <Play weight="fill" className="h-5 w-5" />
        </span>
      </button>
      <div className="min-w-0">
        <div className="flex items-center gap-1.5">
          {entry.isShort && <span className="shrink-0 rounded-[4px] border border-border-strong px-1 text-[11px] text-muted-foreground">Short</span>}
          <span className="truncate text-[15px] font-medium text-foreground">{entry.title}</span>
        </div>
        {failed && item.error ? (
          <div className="truncate text-[13px] text-destructive">{item.error}</div>
        ) : (
          <div className="truncate text-[13px] text-muted-foreground">{entry.artist || entry.channel}</div>
        )}
      </div>
      <span className="text-right text-[13px] tabular-nums text-muted-foreground">{duration(entry.duration)}</span>
      <div className="flex justify-end">
        <RowAction entry={entry} kind={kind} source={source} item={item} progress={progress} />
      </div>
    </li>
  );
}

/** What can be done with a result right now: download it, follow it, cancel it, play it. */
function RowAction({ entry, kind, source, item, progress }: { entry: Entry; kind: Kind; source: string; item?: Item; progress?: Progress }) {
  const [busy, setBusy] = useState(false);
  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      toast(String(e), "error");
    } finally {
      setBusy(false);
    }
  };
  const cancel = (
    <IconButton label="Cancelar o download" size="sm" onClick={() => item && act(async () => (await backend()).downloadCancel(item.id))}>
      <X className="h-4 w-4" />
    </IconButton>
  );

  switch (item?.status) {
    case "done":
      return (
        <Button size="sm" icon={<Play weight="fill" className="h-3.5 w-3.5" />} onClick={() => playItem(item.id)}>
          Tocar
        </Button>
      );
    case "downloading":
      return (
        <div className="flex w-[150px] items-center gap-2">
          {progress?.fraction != null ? (
            <>
              <ProgressBar fraction={progress.fraction} className="flex-1" />
              <span className="w-9 text-right text-[13px] tabular-nums text-muted-foreground">{percent(progress.fraction)}</span>
            </>
          ) : (
            <span className="flex-1 truncate text-[13px] text-muted-foreground">{progress?.stage ?? "Começando"}</span>
          )}
          {cancel}
        </div>
      );
    case "queued":
      return (
        <div className="flex items-center gap-2">
          <span className="text-[13px] text-muted-foreground">Na fila</span>
          {cancel}
        </div>
      );
    case "failed":
      return (
        <Button size="sm" variant="ghost" icon={<ArrowClockwise className="h-4 w-4" />} disabled={busy} onClick={() => act(async () => (await backend()).downloadRetry(item.id))}>
          Tentar de novo
        </Button>
      );
    default:
      return (
        <Button
          size="sm"
          data-action="download"
          icon={kind === "video" ? <FilmStrip className="h-4 w-4" /> : <MusicNotes className="h-4 w-4" />}
          disabled={busy}
          onClick={() => act(async () => (await backend()).download(entry, kind, source))}
        >
          Baixar
        </Button>
      );
  }
}
