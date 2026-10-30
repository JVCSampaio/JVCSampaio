import {
  FolderOpen,
  Heart,
  LinkSimple,
  ListPlus,
  PencilSimple,
  MinusCircle,
  Plus,
  Queue,
  Radio,
  Skull,
  SkipForward,
  UserSound,
} from "@phosphor-icons/react";
import type { MenuEntry } from "@/components/ui/Menu";
import { backend } from "@/lib/backend";
import type { Item, Playlist } from "@/lib/types";
import { showDialog } from "@/state/dialogs";
import { enqueue } from "@/state/player";
import { startRadio } from "@/state/radio";
import { go } from "@/state/router";
import { toast } from "@/state/toasts";
import { toggleLike } from "./LikeButton";

/** Downloads keep the 11-character YouTube id; songs from the computer have their own. */
const fromYouTube = (item: Item) => item.source !== "local" && /^[A-Za-z0-9_-]{11}$/.test(item.id);

/** The actions of a song or video in the library, for its ⋯ button and right click. */
export function itemMenu(item: Item, playlists: Playlist[], inPlaylist?: Playlist): MenuEntry[] {
  const addTo: MenuEntry[] = [
    { type: "item", label: "Nova playlist", icon: <Plus />, onSelect: () => showDialog({ type: "newPlaylist", itemIds: [item.id] }) },
    ...(playlists.length ? [{ type: "separator" } as MenuEntry] : []),
    ...playlists.map<MenuEntry>((p) => ({
      type: "item",
      label: p.name,
      onSelect: async () => {
        const api = await backend();
        await api.playlistAdd(p.id, [item.id]);
        toast(`Adicionada a "${p.name}".`);
      },
    })),
  ];
  return [
    { type: "item", label: item.likedAt ? "Descurtir" : "Curtir", icon: <Heart />, onSelect: () => void toggleLike(item) },
    { type: "item", label: "Tocar em seguida", icon: <SkipForward />, onSelect: () => enqueue([item.id], { next: true }) },
    { type: "item", label: "Adicionar à fila", icon: <Queue />, onSelect: () => enqueue([item.id]) },
    ...(item.kind === "music" ? [{ type: "item", label: "Iniciar rádio", icon: <Radio />, onSelect: () => startRadio(item.id) } as MenuEntry] : []),
    { type: "submenu", label: "Adicionar à playlist", icon: <ListPlus />, entries: addTo },
    ...(item.kind === "music" && item.artist
      ? [{ type: "item", label: "Ver artista", icon: <UserSound />, onSelect: () => go({ name: "artist", artist: item.artist }) } as MenuEntry]
      : []),
    ...(inPlaylist
      ? [
          {
            type: "item",
            label: "Tirar desta playlist",
            icon: <MinusCircle />,
            onSelect: async () => (await backend()).playlistRemove(inPlaylist.id, item.id),
          } as MenuEntry,
        ]
      : []),
    { type: "item", label: "Editar informações", icon: <PencilSimple />, onSelect: () => showDialog({ type: "edit", item }) },
    ...(fromYouTube(item)
      ? [
          {
            type: "item",
            label: "Copiar link do YouTube",
            icon: <LinkSimple />,
            onSelect: () =>
              navigator.clipboard
                .writeText(`https://www.youtube.com/watch?v=${item.id}`)
                .then(() => toast("Link copiado."))
                .catch(() => toast("Não deu para copiar o link.", "error")),
          } as MenuEntry,
        ]
      : []),
    { type: "item", label: "Mostrar no computador", icon: <FolderOpen />, onSelect: async () => (await backend()).itemReveal(item.id).catch((e) => toast(String(e), "error")) },
    { type: "separator" },
    // A song that was already on the computer leaves the library, never the disk.
    item.source === "local"
      ? { type: "item", label: "Tirar da biblioteca", icon: <MinusCircle />, danger: true, onSelect: () => showDialog({ type: "delete", item }) }
      : { type: "item", label: "Apagar do computador", icon: <Skull />, danger: true, onSelect: () => showDialog({ type: "delete", item }) },
  ];
}
