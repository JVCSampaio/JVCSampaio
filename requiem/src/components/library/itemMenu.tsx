import {
  FolderOpen,
  ListPlus,
  MinusCircle,
  Plus,
  Queue,
  Skull,
  SkipForward,
  UserSound,
} from "@phosphor-icons/react";
import type { MenuEntry } from "@/components/ui/Menu";
import { backend } from "@/lib/backend";
import type { Item, Playlist } from "@/lib/types";
import { showDialog } from "@/state/dialogs";
import { enqueue } from "@/state/player";
import { go } from "@/state/router";
import { toast } from "@/state/toasts";

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
    { type: "item", label: "Tocar em seguida", icon: <SkipForward />, onSelect: () => enqueue([item.id], { next: true }) },
    { type: "item", label: "Adicionar à fila", icon: <Queue />, onSelect: () => enqueue([item.id]) },
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
    { type: "item", label: "Mostrar no computador", icon: <FolderOpen />, onSelect: async () => (await backend()).itemReveal(item.id).catch((e) => toast(String(e), "error")) },
    { type: "separator" },
    { type: "item", label: "Apagar do computador", icon: <Skull />, danger: true, onSelect: () => showDialog({ type: "delete", item }) },
  ];
}
