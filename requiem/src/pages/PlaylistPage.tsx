import { DotsThree, DownloadSimple, PencilSimple, Playlist as PlaylistIcon, Skull } from "@phosphor-icons/react";
import { useMemo } from "react";
import { BackBar, Hero, Mosaic, PlayActions } from "@/components/library/Hero";
import { TrackList } from "@/components/library/TrackList";
import { Button, IconButton } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { Dropdown } from "@/components/ui/Menu";
import { count, totalDuration } from "@/lib/format";
import type { Item } from "@/lib/types";
import { showDialog } from "@/state/dialogs";
import { useLibrary } from "@/state/library";
import { go } from "@/state/router";

export function PlaylistPage({ id }: { id: number }) {
  const playlist = useLibrary((s) => s.playlists.find((p) => p.id === id));
  const items = useLibrary((s) => s.items);

  const { ready, waiting } = useMemo(() => {
    const ready: Item[] = [];
    let waiting = 0;
    for (const itemId of playlist?.itemIds ?? []) {
      const item = items[itemId];
      if (item?.status === "done") ready.push(item);
      else if (item && (item.status === "queued" || item.status === "downloading")) waiting++;
    }
    return { ready, waiting };
  }, [playlist, items]);

  if (!playlist) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <EmptyState
          icon={<PlaylistIcon weight="duotone" />}
          title="Essa playlist sumiu"
          actions={
            <Button variant="primary" onClick={() => go({ name: "library" })}>
              Ir para a biblioteca
            </Button>
          }
        >
          Ela foi apagada. As músicas continuam na biblioteca.
        </EmptyState>
      </div>
    );
  }

  const total = ready.reduce((s, i) => s + (i.duration ?? 0), 0);
  const meta = [ready.length ? `${count(ready.length, "música", "músicas")}, ${totalDuration(total)}` : "Vazia", waiting ? `mais ${waiting} baixando` : ""]
    .filter(Boolean)
    .join(", ");

  return (
    <div className="pb-12">
      <BackBar />
      <Hero
        cover={<Mosaic items={ready} className="w-52" />}
        label="Playlist"
        title={playlist.name}
        meta={meta}
        actions={
          <PlayActions
            items={ready}
            extra={
              <Dropdown
                align="start"
                entries={[
                  { type: "item", label: "Renomear", icon: <PencilSimple />, onSelect: () => showDialog({ type: "renamePlaylist", playlist }) },
                  { type: "separator" },
                  { type: "item", label: "Apagar a playlist", icon: <Skull />, danger: true, onSelect: () => showDialog({ type: "deletePlaylist", playlist }) },
                ]}
                trigger={
                  <IconButton label="Mais opções da playlist" size="lg">
                    <DotsThree weight="bold" className="h-6 w-6" />
                  </IconButton>
                }
              />
            }
          />
        }
      />
      <div className="px-8">
        {ready.length ? (
          <TrackList items={ready} inPlaylist={playlist} />
        ) : waiting ? (
          <div className="flex items-center gap-3 text-[15px] text-muted-foreground">
            <DownloadSimple className="h-5 w-5 text-primary" />
            <span>As músicas desta playlist ainda estão baixando. Elas aparecem aqui assim que chegam.</span>
            <Button variant="ghost" size="sm" onClick={() => go({ name: "downloads" })}>
              Ver downloads
            </Button>
          </div>
        ) : (
          <p className="max-w-xl text-[15px] text-muted-foreground">
            Para encher a playlist, clique com o botão direito numa música da biblioteca e escolha Adicionar à playlist.
          </p>
        )}
      </div>
    </div>
  );
}
