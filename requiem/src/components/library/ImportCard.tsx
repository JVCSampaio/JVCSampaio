import { MagnifyingGlass, X } from "@phosphor-icons/react";
import { Button, IconButton } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/Controls";
import { count } from "@/lib/format";
import type { ImportStatus, Track } from "@/lib/types";
import { go } from "@/state/router";

const lead = (track: Track) => track.artist.split(",")[0]?.trim() ?? "";

/** A list being brought from Spotify or pasted: how far the search on YouTube got, then what
 *  came in, the playlist made of it, and the songs that found no good match. */
export function ImportCard({ status, onDismiss, primary }: { status: ImportStatus; onDismiss?: () => void; primary?: boolean }) {
  const { finished, missing } = status;
  return (
    <div className="rounded-surface border border-border bg-card p-4" data-import={status.id} data-finished={finished || undefined}>
      <div className="flex items-start gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-[13px] text-muted-foreground">{finished ? "Lista trazida" : "Procurando no YouTube"}</p>
          <h3 className="truncate text-lg font-bold text-foreground">{status.title}</h3>
          <p className="text-sm tabular-nums text-muted-foreground" aria-live="polite">
            {finished
              ? `${count(status.found, "música encontrada", "músicas encontradas")} de ${status.total}${status.found ? ". Elas baixam em seguida." : ""}`
              : `${status.done} de ${status.total}`}
          </p>
        </div>
        {finished && status.playlistId != null && (
          <Button variant={primary ? "primary" : "secondary"} onClick={() => go({ name: "playlist", id: status.playlistId! })}>
            Abrir a playlist
          </Button>
        )}
        {finished && onDismiss && (
          <IconButton label="Tirar daqui" size="sm" onClick={onDismiss}>
            <X className="h-4 w-4" />
          </IconButton>
        )}
      </div>
      {!finished && <ProgressBar fraction={status.total ? status.done / status.total : 0} className="mt-3" />}
      {status.error && <p className="mt-3 text-sm text-destructive">{status.error}</p>}
      {finished && missing.length > 0 && (
        <details className="mt-3 group/missing">
          <summary className="cursor-pointer select-none text-sm text-muted-foreground hover:text-foreground">
            {count(missing.length, "música ficou de fora", "músicas ficaram de fora")}
          </summary>
          <ul className="mt-2 space-y-0.5">
            {missing.map((track, i) => (
              <li key={`${track.title}-${i}`} className="flex items-center gap-3 rounded-control px-2 py-1 hover:bg-raised">
                <span className="min-w-0 flex-1 truncate text-[15px] text-foreground">
                  {track.title}
                  {track.artist && <span className="text-muted-foreground">, {track.artist}</span>}
                </span>
                <Button size="sm" variant="ghost" icon={<MagnifyingGlass className="h-4 w-4" />} onClick={() => go({ name: "search", query: `${lead(track)} ${track.title}`.trim() })}>
                  Buscar
                </Button>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
