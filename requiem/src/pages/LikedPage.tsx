import { Heart } from "@phosphor-icons/react";
import { useMemo } from "react";
import { BackBar, Hero, PlayActions } from "@/components/library/Hero";
import { TrackList } from "@/components/library/TrackList";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { count, totalDuration } from "@/lib/format";
import { useLibrary } from "@/state/library";
import { go } from "@/state/router";

/** Everything with a heart, the most recent like first. */
export function LikedPage() {
  const items = useLibrary((s) => s.items);
  const liked = useMemo(
    () =>
      Object.values(items)
        .filter((i) => i.status === "done" && i.likedAt)
        .sort((a, b) => (b.likedAt ?? 0) - (a.likedAt ?? 0)),
    [items],
  );

  if (!liked.length) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <EmptyState
          icon={<Heart weight="duotone" />}
          title="Nenhuma curtida ainda"
          actions={
            <Button variant="primary" onClick={() => go({ name: "library" })}>
              Ir para a biblioteca
            </Button>
          }
        >
          Toque no coração de uma música, no player ou na lista, e ela aparece aqui.
        </EmptyState>
      </div>
    );
  }

  const total = liked.reduce((s, i) => s + (i.duration ?? 0), 0);
  return (
    <div className="pb-12">
      <BackBar />
      <Hero
        cover={
          <div className="flex aspect-square w-52 shrink-0 items-center justify-center rounded-surface bg-raised text-primary" aria-hidden>
            <Heart weight="fill" className="h-20 w-20" />
          </div>
        }
        label="Playlist"
        title="Curtidas"
        meta={`${count(liked.length, "música", "músicas")}, ${totalDuration(total)}`}
        actions={<PlayActions items={liked} />}
      />
      <div className="px-8">
        <TrackList items={liked} />
      </div>
    </div>
  );
}
