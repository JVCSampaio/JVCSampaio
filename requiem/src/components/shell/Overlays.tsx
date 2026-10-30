import { ArrowCircleUp, ArrowClockwise, HourglassMedium, WarningCircle, X } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { ProgressBar, TextField } from "@/components/ui/Controls";
import { Dialog } from "@/components/ui/Dialog";
import { backend } from "@/lib/backend";
import { cn, count, percent } from "@/lib/format";
import { useDialogs } from "@/state/dialogs";
import { importEvent } from "@/state/imports";
import type { Item } from "@/lib/types";
import { useLibrary } from "@/state/library";
import { go } from "@/state/router";
import { toast, useToasts } from "@/state/toasts";

const DISMISSED = "requiem.update.dismissed";

/** A newer version is published: one line with the way to get it, until the person says no. */
export function UpdateBanner() {
  const update = useLibrary((s) => s.update);
  const [dismissed, setDismissed] = useState(() => {
    try {
      return localStorage.getItem(DISMISSED);
    } catch {
      return null;
    }
  });
  if (!update || dismissed === update.version) return null;
  return (
    <div className="mx-8 mt-6 flex items-center gap-4 rounded-surface border border-border bg-card px-5 py-4">
      <ArrowCircleUp weight="duotone" className="h-9 w-9 shrink-0 text-primary" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="text-[15px] font-medium text-foreground">Nova versão do Requiem: {update.version}</p>
        <p className="text-[13px] text-muted-foreground">Baixe e instale por cima. Músicas, playlists e ajustes continuam.</p>
      </div>
      <Button variant="primary" onClick={async () => (await backend()).updateOpen().catch((e) => toast(String(e), "error"))}>
        Baixar
      </Button>
      <IconButton
        label="Agora não"
        size="sm"
        onClick={() => {
          try {
            localStorage.setItem(DISMISSED, update.version);
          } catch {
            // Without storage the notice simply comes back next time.
          }
          setDismissed(update.version);
        }}
      >
        <X className="h-4 w-4" />
      </IconButton>
    </div>
  );
}

/** The first run downloads the engine. Nothing is blocked: the library still plays. */
export function ToolsBanner() {
  const tools = useLibrary((s) => s.tools);
  const incomplete = !!tools.status && !tools.status.ready;
  if (!tools.installing && !tools.error && !incomplete) return null;
  const step = tools.step;
  return (
    <div className="mx-8 mt-6 rounded-surface border border-border bg-card px-5 py-4">
      {tools.installing || !tools.error ? (
        <div className="flex items-center gap-4">
          <HourglassMedium weight="duotone" className="h-9 w-9 shrink-0 text-primary" aria-hidden />
          <div className="min-w-0 flex-1">
            <p className="text-[15px] font-medium text-foreground">
              Preparando o motor{step ? `, ${step.index + 1} de ${step.total}` : ""}
            </p>
            <p className="truncate text-[13px] text-muted-foreground">{step?.label ?? "Verificando o que já está instalado"}</p>
            {step?.fraction != null && <ProgressBar fraction={step.fraction} className="mt-2" />}
          </div>
          {step?.fraction != null && <span className="text-sm tabular-nums text-muted-foreground">{percent(step.fraction)}</span>}
        </div>
      ) : (
        <div className="flex items-center gap-4">
          <WarningCircle weight="fill" className="h-6 w-6 shrink-0 text-destructive" aria-hidden />
          <p className="min-w-0 flex-1 text-sm text-foreground">{tools.error}</p>
          <Button
            size="sm"
            icon={<ArrowClockwise className="h-4 w-4" />}
            onClick={async () => {
              useLibrary.setState({ tools: { ...tools, installing: true, error: null } });
              (await backend()).toolsInstall();
            }}
          >
            Tentar de novo
          </Button>
        </div>
      )}
    </div>
  );
}

export function Toasts() {
  const toasts = useToasts((s) => s.toasts);
  const dismiss = useToasts((s) => s.dismiss);
  return (
    <div className="pointer-events-none fixed bottom-[100px] right-6 z-50 flex w-[360px] flex-col gap-2" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={cn(
            "menu-content pointer-events-auto flex items-start gap-3 rounded-surface border bg-raised px-4 py-3 text-sm shadow-2xl shadow-black/50",
            t.tone === "error" ? "border-destructive/40 text-foreground" : "border-border text-foreground",
          )}
          data-state="open"
        >
          {t.tone === "error" && <WarningCircle weight="fill" className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />}
          <span className="min-w-0 flex-1">{t.message}</span>
          {t.action && (
            <button type="button" className="font-medium text-primary hover:underline" onClick={t.action.run}>
              {t.action.label}
            </button>
          )}
          <button type="button" aria-label="Fechar" onClick={() => dismiss(t.id)} className="text-faint-foreground hover:text-foreground">
            <X className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  );
}

export function DialogsHost() {
  const open = useDialogs((s) => s.open);
  const close = useDialogs((s) => s.close);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setBusy(false);
    setName(open?.type === "renamePlaylist" ? open.playlist.name : "");
  }, [open]);

  if (!open) return null;
  const onOpenChange = (v: boolean) => !v && close();

  if (open.type === "delete") {
    const { item } = open;
    const local = item.source === "local";
    return (
      <Dialog
        open
        onOpenChange={onOpenChange}
        title={local ? `Tirar "${item.title}" da biblioteca?` : `Apagar "${item.title}"?`}
        description={
          local
            ? "O arquivo continua na sua pasta. O Requiem para de mostrar ele, mesmo nas próximas leituras da pasta."
            : "O arquivo sai do computador e a sincronização não baixa de novo. Dá para buscar e baixar outra vez quando quiser."
        }
      >
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={close}>
            Cancelar
          </Button>
          <Button
            variant="danger"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              await (await backend()).itemDelete(item.id, !local).catch((e) => toast(String(e), "error"));
              close();
            }}
          >
            {local ? "Tirar da biblioteca" : "Apagar"}
          </Button>
        </div>
      </Dialog>
    );
  }

  if (open.type === "edit") return <EditInfoDialog key={open.item.id} item={open.item} onClose={close} />;
  if (open.type === "pasteList") return <PasteListDialog onClose={close} />;

  if (open.type === "deletePlaylist") {
    const { playlist } = open;
    return (
      <Dialog open onOpenChange={onOpenChange} title={`Apagar a playlist "${playlist.name}"?`} description="As músicas continuam na biblioteca.">
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={close}>
            Cancelar
          </Button>
          <Button
            variant="danger"
            onClick={async () => {
              await (await backend()).playlistDelete(playlist.id);
              close();
              go({ name: "library" });
            }}
          >
            Apagar playlist
          </Button>
        </div>
      </Dialog>
    );
  }

  const renaming = open.type === "renamePlaylist";
  const submit = async () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    setBusy(true);
    const api = await backend();
    if (open.type === "renamePlaylist") {
      await api.playlistRename(open.playlist.id, trimmed);
      close();
    } else {
      const id = await api.playlistCreate(trimmed, open.itemIds);
      close();
      if (open.itemIds.length) toast(`Playlist "${trimmed}" criada.`);
      else go({ name: "playlist", id });
    }
  };
  return (
    <Dialog open onOpenChange={onOpenChange} title={renaming ? "Renomear playlist" : "Nova playlist"}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <TextField autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Nome da playlist" maxLength={80} aria-label="Nome da playlist" />
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={close}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" disabled={!name.trim() || busy}>
            {renaming ? "Salvar" : "Criar"}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** A list from anywhere: "Artist - Title" lines, an Exportify CSV, Spotify links. The songs
 *  are found on YouTube in the background; Downloads shows how it goes. */
function PasteListDialog({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState("");
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const api = await backend();
      const tracks = await api.pasteList(text);
      const started = await api.importList(name.trim() || "Lista colada", tracks);
      importEvent(started);
      onClose();
      toast(`Procurando ${count(tracks.length, "música", "músicas")} no YouTube.`, "info", { label: "Ver em Downloads", run: () => go({ name: "downloads" }) });
    } catch (e) {
      setError(String(e));
      setBusy(false);
    }
  };
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()} title="Colar uma lista" description="Uma música por linha, com o artista e o nome dela. Também vale o CSV do Exportify ou links do Spotify.">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <TextField value={name} onChange={(e) => setName(e.target.value)} placeholder="Nome da playlist" maxLength={80} aria-label="Nome da playlist" />
        <textarea
          autoFocus
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={8}
          spellCheck={false}
          aria-label="Lista de músicas"
          placeholder={"Saint-Saëns Danse Macabre\nGrieg Na Gruta do Rei da Montanha"}
          className="mt-3 w-full resize-y rounded-control border border-border bg-surface px-3 py-2 text-[15px] text-foreground placeholder:text-faint-foreground focus-visible:border-border-strong focus-visible:outline-none"
        />
        {error && <p className="mt-2 text-sm text-destructive">{error}</p>}
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" disabled={!text.trim() || busy}>
            Trazer as músicas
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** The person's own names for a song, for when YouTube or the tags got them wrong. */
function EditInfoDialog({ item, onClose }: { item: Item; onClose: () => void }) {
  const [title, setTitle] = useState(item.title);
  const [artist, setArtist] = useState(item.artist);
  const [album, setAlbum] = useState(item.album ?? "");
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    if (!title.trim()) return;
    setBusy(true);
    try {
      await (await backend()).itemEdit(item.id, title, artist, album.trim() || null);
      onClose();
    } catch (e) {
      toast(String(e), "error");
      setBusy(false);
    }
  };
  return (
    <Dialog open onOpenChange={(v) => !v && onClose()} title="Editar informações" description="Vale só no Requiem. O arquivo continua como está.">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <label className="block text-[13px] text-muted-foreground">
          Título
          <TextField autoFocus className="mt-1" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
        </label>
        <label className="block text-[13px] text-muted-foreground">
          Artista
          <TextField className="mt-1" value={artist} onChange={(e) => setArtist(e.target.value)} maxLength={200} />
        </label>
        <label className="block text-[13px] text-muted-foreground">
          Álbum
          <TextField className="mt-1" value={album} onChange={(e) => setAlbum(e.target.value)} maxLength={200} />
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" disabled={!title.trim() || busy}>
            Salvar
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
