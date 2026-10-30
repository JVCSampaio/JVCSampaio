import { ArrowClockwise, WarningCircle } from "@phosphor-icons/react";
import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import { useEffect } from "react";
import { MediaStage } from "@/components/shell/MediaStage";
import { NowPlayingPanel } from "@/components/shell/NowPlaying";
import { DialogsHost, Toasts, ToolsBanner, UpdateBanner } from "@/components/shell/Overlays";
import { PlayerBar } from "@/components/shell/PlayerBar";
import { Sidebar } from "@/components/shell/Sidebar";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Controls";
import { EmptyState } from "@/components/ui/EmptyState";
import { backend, isTauri } from "@/lib/backend";
import { WAIT } from "@/lib/motion";
import { useDelayed } from "@/lib/useDelayed";
import { DownloadsPage } from "@/pages/DownloadsPage";
import { HomePage } from "@/pages/HomePage";
import { LikedPage } from "@/pages/LikedPage";
import { StatsPage } from "@/pages/StatsPage";
import { AlbumPage, ArtistPage, LibraryPage } from "@/pages/LibraryPage";
import { PlaylistPage } from "@/pages/PlaylistPage";
import { SearchPage } from "@/pages/SearchPage";
import { SettingsPage } from "@/pages/SettingsPage";
import { useLibrary } from "@/state/library";
import {
  installPlayer,
  next,
  previous,
  seekBy,
  setCinema,
  setPanel,
  setVolume,
  toggle,
  toggleMute,
  usePlayer,
} from "@/state/player";
import { go, useRouter, type Route } from "@/state/router";

let started = false;

async function start() {
  if (started) return;
  started = true;
  await useLibrary.getState().init();
  await installPlayer();
  const api = await backend();
  if (await api.tourMark("start").catch(() => false)) {
    const { runTour } = await import("@/lib/tour");
    void runTour(api);
  }
}

export function App() {
  const ready = useLibrary((s) => s.ready);
  const loadError = useLibrary((s) => s.loadError);
  const route = useRouter((s) => s.route);

  useEffect(() => {
    void start();
  }, []);
  useShortcuts();

  return (
    <TooltipPrimitive.Provider delayDuration={500} skipDelayDuration={200}>
      <div className="atmosphere grid h-full grid-rows-[minmax(0,1fr)_auto]">
        <div className="grid min-h-0 grid-cols-[248px_minmax(0,1fr)_auto]">
          <Sidebar />
          <main id="main-scroll" className="relative min-h-0 overflow-y-auto" aria-label="Conteúdo">
            <div id="main-content" className="@container flex min-h-full flex-col">
              <UpdateBanner />
              <ToolsBanner />
              <div className="grid flex-1 grid-cols-[minmax(0,1fr)]">{!ready ? <PageSkeleton /> : loadError ? <LoadError message={loadError} /> : <Page route={route} />}</div>
            </div>
          </main>
          <NowPlayingPanel />
        </div>
        <PlayerBar />
      </div>
      <MediaStage />
      <Toasts />
      <DialogsHost />
    </TooltipPrimitive.Provider>
  );
}

function Page({ route }: { route: Route }) {
  switch (route.name) {
    case "home":
      return <HomePage />;
    case "search":
      return <SearchPage initial={route.query} />;
    case "library":
      return <LibraryPage tab={route.tab ?? "music"} />;
    case "artist":
      return <ArtistPage key={route.artist} artist={route.artist} />;
    case "album":
      return <AlbumPage key={`${route.album}-${route.artist}`} album={route.album} artist={route.artist} />;
    case "playlist":
      return <PlaylistPage key={route.id} id={route.id} />;
    case "liked":
      return <LikedPage />;
    case "stats":
      return <StatsPage />;
    case "downloads":
      return <DownloadsPage />;
    case "settings":
      return <SettingsPage section={route.section} />;
  }
}

function PageSkeleton() {
  const shown = useDelayed(true, WAIT.showAfter);
  if (!shown) return null;
  return (
    <div className="px-8 pt-8" aria-label="Carregando">
      <Skeleton className="h-12 w-72" />
      <div className="mt-8 grid grid-cols-2 gap-2 @5xl:grid-cols-3">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-16" />
        ))}
      </div>
    </div>
  );
}

function LoadError({ message }: { message: string }) {
  return (
    <div className="flex items-center justify-center">
      <EmptyState
        icon={<WarningCircle weight="duotone" />}
        title="O motor não respondeu"
        actions={
          <Button variant="primary" icon={<ArrowClockwise className="h-4 w-4" />} onClick={() => window.location.reload()}>
            Tentar de novo
          </Button>
        }
      >
        <span data-selectable>{message}</span>
      </EmptyState>
    </div>
  );
}

function focusSearch() {
  if (useRouter.getState().route.name !== "search") go({ name: "search" });
  requestAnimationFrame(() => {
    const input = document.getElementById("search-input") as HTMLInputElement | null;
    input?.focus();
    input?.select();
  });
}

/** Space plays and pauses anywhere outside a text field, like on any music player. */
function useShortcuts() {
  useEffect(() => {
    const typingIn = (target: EventTarget | null) => {
      const el = target as HTMLElement | null;
      return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
    };
    // Controls that answer to Space themselves keep it.
    const ownsSpace = (target: EventTarget | null) =>
      !!(target as HTMLElement | null)?.closest?.("[role=switch], [role=radio], [role=checkbox], [role=menuitem], [role=option], [role=slider]");

    const onKey = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey;
      if (mod && e.key.toLowerCase() === "k") {
        e.preventDefault();
        focusSearch();
        return;
      }
      if (e.key === "Escape" && usePlayer.getState().cinema) {
        void setCinema(false);
        return;
      }
      if (e.altKey && e.key === "ArrowLeft") {
        e.preventDefault();
        useRouter.getState().back();
        return;
      }
      if (typingIn(e.target) || e.defaultPrevented) return;

      if (e.key === " " && !ownsSpace(e.target)) {
        e.preventDefault();
        if (!e.repeat) toggle();
        return;
      }
      const arrows: Record<string, () => void> | null = mod
        ? {
            ArrowRight: () => next(),
            ArrowLeft: previous,
            ArrowUp: () => setVolume(usePlayer.getState().volume + 0.1),
            ArrowDown: () => setVolume(usePlayer.getState().volume - 0.1),
          }
        : e.shiftKey
          ? { ArrowRight: () => seekBy(10), ArrowLeft: () => seekBy(-10) }
          : null;
      const action = arrows?.[e.key];
      if (action) {
        e.preventDefault();
        action();
        return;
      }
      if (mod || e.altKey) return;

      switch (e.key.toLowerCase()) {
        case "m":
          toggleMute();
          break;
        case "l":
          setPanel("lyrics");
          break;
        case "q":
          setPanel("queue");
          break;
        case "f": {
          const { queue, index, cinema } = usePlayer.getState();
          const id = queue[index];
          if (id && useLibrary.getState().items[id]?.kind === "video") void setCinema(!cinema);
          break;
        }
      }
    };
    // A Space released on a focused button would click it after the keydown toggled playback.
    const onKeyUp = (e: KeyboardEvent) => {
      if (e.key === " " && !typingIn(e.target) && !ownsSpace(e.target)) e.preventDefault();
    };
    const onMouse = (e: MouseEvent) => {
      if (e.button === 3) useRouter.getState().back();
    };
    // Inside the app window, the right click belongs to the app's own menus.
    const onContextMenu = (e: MouseEvent) => {
      if (!(e.target as HTMLElement | null)?.closest?.("input, textarea, [data-selectable]")) e.preventDefault();
    };

    window.addEventListener("keydown", onKey);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("mouseup", onMouse);
    const blockMenu = isTauri && !import.meta.env.DEV;
    if (blockMenu) window.addEventListener("contextmenu", onContextMenu);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("mouseup", onMouse);
      if (blockMenu) window.removeEventListener("contextmenu", onContextMenu);
    };
  }, []);
}
