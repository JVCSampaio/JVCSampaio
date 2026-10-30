import { create } from "zustand";

export type LibraryTab = "music" | "videos" | "artists" | "albums";

export type Route =
  | { name: "home" }
  | { name: "search"; query?: string }
  | { name: "library"; tab?: LibraryTab }
  | { name: "artist"; artist: string }
  | { name: "album"; album: string; artist: string }
  | { name: "playlist"; id: number }
  | { name: "downloads" }
  | { name: "settings"; section?: "account" };

interface RouterState {
  route: Route;
  history: Route[];
  go(route: Route): void;
  back(): void;
}

export const useRouter = create<RouterState>()((set, get) => ({
  route: { name: "home" },
  history: [],
  go(route) {
    const current = get().route;
    if (JSON.stringify(current) === JSON.stringify(route)) return;
    set({ route, history: [...get().history.slice(-30), current] });
    document.getElementById("main-scroll")?.scrollTo({ top: 0 });
  },
  back() {
    const history = get().history;
    const previous = history[history.length - 1];
    if (!previous) return;
    set({ route: previous, history: history.slice(0, -1) });
  },
}));

export const go = (route: Route) => useRouter.getState().go(route);
