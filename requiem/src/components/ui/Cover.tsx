import { FilmStrip, MusicNotes, Playlist as PlaylistIcon, UserSound } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { cn } from "@/lib/format";

type CoverKind = "music" | "video" | "playlist" | "artist";

const icons = { music: MusicNotes, video: FilmStrip, playlist: PlaylistIcon, artist: UserSound };

/**
 * Cover art with a reserved box. Without an image (or when it fails to load) it shows the
 * honest empty: a neutral tile with the icon of the item's type, the same on every item.
 */
export function Cover({
  src,
  kind,
  className,
  rounded = "rounded-control",
  alt = "",
}: {
  src: string | null | undefined;
  kind: CoverKind;
  className?: string;
  rounded?: string;
  alt?: string;
}) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  const Icon = icons[kind];
  const aspect = kind === "video" ? "aspect-video" : "aspect-square";
  return (
    <div className={cn("relative shrink-0 overflow-hidden bg-raised", aspect, rounded, className)}>
      {src && !failed ? (
        <img
          src={src}
          alt={alt}
          loading="lazy"
          decoding="async"
          draggable={false}
          onError={() => setFailed(true)}
          className="absolute inset-0 h-full w-full object-cover"
        />
      ) : (
        <div className="absolute inset-0 flex items-center justify-center text-faint-foreground">
          <Icon weight="duotone" className="h-[38%] w-[38%] max-h-16 max-w-16" />
        </div>
      )}
    </div>
  );
}

/** The small picture at the start of a list row. In a list that mixes songs and videos, every
 *  row reserves the video's width so the titles line up. */
export function RowCover({ src, kind, wide }: { src: string | null; kind: "music" | "video"; wide: boolean }) {
  const cover = <Cover src={src} kind={kind} className={kind === "video" ? "w-16" : "w-10"} rounded="rounded-[4px]" />;
  return wide ? <div className="flex w-16 shrink-0 justify-center">{cover}</div> : cover;
}
