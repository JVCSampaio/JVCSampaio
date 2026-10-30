import { DownloadSimple, FolderOpen, Ghost, MagnifyingGlass, Play } from "@phosphor-icons/react";
import { useMemo } from "react";
import { Section, Shelf, SongTile, VideoTile } from "@/components/library/Tiles";
import { Button } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/Controls";
import { Cover, RowCover } from "@/components/ui/Cover";
import { EmptyState } from "@/components/ui/EmptyState";
import { cn, count, greeting } from "@/lib/format";
import type { Item } from "@/lib/types";
import { isAccountConnected, libraryItems, useLibrary } from "@/state/library";
import { coverUrl, playList, usePlayer } from "@/state/player";
import { go } from "@/state/router";

const fromYouTube = (i: Item) => i.source === "history" || i.source === "liked" || i.source === "liked_music";
const onDisk = (i: Item) => i.source === "local";
const fromRadio = (i: Item) => i.source === "radio";
const bySeen = (a: Item, b: Item) => (b.seenAt ?? b.addedAt) - (a.seenAt ?? a.addedAt);

export function HomePage() {
  const items = useLibrary((s) => s.items);
  const settings = useLibrary((s) => s.settings);
  const reading = useLibrary((s) => s.local.running);
  const library = useMemo(() => libraryItems(items), [items]);

  const sections = useMemo(() => {
    const continueWith = library
      .filter((i) => i.lastPlayedAt)
      .sort((a, b) => (b.lastPlayedAt ?? 0) - (a.lastPlayedAt ?? 0))
      .slice(0, 6);
    const heardMusic = library.filter((i) => i.kind === "music" && fromYouTube(i)).sort(bySeen).slice(0, 14);
    const seenVideos = library.filter((i) => i.kind === "video" && fromYouTube(i)).sort(bySeen).slice(0, 10);
    const computer = library.filter(onDisk).slice(0, 14);
    const chosen = library.filter((i) => !fromYouTube(i) && !onDisk(i) && !fromRadio(i)).slice(0, 14);
    const radio = library.filter(fromRadio).slice(0, 14);
    const mostPlayed = library
      .filter((i) => i.playCount >= 2)
      .sort((a, b) => b.playCount - a.playCount)
      .slice(0, 8);
    return { continueWith, heardMusic, computer, seenVideos, chosen, radio, mostPlayed };
  }, [library]);

  if (!library.length) return <EmptyHome connected={isAccountConnected(settings)} reading={reading} />;

  return (
    <div className="px-8 pb-12 pt-8">
      <h1 className="font-display text-5xl font-semibold text-foreground">{greeting()}</h1>
      <DownloadStrip />

      <div className="mt-8 flex flex-col gap-10">
        {sections.continueWith.length > 0 && (
          <div className="grid grid-cols-2 gap-2 @5xl:grid-cols-3">
            {sections.continueWith.map((item, i) => (
              <QuickPick key={item.id} item={item} row={sections.continueWith} index={i} />
            ))}
          </div>
        )}

        {sections.heardMusic.length > 0 && (
          <Section title="Ouvidas no YouTube">
            <Shelf>
              {sections.heardMusic.map((item, i) => (
                <SongTile key={item.id} item={item} row={sections.heardMusic} index={i} />
              ))}
            </Shelf>
          </Section>
        )}

        {sections.computer.length > 0 && (
          <Section
            title="No seu computador"
            action={
              <Button variant="ghost" size="sm" onClick={() => go({ name: "library" })}>
                Ver a biblioteca
              </Button>
            }
          >
            <Shelf>
              {sections.computer.map((item, i) =>
                item.kind === "video" ? (
                  <VideoTile key={item.id} item={item} row={sections.computer} index={i} />
                ) : (
                  <SongTile key={item.id} item={item} row={sections.computer} index={i} />
                ),
              )}
            </Shelf>
          </Section>
        )}

        {sections.seenVideos.length > 0 && (
          <Section title="Vídeos que você viu">
            <Shelf>
              {sections.seenVideos.map((item, i) => (
                <VideoTile key={item.id} item={item} row={sections.seenVideos} index={i} />
              ))}
            </Shelf>
          </Section>
        )}

        {sections.chosen.length > 0 && (
          <Section title="Baixadas por você">
            <Shelf>
              {sections.chosen.map((item, i) =>
                item.kind === "video" ? (
                  <VideoTile key={item.id} item={item} row={sections.chosen} index={i} />
                ) : (
                  <SongTile key={item.id} item={item} row={sections.chosen} index={i} />
                ),
              )}
            </Shelf>
          </Section>
        )}

        {sections.radio.length > 0 && (
          <Section title="Descobertas na rádio">
            <Shelf>
              {sections.radio.map((item, i) => (
                <SongTile key={item.id} item={item} row={sections.radio} index={i} />
              ))}
            </Shelf>
          </Section>
        )}

        {sections.mostPlayed.length > 0 && (
          <Section title="Mais tocadas">
            <ol className="grid grid-cols-1 gap-x-8 @3xl:grid-cols-2">
              {sections.mostPlayed.map((item, i) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => playList(sections.mostPlayed.map((x) => x.id), i)}
                    className="flex w-full items-center gap-4 rounded-control px-2 py-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised"
                  >
                    <span className="w-5 text-right font-display text-2xl font-semibold text-faint-foreground">{i + 1}</span>
                    <RowCover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} wide={sections.mostPlayed.some((i) => i.kind === "video")} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[15px] font-medium text-foreground">{item.title}</span>
                      <span className="block truncate text-[13px] text-muted-foreground">{item.artist}</span>
                    </span>
                    <span className="text-[13px] tabular-nums text-faint-foreground">{count(item.playCount, "vez", "vezes")}</span>
                  </button>
                </li>
              ))}
            </ol>
          </Section>
        )}
      </div>
    </div>
  );
}

function QuickPick({ item, row, index }: { item: Item; row: Item[]; index: number }) {
  const current = usePlayer((s) => s.queue[s.index] === item.id);
  return (
    <button
      type="button"
      onClick={() => playList(row.map((i) => i.id), index)}
      className="group flex h-16 items-center gap-3 overflow-hidden rounded-control bg-card pr-3 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised"
    >
      <Cover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} className={item.kind === "video" ? "h-16 w-auto" : "h-16 w-16"} rounded="rounded-none" />
      <span className={cn("min-w-0 flex-1 truncate text-[15px] font-medium", current ? "text-primary" : "text-foreground")}>{item.title}</span>
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground transition-opacity [transition-duration:var(--dur-fast)] [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100">
        <Play weight="fill" className="h-4 w-4" />
      </span>
    </button>
  );
}

/** One line while the engine downloads: how many, which one, how far. */
function DownloadStrip() {
  const items = useLibrary((s) => s.items);
  const progress = useLibrary((s) => s.progress);
  const active = Object.values(items).filter((i) => i.status === "downloading");
  const queued = Object.values(items).filter((i) => i.status === "queued").length;
  if (!active.length) return null;
  const first = active[0];
  const p = progress[first.id];
  return (
    <button
      type="button"
      onClick={() => go({ name: "downloads" })}
      className="mt-6 flex w-full max-w-xl items-center gap-4 rounded-surface border border-border bg-card px-4 py-3 text-left transition-[border-color] [transition-duration:var(--dur-fast)] hover:border-border-strong"
    >
      <DownloadSimple weight="bold" className="h-5 w-5 shrink-0 text-primary" />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm text-foreground">
          Baixando "{first.title}"{queued ? `, mais ${queued} na fila` : ""}
        </span>
        {p?.fraction != null ? <ProgressBar fraction={p.fraction} className="mt-2" /> : <span className="block text-[13px] text-muted-foreground">{p?.stage ?? "Começando"}</span>}
      </span>
    </button>
  );
}

function EmptyHome({ connected, reading }: { connected: boolean; reading: boolean }) {
  const folders = (
    <Button variant="ghost" size="lg" icon={<FolderOpen className="h-5 w-5" />} onClick={() => go({ name: "settings", section: "folders" })}>
      Escolher pastas de música
    </Button>
  );
  if (reading) {
    return (
      <div className="flex min-h-full items-center justify-center px-8">
        <EmptyState icon={<FolderOpen weight="duotone" />} title="Lendo o seu computador">
          As músicas e os vídeos que já estão nas suas pastas de música aparecem aqui em instantes.
        </EmptyState>
      </div>
    );
  }
  return (
    <div className="flex min-h-full items-center justify-center px-8">
      <EmptyState
        icon={<Ghost weight="duotone" />}
        title="A cripta está vazia"
        actions={
          connected ? (
            <>
              <Button variant="primary" size="lg" icon={<MagnifyingGlass weight="bold" className="h-5 w-5" />} onClick={() => go({ name: "search" })}>
                Buscar uma música
              </Button>
              {folders}
            </>
          ) : (
            <>
              <Button variant="primary" size="lg" onClick={() => go({ name: "settings", section: "account" })}>
                Conectar o YouTube
              </Button>
              <Button variant="ghost" size="lg" onClick={() => go({ name: "search" })}>
                Buscar uma música
              </Button>
              {folders}
            </>
          )
        }
      >
        {connected
          ? "O que você ouvir no YouTube chega aqui sozinho a cada sincronização. As músicas que já estão no computador também aparecem, das pastas que você escolher."
          : "Conecte o YouTube e o Requiem guarda sozinho o que você ouve. As músicas que já estão no computador aparecem aqui quando você escolhe as pastas."}
      </EmptyState>
    </div>
  );
}
