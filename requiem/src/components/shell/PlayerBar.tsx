import {
  CornersOut,
  MicrophoneStage,
  Pause,
  Play,
  Queue,
  Repeat,
  RepeatOnce,
  Shuffle,
  SkipBack,
  SkipForward,
  SpeakerHigh,
  SpeakerLow,
  SpeakerSlash,
  X,
} from "@phosphor-icons/react";
import { useState } from "react";
import { IconButton, KeyCombo } from "@/components/ui/Button";
import { Slider } from "@/components/ui/Controls";
import { Cover } from "@/components/ui/Cover";
import { duration, percent } from "@/lib/format";
import { useLibrary } from "@/state/library";
import {
  coverUrl,
  cycleRepeat,
  dropPending,
  next,
  previous,
  seek,
  setCinema,
  setPanel,
  setVolume,
  toggle,
  toggleMute,
  toggleShuffle,
  usePlayer,
} from "@/state/player";
import { go } from "@/state/router";
import { LikeButton } from "@/components/library/LikeButton";

export function PlayerBar() {
  const id = usePlayer((s) => s.queue[s.index] ?? null);
  const item = useLibrary((s) => (id ? s.items[id] : undefined));
  const pending = usePlayer((s) => s.pending);

  return (
    <footer className="grid h-[84px] grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)_minmax(0,1fr)] items-center gap-6 border-t border-border bg-surface px-4">
      {pending ? <Preparing /> : item ? <NowPlaying /> : <span />}
      {item ? (
        <Transport />
      ) : (
        <p className="text-center text-sm text-faint-foreground">
          {pending ? "Começa sozinha quando o arquivo chegar." : "Nada tocando. Escolha uma música na biblioteca."}
        </p>
      )}
      {item ? <Extras isVideo={item.kind === "video"} /> : <span />}
    </footer>
  );
}

/** A search result asked to play, while its file downloads ahead of everything else. */
function Preparing() {
  const pending = usePlayer((s) => s.pending);
  const status = useLibrary((s) => (pending ? s.items[pending.entry.id]?.status : undefined));
  const fraction = useLibrary((s) => (pending ? s.progress[pending.entry.id]?.fraction : undefined));
  if (!pending) return null;
  const { entry, kind } = pending;
  return (
    <div className="flex min-w-0 items-center gap-3" data-preparing={entry.id}>
      <Cover src={entry.thumbnail || null} kind={kind} className={kind === "video" ? "w-[96px]" : "w-14"} />
      <div className="min-w-0" aria-live="polite">
        <div className="truncate text-[15px] font-medium text-foreground">{entry.title}</div>
        <div className="truncate text-[13px] text-muted-foreground">
          {status === "downloading" && fraction != null ? `Preparando, ${percent(fraction)}` : "Preparando"}
        </div>
      </div>
      <IconButton label="Não tocar esta" size="sm" onClick={dropPending} className="ml-1">
        <X className="h-4 w-4" />
      </IconButton>
    </div>
  );
}

function NowPlaying() {
  const id = usePlayer((s) => s.queue[s.index] ?? null);
  const item = useLibrary((s) => (id ? s.items[id] : undefined));
  if (!item) return null;
  return (
    <div className="flex min-w-0 items-center gap-3">
      <button type="button" onClick={() => setPanel("lyrics")} aria-label="Abrir o painel Tocando agora" className="shrink-0 rounded-control">
        <Cover src={coverUrl(item)} kind={item.kind === "video" ? "video" : "music"} className={item.kind === "video" ? "w-[96px]" : "w-14"} />
      </button>
      <div className="min-w-0">
        <div className="truncate text-[15px] font-medium text-foreground">{item.title}</div>
        <button
          type="button"
          onClick={() => item.kind === "music" && item.artist && go({ name: "artist", artist: item.artist })}
          className="block max-w-full truncate text-left text-[13px] text-muted-foreground hover:text-foreground hover:underline"
        >
          {item.artist || item.channel}
        </button>
      </div>
      <LikeButton item={item} className="ml-1" />
    </div>
  );
}

function Transport() {
  const playing = usePlayer((s) => s.playing);
  const shuffle = usePlayer((s) => s.shuffle);
  const repeat = usePlayer((s) => s.repeat);
  const position = usePlayer((s) => s.position);
  const total = usePlayer((s) => s.duration);
  const [dragging, setDragging] = useState<number | null>(null);
  const shown = dragging ?? position;

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="flex items-center gap-2">
        <IconButton label={shuffle ? "Desligar aleatório" : "Aleatório"} active={shuffle} onClick={toggleShuffle} size="sm">
          <Shuffle weight="bold" className="h-[18px] w-[18px]" />
        </IconButton>
        <IconButton label="Anterior" onClick={previous} shortcut={<KeyCombo mod="Ctrl" arrow="left" />}>
          <SkipBack weight="fill" className="h-5 w-5" />
        </IconButton>
        <IconButton label={playing ? "Pausar" : "Tocar"} tone="bone" size="lg" onClick={toggle} shortcut="Espaço">
          {playing ? <Pause weight="fill" className="h-5 w-5" /> : <Play weight="fill" className="h-5 w-5 translate-x-px" />}
        </IconButton>
        <IconButton label="Próxima" onClick={() => next()} shortcut={<KeyCombo mod="Ctrl" arrow="right" />}>
          <SkipForward weight="fill" className="h-5 w-5" />
        </IconButton>
        <IconButton
          label={repeat === "off" ? "Repetir a fila" : repeat === "all" ? "Repetir esta" : "Desligar repetição"}
          active={repeat !== "off"}
          onClick={cycleRepeat}
          size="sm"
        >
          {repeat === "one" ? <RepeatOnce weight="bold" className="h-[18px] w-[18px]" /> : <Repeat weight="bold" className="h-[18px] w-[18px]" />}
        </IconButton>
      </div>
      <div className="flex w-full max-w-[560px] items-center gap-2 text-[12px] tabular-nums text-muted-foreground">
        <span className="w-10 text-right">{duration(shown)}</span>
        <Slider
          label="Posição"
          value={shown}
          max={total}
          tone="ember"
          onChange={(v) => setDragging(v)}
          onCommit={(v) => {
            seek(v);
            setDragging(null);
          }}
          className="flex-1"
        />
        <span className="w-10">{duration(total)}</span>
      </div>
    </div>
  );
}

function Extras({ isVideo }: { isVideo: boolean }) {
  const panel = usePlayer((s) => s.panel);
  const volume = usePlayer((s) => s.volume);
  const muted = usePlayer((s) => s.muted);
  const VolumeIcon = muted || volume === 0 ? SpeakerSlash : volume < 0.5 ? SpeakerLow : SpeakerHigh;
  return (
    <div className="flex items-center justify-end gap-1">
      <IconButton label="Letra" active={panel === "lyrics"} onClick={() => setPanel("lyrics")} shortcut="L">
        <MicrophoneStage className="h-5 w-5" />
      </IconButton>
      <IconButton label="Fila" active={panel === "queue"} onClick={() => setPanel("queue")} shortcut="Q">
        <Queue className="h-5 w-5" />
      </IconButton>
      {isVideo && (
        <IconButton label="Tela cheia" onClick={() => void setCinema(true)} shortcut="F">
          <CornersOut className="h-5 w-5" />
        </IconButton>
      )}
      <IconButton label={muted ? "Ativar som" : "Silenciar"} onClick={toggleMute} shortcut="M">
        <VolumeIcon className="h-5 w-5" />
      </IconButton>
      <Slider label="Volume" value={muted ? 0 : volume} max={1} onChange={setVolume} className="w-28" />
    </div>
  );
}
