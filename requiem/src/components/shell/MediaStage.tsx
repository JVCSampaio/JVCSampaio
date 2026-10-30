import { CornersIn, Pause, Play } from "@phosphor-icons/react";
import { useEffect, useRef, useState } from "react";
import { IconButton } from "@/components/ui/Button";
import { Slider } from "@/components/ui/Controls";
import { cn, duration } from "@/lib/format";
import { useLibrary } from "@/state/library";
import { media, seek, setCinema, toggle, usePlayer } from "@/state/player";
import { useStage } from "@/state/stage";

/**
 * Holds the single media element. It never moves in the DOM (moving it would stop playback);
 * the box around it is placed over the panel's slot, over the whole window in cinema mode, or
 * kept out of sight for songs.
 */
export function MediaStage() {
  const host = useRef<HTMLDivElement>(null);
  const slot = useStage((s) => s.slot);
  const cinema = usePlayer((s) => s.cinema);
  const panel = usePlayer((s) => s.panel);
  const id = usePlayer((s) => s.queue[s.index] ?? null);
  const kind = useLibrary((s) => (id ? s.items[id]?.kind : undefined));

  useEffect(() => {
    const el = host.current;
    if (el && media.parentElement !== el) el.appendChild(media);
    media.className = "h-full w-full bg-black object-contain";
  }, []);

  const isVideo = kind === "video";
  const inPanel = isVideo && !cinema && !!panel && !!slot;
  const style: React.CSSProperties = inPanel
    ? { top: slot.top, left: slot.left, width: slot.width, height: slot.height }
    : cinema && isVideo
      ? { inset: 0 }
      : { width: 1, height: 1, left: -10, top: -10, opacity: 0, pointerEvents: "none" };

  return (
    <div
      className={cn("fixed overflow-hidden", inPanel && "z-20 rounded-surface", cinema && isVideo && "z-50 bg-black")}
      style={style}
      aria-hidden={!inPanel && !(cinema && isVideo)}
    >
      <div ref={host} className="h-full w-full" onDoubleClick={() => isVideo && void setCinema(!cinema)} />
      {cinema && isVideo && <CinemaControls />}
    </div>
  );
}

function CinemaControls() {
  const playing = usePlayer((s) => s.playing);
  const position = usePlayer((s) => s.position);
  const total = usePlayer((s) => s.duration);
  const id = usePlayer((s) => s.queue[s.index] ?? null);
  const title = useLibrary((s) => (id ? s.items[id]?.title : ""));
  const [visible, setVisible] = useState(true);

  // Controls step aside while the video plays and nobody moves the mouse.
  useEffect(() => {
    let timer = window.setTimeout(() => setVisible(false), 2500);
    const wake = () => {
      setVisible(true);
      window.clearTimeout(timer);
      timer = window.setTimeout(() => setVisible(false), 2500);
    };
    window.addEventListener("mousemove", wake);
    window.addEventListener("keydown", wake);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("mousemove", wake);
      window.removeEventListener("keydown", wake);
    };
  }, []);

  const shown = visible || !playing;
  return (
    <div
      className={cn(
        "absolute inset-x-0 bottom-0 bg-linear-to-t from-black/80 to-transparent px-8 pb-6 pt-16 transition-opacity [transition-duration:var(--dur-slow)]",
        shown ? "opacity-100" : "pointer-events-none opacity-0",
      )}
    >
      <p className="mb-3 truncate text-lg font-bold text-foreground">{title}</p>
      <div className="flex items-center gap-4">
        <IconButton label={playing ? "Pausar" : "Tocar"} tone="bone" size="lg" onClick={toggle}>
          {playing ? <Pause weight="fill" className="h-5 w-5" /> : <Play weight="fill" className="h-5 w-5" />}
        </IconButton>
        <span className="w-12 text-right text-[13px] tabular-nums text-muted-foreground">{duration(position)}</span>
        <Slider label="Posição" value={position} max={total} tone="ember" onChange={seek} className="flex-1" />
        <span className="w-12 text-[13px] tabular-nums text-muted-foreground">{duration(total)}</span>
        <IconButton label="Sair da tela cheia" onClick={() => void setCinema(false)} shortcut="Esc">
          <CornersIn className="h-5 w-5" />
        </IconButton>
      </div>
    </div>
  );
}
