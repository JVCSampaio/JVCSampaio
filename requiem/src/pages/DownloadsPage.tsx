import { ArrowClockwise, ArrowsClockwise, FilmStrip, Ghost, MoonStars, MusicNotes, Play, WarningCircle, X } from "@phosphor-icons/react";
import { useMemo, useState } from "react";
import { Section } from "@/components/library/Tiles";
import { Button, IconButton } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/Controls";
import { Cover, RowCover } from "@/components/ui/Cover";
import { EmptyState } from "@/components/ui/EmptyState";
import { backend } from "@/lib/backend";
import { ago, cn, count, duration, inTime, percent, speed } from "@/lib/format";
import type { Item } from "@/lib/types";
import { isAccountConnected, useLibrary } from "@/state/library";
import { coverUrl, playItem } from "@/state/player";
import { go } from "@/state/router";
import { toast } from "@/state/toasts";

const sources: Record<string, string> = {
  history: "Do histórico",
  liked: "Das curtidas",
  liked_music: "Das curtidas do YouTube Music",
  search: "Da busca",
  link: "De um link",
  playlist: "De uma playlist",
};

const DAY = 86_400_000;

async function call(fn: (api: Awaited<ReturnType<typeof backend>>) => Promise<unknown>) {
  try {
    await fn(await backend());
  } catch (e) {
    toast(String(e), "error");
  }
}

export function DownloadsPage() {
  const items = useLibrary((s) => s.items);

  const { active, queued, missed, arrived } = useMemo(() => {
    const all = Object.values(items);
    const since = Date.now() - DAY;
    return {
      active: all.filter((i) => i.status === "downloading"),
      queued: all.filter((i) => i.status === "queued").sort((a, b) => (b.seenAt ?? b.addedAt) - (a.seenAt ?? a.addedAt)),
      missed: all.filter((i) => i.status === "failed" || i.status === "skipped"),
      arrived: all
        .filter((i) => i.status === "done" && (i.downloadedAt ?? 0) > since)
        .sort((a, b) => (b.downloadedAt ?? 0) - (a.downloadedAt ?? 0))
        .slice(0, 8),
    };
  }, [items]);

  const idle = !active.length && !queued.length && !missed.length;

  return (
    <div className="px-8 pb-12 pt-8">
      <h1 className="font-display text-5xl font-semibold text-foreground">Downloads</h1>
      <SyncCard />

      {idle && !arrived.length ? (
        <EmptyState icon={<Ghost weight="duotone" />} title="Nenhuma alma na fila">
          O que a sincronização encontrar no seu YouTube e o que você pedir na busca aparece aqui enquanto baixa.
        </EmptyState>
      ) : (
        <div className="mt-10 flex max-w-4xl flex-col gap-10">
          {active.length > 0 && (
            <Section title="Baixando agora">
              <ul className="space-y-1">
                {active.map((item) => (
                  <ActiveRow key={item.id} item={item} />
                ))}
              </ul>
            </Section>
          )}

          {queued.length > 0 && (
            <Section title="Na fila" action={<span className="text-sm tabular-nums text-muted-foreground">{count(queued.length, "item", "itens")}</span>}>
              <ul className="space-y-0.5">
                {queued.slice(0, 200).map((item) => (
                  <Row key={item.id} item={item} detail={sources[item.source] ?? ""} wide={queued.some((i) => i.kind === "video")}>
                    <IconButton label="Tirar da fila" size="sm" onClick={() => call((api) => api.downloadCancel(item.id))}>
                      <X className="h-4 w-4" />
                    </IconButton>
                  </Row>
                ))}
              </ul>
            </Section>
          )}

          {missed.length > 0 && <Missed items={missed} />}

          {arrived.length > 0 && (
            <Section title="Chegaram hoje">
              <ul className="space-y-0.5">
                {arrived.map((item) => (
                  <Row key={item.id} item={item} detail={ago(item.downloadedAt)} wide={arrived.some((i) => i.kind === "video")}>
                    <Button size="sm" variant="ghost" icon={<Play weight="fill" className="h-3.5 w-3.5" />} onClick={() => playItem(item.id)}>
                      Tocar
                    </Button>
                  </Row>
                ))}
              </ul>
            </Section>
          )}
        </div>
      )}
    </div>
  );
}

/** What the automatic sync is doing, when it ran, when it runs next. */
function SyncCard() {
  const sync = useLibrary((s) => s.sync);
  const settings = useLibrary((s) => s.settings);
  const connected = isAccountConnected(settings);

  if (!connected) {
    return (
      <div className="mt-6 flex max-w-4xl items-center gap-4 rounded-surface border border-border bg-card px-5 py-4">
        <Ghost weight="duotone" className="h-9 w-9 shrink-0 text-muted-foreground" />
        <div className="min-w-0 flex-1">
          <p className="text-[15px] font-medium text-foreground">Conecte o YouTube</p>
          <p className="text-[13px] text-muted-foreground">Com a conta conectada, o Requiem baixa sozinho o que você ouve e curte.</p>
        </div>
        <Button variant="primary" onClick={() => go({ name: "settings", section: "account" })}>
          Conectar
        </Button>
      </div>
    );
  }

  const running = !!sync?.running;
  const failed = !running && !!sync?.lastError;
  let title: string;
  let detail: string;
  if (running) {
    title = "Lendo o seu YouTube";
    detail = "Histórico e curtidas. O que for novo entra na fila abaixo.";
  } else if (failed) {
    title = "A última sincronização falhou";
    detail = sync?.lastError ?? "";
  } else if (sync?.lastRunAt) {
    title = `Sincronizado ${ago(sync.lastRunAt)}`;
    const found = sync.lastFound ? `${count(sync.lastFound, "novidade", "novidades")} na última leitura.` : "Nada novo na última leitura.";
    const next = !settings?.autoSync ? " A sincronização automática está desligada." : sync.nextRunAt ? ` A próxima é ${inTime(sync.nextRunAt)}.` : "";
    detail = found + next;
  } else {
    title = "Primeira sincronização em instantes";
    detail = "O Requiem lê o seu histórico e as suas curtidas e baixa o que for novo.";
  }

  return (
    <div className="mt-6 flex max-w-4xl items-center gap-4 rounded-surface border border-border bg-card px-5 py-4">
      {failed ? (
        <WarningCircle weight="fill" className="h-9 w-9 shrink-0 text-destructive" />
      ) : (
        <MoonStars weight={running ? "fill" : "duotone"} className={cn("h-9 w-9 shrink-0", running ? "animate-pulse text-primary" : "text-muted-foreground")} />
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[15px] font-medium text-foreground">{title}</p>
        <p className={cn("text-[13px]", failed ? "text-destructive" : "text-muted-foreground")} data-selectable={failed || undefined}>
          {detail}
        </p>
      </div>
      <Button icon={<ArrowsClockwise className="h-4 w-4" />} disabled={running} onClick={() => call((api) => api.syncNow())}>
        Sincronizar agora
      </Button>
    </div>
  );
}

function KindIcon({ item }: { item: Item }) {
  if (item.kind === "video") return <FilmStrip className="h-4 w-4 shrink-0 text-faint-foreground" aria-label="Vídeo" />;
  if (item.kind === "music") return <MusicNotes className="h-4 w-4 shrink-0 text-faint-foreground" aria-label="Música" />;
  return null;
}

function Row({ item, detail, error, wide, children }: { item: Item; detail: string; error?: boolean; wide: boolean; children: React.ReactNode }) {
  const facts = [item.artist || item.channel, duration(item.duration)].filter(Boolean).join(", ");
  return (
    <li className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-4 rounded-control px-3 py-2 transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised">
      <RowCover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} wide={wide} />
      <div className="min-w-0">
        <div className="flex items-center gap-1.5">
          <KindIcon item={item} />
          <span className="truncate text-[15px] font-medium text-foreground">{item.title}</span>
        </div>
        <div className="truncate text-[13px] text-muted-foreground">
          {facts}
          {detail && <span className={cn(error ? "text-destructive" : "text-faint-foreground")}>{facts ? `, ${detail}` : detail}</span>}
        </div>
      </div>
      <div className="flex items-center gap-1">{children}</div>
    </li>
  );
}

function ActiveRow({ item }: { item: Item }) {
  const p = useLibrary((s) => s.progress[item.id]);
  const facts = [p?.fraction != null ? percent(p.fraction) : null, speed(p?.speed), p?.eta ? `falta ${duration(p.eta)}` : null].filter(Boolean).join(", ");
  return (
    <li className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-4 rounded-surface border border-border bg-card px-4 py-3">
      <Cover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} className={item.kind === "video" ? "w-[96px]" : "w-14"} rounded="rounded-[4px]" />
      <div className="min-w-0">
        <div className="flex items-center gap-1.5">
          <KindIcon item={item} />
          <span className="truncate text-[15px] font-medium text-foreground">{item.title}</span>
        </div>
        <div className="truncate text-[13px] text-muted-foreground">{item.artist || item.channel}</div>
        <div className="mt-2 flex items-center gap-3">
          {p?.fraction != null && <ProgressBar fraction={p.fraction} className="max-w-md flex-1" />}
          <span className="shrink-0 text-[13px] tabular-nums text-muted-foreground">{p?.fraction != null ? facts : p?.stage ?? "Começando"}</span>
        </div>
      </div>
      <IconButton label="Cancelar o download" size="sm" onClick={() => call((api) => api.downloadCancel(item.id))}>
        <X className="h-4 w-4" />
      </IconButton>
    </li>
  );
}

function Missed({ items }: { items: Item[] }) {
  const [clearing, setClearing] = useState(false);
  return (
    <Section
      title="Não baixadas"
      action={
        <Button
          variant="ghost"
          size="sm"
          disabled={clearing}
          onClick={async () => {
            setClearing(true);
            await call(async (api) => {
              const n = await api.downloadsClear();
              if (n) toast(`${count(n, "item saiu", "itens saíram")} da lista.`);
            });
            setClearing(false);
          }}
        >
          Limpar a lista
        </Button>
      }
    >
      <ul className="space-y-0.5">
        {items.map((item) => (
          <Row
            key={item.id}
            item={item}
            detail={item.status === "skipped" ? skipReason(item.error) : item.error ?? "Falhou"}
            error={item.status === "failed"}
            wide={items.some((i) => i.kind === "video")}
          >
            <Button size="sm" variant="ghost" icon={<ArrowClockwise className="h-4 w-4" />} onClick={() => call((api) => api.downloadRetry(item.id))}>
              {item.status === "skipped" ? "Baixar assim mesmo" : "Tentar de novo"}
            </Button>
            <IconButton label="Tirar da lista" size="sm" onClick={() => call((api) => api.downloadCancel(item.id))}>
              <X className="h-4 w-4" />
            </IconButton>
          </Row>
        ))}
      </ul>
    </Section>
  );
}

function skipReason(reason: string | null): string {
  if (!reason) return "pulado";
  if (reason === "Short") return "pulado por ser Short";
  return `pulado: ${reason.charAt(0).toLowerCase()}${reason.slice(1)}`;
}
