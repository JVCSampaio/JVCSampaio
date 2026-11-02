import { ArrowClockwise, Play, WarningCircle, YoutubeLogo } from "@phosphor-icons/react";
import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { ProgressBar, Segmented, Skeleton, Switch } from "@/components/ui/Controls";
import { Cover } from "@/components/ui/Cover";
import { EmptyState } from "@/components/ui/EmptyState";
import { backend } from "@/lib/backend";
import { cn, count, duration, percent } from "@/lib/format";
import { WAIT } from "@/lib/motion";
import type { Entry, Item } from "@/lib/types";
import { useDelayed } from "@/lib/useDelayed";
import { isAccountConnected, useLibrary } from "@/state/library";
import { playItem } from "@/state/player";
import { go } from "@/state/router";
import { toast } from "@/state/toasts";

type Source = "videos" | "music";
type Load = { status: "loading" } | { status: "error"; message: string } | { status: "done"; entries: Entry[] };

/** Rows drawn at first; long lists grow on demand. */
const PAGE = 200;

/**
 * Everything the person liked on YouTube or YouTube Music. They choose what to download; each
 * song or video goes to the library as soon as its file lands.
 */
export function YouTubeLikesPage() {
  const settings = useLibrary((s) => s.settings);
  const save = useLibrary((s) => s.saveSettings);
  const items = useLibrary((s) => s.items);
  const [source, setSource] = useState<Source>("videos");
  const [attempt, setAttempt] = useState(0);
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [shown, setShown] = useState(PAGE);
  const [busy, setBusy] = useState(false);
  const connected = isAccountConnected(settings);
  const showSkeleton = useDelayed(load.status === "loading", WAIT.showAfter);

  useEffect(() => {
    if (!connected) return;
    let alive = true;
    setLoad({ status: "loading" });
    setChosen(new Set());
    setShown(PAGE);
    backend()
      .then((api) => api.youtubeLikes(source === "music"))
      .then((entries) => alive && setLoad({ status: "done", entries }))
      .catch((e) => alive && setLoad({ status: "error", message: String(e) }));
    return () => {
      alive = false;
    };
  }, [source, attempt, connected]);

  const entries = load.status === "done" ? load.entries : [];
  const missing = useMemo(() => entries.filter((e) => !items[e.id] || ["failed", "skipped", "removed"].includes(items[e.id].status)), [entries, items]);
  const inLibrary = entries.length - missing.length;

  if (!connected) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <EmptyState
          icon={<YoutubeLogo weight="duotone" />}
          title="Conecte o YouTube"
          actions={
            <Button variant="primary" onClick={() => go({ name: "settings", section: "account" })}>
              Conectar o YouTube
            </Button>
          }
        >
          Com a conta conectada, aqui aparecem todos os vídeos e músicas que você curtiu, e você escolhe quais baixar.
        </EmptyState>
      </div>
    );
  }

  const kind = source === "music" ? "music" : "pick";
  const origin = source === "music" ? "liked_music" : "liked";
  const download = async (list: Entry[]) => {
    setBusy(true);
    try {
      const api = await backend();
      for (const entry of list) await api.download(entry, kind, origin);
      setChosen(new Set());
      toast(`${count(list.length, "item foi", "itens foram")} para a fila. Cada um entra na biblioteca assim que chegar.`);
    } catch (e) {
      toast(String(e), "error");
    } finally {
      setBusy(false);
    }
  };
  const toggle = (id: string) =>
    setChosen((now) => {
      const next = new Set(now);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const autoKey = source === "music" ? "syncMusicLikes" : "syncLikes";
  const picked = missing.filter((e) => chosen.has(e.id));

  return (
    <div className="px-8 pb-12 pt-8">
      <h1 className="font-display text-5xl font-semibold text-foreground">Curtidas no YouTube</h1>
      <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
        <Segmented<Source>
          label="Mostrar"
          value={source}
          onChange={setSource}
          options={[
            { value: "videos", label: "Vídeos curtidos" },
            { value: "music", label: "YouTube Music" },
          ]}
        />
        {settings && (
          <label htmlFor="auto-likes" className="flex items-center gap-3 text-sm text-muted-foreground">
            Baixar as novas curtidas sozinho
            <Switch id="auto-likes" checked={settings[autoKey]} onChange={(v) => void save({ ...settings, [autoKey]: v })} />
          </label>
        )}
      </div>

      {load.status === "loading" && showSkeleton && (
        <ul className="mt-8 max-w-4xl space-y-1" aria-label="Carregando">
          {[60, 45, 70, 52, 38, 64].map((w, i) => (
            <li key={i} className="flex items-center gap-4 px-3 py-2">
              <Skeleton className="h-12 w-[85px]" />
              <div className="flex-1 space-y-2">
                <div style={{ width: `${w}%` }}>
                  <Skeleton className="h-4" />
                </div>
                <div style={{ width: `${w / 2}%` }}>
                  <Skeleton className="h-3" />
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {load.status === "error" && (
        <div className="mt-8 flex max-w-xl items-start gap-3 text-[15px]">
          <WarningCircle weight="fill" className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
          <div>
            <p className="text-foreground">{load.message}</p>
            <Button variant="ghost" size="sm" className="-ml-3 mt-2" icon={<ArrowClockwise className="h-4 w-4" />} onClick={() => setAttempt((a) => a + 1)}>
              Tentar de novo
            </Button>
          </div>
        </div>
      )}

      {load.status === "done" && !entries.length && (
        <p className="mt-8 max-w-xl text-[15px] text-muted-foreground">
          {source === "music" ? "Nenhuma música curtida no YouTube Music ainda." : "Nenhum vídeo curtido no YouTube ainda."}
        </p>
      )}

      {load.status === "done" && entries.length > 0 && (
        <div className="max-w-4xl">
          <div className="sticky top-0 z-10 -mx-3 mt-6 flex flex-wrap items-center gap-3 bg-background/95 px-3 py-3">
            <span className="text-sm text-muted-foreground">
              {count(entries.length, "curtida", "curtidas")}, {inLibrary} na biblioteca
            </span>
            <span className="flex-1" />
            {missing.length > 0 && (
              <Button variant="ghost" size="sm" onClick={() => setChosen(picked.length === missing.length ? new Set() : new Set(missing.map((e) => e.id)))}>
                {picked.length === missing.length ? "Limpar a seleção" : "Selecionar todas que faltam"}
              </Button>
            )}
            <Button variant="primary" disabled={!picked.length || busy} onClick={() => void download(picked)} data-action="download-chosen">
              {picked.length ? `Baixar ${count(picked.length, "selecionada", "selecionadas")}` : "Baixar selecionadas"}
            </Button>
          </div>
          <ul className="space-y-0.5">
            {entries.slice(0, shown).map((entry) => (
              <LikeRow key={entry.id} entry={entry} item={items[entry.id]} chosen={chosen.has(entry.id)} onToggle={() => toggle(entry.id)} onDownload={() => void download([entry])} />
            ))}
          </ul>
          {entries.length > shown && (
            <Button variant="ghost" className="mt-4" onClick={() => setShown((n) => n + PAGE)}>
              Mostrar mais {Math.min(PAGE, entries.length - shown)}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

function LikeRow({ entry, item, chosen, onToggle, onDownload }: { entry: Entry; item?: Item; chosen: boolean; onToggle: () => void; onDownload: () => void }) {
  const progress = useLibrary((s) => s.progress[entry.id]);
  const status = item?.status;
  const open = !item || status === "failed" || status === "skipped" || status === "removed";
  return (
    <li data-like={entry.id} className={cn("grid grid-cols-[20px_auto_minmax(0,1fr)_56px_minmax(150px,auto)] items-center gap-4 rounded-control px-3 py-2 hover:bg-raised", chosen && "bg-raised")}>
      <input
        type="checkbox"
        checked={chosen}
        disabled={!open}
        onChange={onToggle}
        aria-label={`Escolher ${entry.title}`}
        className="h-4 w-4 accent-[hsl(var(--primary))] disabled:opacity-30"
      />
      <Cover src={entry.thumbnail || null} kind="video" className="w-[85px]" rounded="rounded-[4px]" />
      <div className="min-w-0">
        <div className="truncate text-[15px] font-medium text-foreground">{entry.title}</div>
        {status === "failed" && item?.error ? (
          <div className="truncate text-[13px] text-destructive">{item.error}</div>
        ) : (
          <div className="truncate text-[13px] text-muted-foreground">{entry.artist || entry.channel}</div>
        )}
      </div>
      <span className="text-right text-[13px] tabular-nums text-muted-foreground">{duration(entry.duration)}</span>
      <div className="flex justify-end">
        {status === "done" ? (
          <Button size="sm" variant="ghost" icon={<Play weight="fill" className="h-3.5 w-3.5" />} onClick={() => playItem(entry.id)}>
            Na biblioteca
          </Button>
        ) : status === "downloading" ? (
          <div className="flex w-[150px] items-center gap-2">
            <ProgressBar fraction={progress?.fraction ?? 0} className="flex-1" />
            <span className="w-9 text-right text-[13px] tabular-nums text-muted-foreground">{percent(progress?.fraction)}</span>
          </div>
        ) : status === "queued" ? (
          <span className="text-[13px] text-muted-foreground">Na fila</span>
        ) : (
          <Button size="sm" onClick={onDownload}>
            {status === "failed" ? "Tentar de novo" : "Baixar"}
          </Button>
        )}
      </div>
    </li>
  );
}
