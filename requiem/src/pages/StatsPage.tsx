import { ArrowClockwise, Sparkle, WarningCircle } from "@phosphor-icons/react";
import { useEffect, useMemo, useState } from "react";
import { Section } from "@/components/library/Tiles";
import { Button } from "@/components/ui/Button";
import { Segmented, Skeleton } from "@/components/ui/Controls";
import { RowCover } from "@/components/ui/Cover";
import { EmptyState } from "@/components/ui/EmptyState";
import { backend } from "@/lib/backend";
import { count, totalDuration } from "@/lib/format";
import { WAIT } from "@/lib/motion";
import type { Item, Play, Stats } from "@/lib/types";
import { useDelayed } from "@/lib/useDelayed";
import { useLibrary } from "@/state/library";
import { coverUrl, playItem, playList } from "@/state/player";
import { go } from "@/state/router";

type Period = "week" | "month" | "year" | "all";

const DAYS: Record<Period, number | null> = { week: 7, month: 30, year: 365, all: null };
const DAY = 86_400_000;

type Load = { status: "loading" } | { status: "error"; message: string } | { status: "done"; stats: Stats };

/** The Retrospectiva: what the person listened to in a period, at any time of the year. */
export function StatsPage() {
  const [period, setPeriod] = useState<Period>("month");
  const [attempt, setAttempt] = useState(0);
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [history, setHistory] = useState<Play[] | null>(null);
  const showSkeleton = useDelayed(load.status === "loading", WAIT.showAfter);

  useEffect(() => {
    let alive = true;
    setLoad({ status: "loading" });
    const days = DAYS[period];
    backend()
      .then((api) => api.stats(days ? Date.now() - days * DAY : 0))
      .then((stats) => alive && setLoad({ status: "done", stats }))
      .catch((e) => alive && setLoad({ status: "error", message: String(e) }));
    return () => {
      alive = false;
    };
  }, [period, attempt]);

  useEffect(() => {
    let alive = true;
    backend()
      .then((api) => api.history(80))
      .then((plays) => alive && setHistory(plays))
      .catch(() => alive && setHistory([]));
    return () => {
      alive = false;
    };
  }, []);

  if (history && !history.length && load.status === "done" && !load.stats.plays) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <EmptyState icon={<Sparkle weight="duotone" />} title="A Retrospectiva começa agora">
          Tudo o que você ouvir a partir de hoje entra aqui: o tempo ouvindo, as músicas e os artistas que mais tocam.
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="max-w-5xl px-8 pb-12 pt-8">
      <h1 className="font-display text-5xl font-semibold text-foreground">Retrospectiva</h1>
      <Segmented<Period>
        label="Período"
        className="mt-6"
        value={period}
        onChange={setPeriod}
        options={[
          { value: "week", label: "7 dias" },
          { value: "month", label: "30 dias" },
          { value: "year", label: "1 ano" },
          { value: "all", label: "Sempre" },
        ]}
      />

      {load.status === "loading" && showSkeleton && (
        <div className="mt-8 flex gap-16" aria-label="Carregando">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="space-y-2">
              <Skeleton className="h-12 w-28" />
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      )}
      {load.status === "error" && (
        <div className="mt-8 flex items-start gap-3 text-[15px]">
          <WarningCircle weight="fill" className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
          <div>
            <p className="text-foreground">{load.message}</p>
            <Button variant="ghost" size="sm" className="-ml-3 mt-2" icon={<ArrowClockwise className="h-4 w-4" />} onClick={() => setAttempt((a) => a + 1)}>
              Tentar de novo
            </Button>
          </div>
        </div>
      )}
      {load.status === "done" && <Numbers stats={load.stats} period={period} />}

      {history && history.length > 0 && <Recent plays={history} />}
    </div>
  );
}

function Figure({ value, label }: { value: string; label: string }) {
  return (
    <div>
      <div className="font-display text-5xl font-semibold leading-none text-foreground tabular-nums">{value}</div>
      <div className="mt-2 text-sm text-muted-foreground">{label}</div>
    </div>
  );
}

function Numbers({ stats, period }: { stats: Stats; period: Period }) {
  const items = useLibrary((s) => s.items);
  if (!stats.plays) {
    const window = { week: "nos últimos 7 dias", month: "nos últimos 30 dias", year: "no último ano", all: "ainda" }[period];
    return <p className="mt-8 text-[15px] text-muted-foreground">Nada tocado {window}.</p>;
  }
  const top = stats.topItems.map(([id, plays]) => ({ item: items[id], plays })).filter((t): t is { item: Item; plays: number } => !!t.item);
  return (
    <>
      <div className="mt-8 flex flex-wrap gap-x-16 gap-y-8">
        <Figure value={totalDuration(stats.seconds)} label="ouvindo" />
        <Figure value={stats.plays.toLocaleString("pt-BR")} label={stats.plays === 1 ? "reprodução" : "reproduções"} />
        <Figure value={stats.songs.toLocaleString("pt-BR")} label={stats.songs === 1 ? "música diferente" : "músicas diferentes"} />
        <Figure value={stats.artists.toLocaleString("pt-BR")} label={stats.artists === 1 ? "artista" : "artistas"} />
      </div>
      {stats.topHour != null && <p className="mt-6 text-[15px] text-muted-foreground">Você ouve mais música por volta das {stats.topHour}h.</p>}

      <div className="mt-12 grid gap-12 @4xl:grid-cols-[3fr_2fr]">
        <Section title="Mais tocadas">
          <ol>
            {top.slice(0, 10).map(({ item, plays }, i) => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => playList(top.map((t) => t.item.id), i)}
                  className="flex w-full items-center gap-4 rounded-control px-2 py-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised"
                >
                  <span className="w-6 text-right font-display text-2xl font-semibold text-faint-foreground">{i + 1}</span>
                  <RowCover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} wide={top.some((t) => t.item.kind === "video")} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-medium text-foreground">{item.title}</span>
                    <span className="block truncate text-[13px] text-muted-foreground">{item.artist || item.channel}</span>
                  </span>
                  <span className="text-[13px] tabular-nums text-faint-foreground">{count(plays, "vez", "vezes")}</span>
                </button>
              </li>
            ))}
          </ol>
        </Section>
        {stats.topArtists.length > 0 && (
          <Section title="Artistas mais ouvidos">
            <ol>
              {stats.topArtists.slice(0, 6).map(([artist, plays, seconds], i) => (
                <li key={artist}>
                  <button
                    type="button"
                    onClick={() => go({ name: "artist", artist })}
                    className="flex w-full items-center gap-4 rounded-control px-2 py-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised"
                  >
                    <span className="w-6 text-right font-display text-2xl font-semibold text-faint-foreground">{i + 1}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[15px] font-medium text-foreground">{artist}</span>
                      <span className="block truncate text-[13px] text-muted-foreground">
                        {totalDuration(seconds)}, {count(plays, "reprodução", "reproduções")}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ol>
          </Section>
        )}
      </div>
    </>
  );
}

function dayLabel(ms: number): string {
  const day = new Date(ms).toDateString();
  if (day === new Date().toDateString()) return "Hoje";
  if (day === new Date(Date.now() - DAY).toDateString()) return "Ontem";
  const text = new Date(ms).toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long" });
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** The play log, newest first, grouped by day. */
function Recent({ plays }: { plays: Play[] }) {
  const items = useLibrary((s) => s.items);
  const days = useMemo(() => {
    const groups: Array<{ label: string; rows: Array<{ play: Play; item: Item }> }> = [];
    for (const play of plays) {
      const item = items[play.itemId];
      if (!item || item.status !== "done") continue;
      const label = dayLabel(play.playedAt);
      if (groups[groups.length - 1]?.label !== label) groups.push({ label, rows: [] });
      groups[groups.length - 1].rows.push({ play, item });
    }
    return groups;
  }, [plays, items]);
  if (!days.length) return null;
  const wide = days.some((d) => d.rows.some((r) => r.item.kind === "video"));
  return (
    <div className="mt-12">
      <Section title="Tocadas recentemente">
        <div className="space-y-6">
          {days.map((day) => (
            <div key={day.label}>
              <h3 className="mb-1 px-2 text-[13px] font-medium text-faint-foreground">{day.label}</h3>
              <ul>
                {day.rows.map(({ play, item }) => (
                  <li key={play.id}>
                    <button
                      type="button"
                      onClick={() => playItem(item.id)}
                      className="flex w-full items-center gap-4 rounded-control px-2 py-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised"
                    >
                      <span className="w-12 text-[13px] tabular-nums text-faint-foreground">
                        {new Date(play.playedAt).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
                      </span>
                      <RowCover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} wide={wide} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[15px] text-foreground">{item.title}</span>
                        <span className="block truncate text-[13px] text-muted-foreground">{item.artist || item.channel}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </Section>
    </div>
  );
}
