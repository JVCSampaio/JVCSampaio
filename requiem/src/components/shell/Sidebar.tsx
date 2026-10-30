import {
  Books,
  DownloadSimple,
  GearSix,
  Ghost,
  House,
  MagnifyingGlass,
  MoonStars,
  Plus,
  Skull,
  WarningCircle,
} from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { IconButton, Kbd } from "@/components/ui/Button";
import { ago, cn } from "@/lib/format";
import { showDialog } from "@/state/dialogs";
import { isAccountConnected, useLibrary } from "@/state/library";
import { go, useRouter, type Route } from "@/state/router";

function NavItem({ icon, label, active, onClick, end }: { icon: ReactNode; label: string; active: boolean; onClick: () => void; end?: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={active ? "page" : undefined}
      className={cn(
        "relative flex h-10 w-full items-center gap-3 rounded-control px-3 text-[15px] font-medium",
        "transition-[background-color,color] [transition-duration:var(--dur-fast)]",
        active ? "bg-raised text-foreground" : "text-muted-foreground hover:bg-raised/60 hover:text-foreground",
      )}
    >
      {active && <span className="absolute left-0 top-2 bottom-2 w-0.5 rounded-full bg-primary" aria-hidden />}
      <span className="[&_svg]:h-5 [&_svg]:w-5">{icon}</span>
      <span className="flex-1 text-left">{label}</span>
      {end}
    </button>
  );
}

function routeIs(route: Route, name: Route["name"] | Route["name"][]) {
  return Array.isArray(name) ? name.includes(route.name) : route.name === name;
}

export function Sidebar() {
  const route = useRouter((s) => s.route);
  const playlists = useLibrary((s) => s.playlists);
  const items = useLibrary((s) => s.items);
  const sync = useLibrary((s) => s.sync);
  const settings = useLibrary((s) => s.settings);
  const active = Object.values(items).filter((i) => i.status === "downloading" || i.status === "queued").length;
  const connected = isAccountConnected(settings);

  return (
    <aside className="flex min-h-0 flex-col border-r border-border bg-surface">
      <div className="flex items-center gap-2 px-5 pb-2 pt-5">
        <Skull weight="fill" className="h-7 w-7 text-primary" aria-hidden />
        <span className="font-display text-[30px] font-semibold leading-none text-foreground">Requiem</span>
      </div>

      <nav className="mt-6 flex flex-col gap-1 px-3" aria-label="Seções">
        <NavItem icon={<House weight={routeIs(route, "home") ? "fill" : "regular"} />} label="Início" active={routeIs(route, "home")} onClick={() => go({ name: "home" })} />
        <NavItem
          icon={<MagnifyingGlass weight={routeIs(route, "search") ? "bold" : "regular"} />}
          label="Buscar"
          active={routeIs(route, "search")}
          onClick={() => go({ name: "search" })}
          end={<Kbd>Ctrl K</Kbd>}
        />
        <NavItem
          icon={<Books weight={routeIs(route, ["library", "artist", "album"]) ? "fill" : "regular"} />}
          label="Biblioteca"
          active={routeIs(route, ["library", "artist", "album"])}
          onClick={() => go({ name: "library" })}
        />
        <NavItem
          icon={<DownloadSimple weight={routeIs(route, "downloads") ? "bold" : "regular"} />}
          label="Downloads"
          active={routeIs(route, "downloads")}
          onClick={() => go({ name: "downloads" })}
          end={active > 0 ? <span className="text-[13px] tabular-nums text-primary">{active}</span> : undefined}
        />
        <NavItem icon={<GearSix weight={routeIs(route, "settings") ? "fill" : "regular"} />} label="Ajustes" active={routeIs(route, "settings")} onClick={() => go({ name: "settings" })} />
      </nav>

      <div className="mt-8 flex items-center justify-between px-6">
        <h2 className="text-[13px] font-medium text-faint-foreground">Playlists</h2>
        <IconButton label="Nova playlist" size="sm" onClick={() => showDialog({ type: "newPlaylist", itemIds: [] })}>
          <Plus className="h-4 w-4" />
        </IconButton>
      </div>
      <div className="mt-1 min-h-0 flex-1 overflow-y-auto px-3 pb-4">
        {playlists.map((p) => {
          const on = route.name === "playlist" && route.id === p.id;
          return (
            <button
              key={p.id}
              type="button"
              onClick={() => go({ name: "playlist", id: p.id })}
              className={cn(
                "flex h-9 w-full items-center rounded-control px-3 text-left text-[15px]",
                "transition-[background-color,color] [transition-duration:var(--dur-fast)]",
                on ? "bg-raised text-foreground" : "text-muted-foreground hover:bg-raised/60 hover:text-foreground",
              )}
            >
              <span className="truncate">{p.name}</span>
            </button>
          );
        })}
      </div>

      <button
        type="button"
        onClick={() => go(connected ? { name: "downloads" } : { name: "settings", section: "account" })}
        className="flex items-center gap-3 border-t border-border px-6 py-4 text-left text-[13px] text-muted-foreground transition-[color] [transition-duration:var(--dur-fast)] hover:text-foreground"
      >
        {!connected ? (
          <>
            <Ghost className="h-5 w-5 shrink-0" />
            <span>YouTube desconectado. Conectar.</span>
          </>
        ) : sync?.running ? (
          <>
            <MoonStars weight="fill" className="h-5 w-5 shrink-0 animate-pulse text-primary" />
            <span>Lendo o seu YouTube</span>
          </>
        ) : sync?.lastError ? (
          <>
            <WarningCircle weight="fill" className="h-5 w-5 shrink-0 text-destructive" />
            <span className="text-destructive">A última sincronização falhou</span>
          </>
        ) : (
          <>
            <MoonStars className="h-5 w-5 shrink-0" />
            <span>{sync?.lastRunAt ? `Sincronizado ${ago(sync.lastRunAt)}` : "Primeira sincronização em instantes"}</span>
          </>
        )}
      </button>
    </aside>
  );
}
