import { create } from "zustand";
import { count } from "@/lib/format";
import type { ImportStatus } from "@/lib/types";
import { go } from "./router";
import { toast } from "./toasts";

/** Lists being brought from Spotify or pasted, newest first, until the person dismisses them. */
interface ImportsState {
  imports: ImportStatus[];
}

export const useImports = create<ImportsState>()(() => ({ imports: [] }));

export function importEvent(status: ImportStatus) {
  const known = useImports.getState().imports;
  const before = known.find((i) => i.id === status.id);
  useImports.setState({ imports: before ? known.map((i) => (i.id === status.id ? status : i)) : [status, ...known] });
  if (!status.finished || before?.finished) return;
  if (status.error && !status.found) {
    toast(status.error, "error");
    return;
  }
  const open = status.playlistId != null ? { label: "Abrir a playlist", run: () => go({ name: "playlist", id: status.playlistId! }) } : undefined;
  toast(`"${status.title}": ${count(status.found, "música encontrada", "músicas encontradas")} de ${status.total}.`, "info", open);
}

export function dismissImport(id: string) {
  useImports.setState({ imports: useImports.getState().imports.filter((i) => i.id !== id) });
}
