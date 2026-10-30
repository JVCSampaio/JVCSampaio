import { Books, FolderOpen, MagnifyingGlass, X } from "@phosphor-icons/react";
import { useMemo, useState } from "react";
import { BackBar, Hero, PlayActions } from "@/components/library/Hero";
import { Section, Shelf, VideoTile } from "@/components/library/Tiles";
import { sortTracks, TrackList, type TrackSort } from "@/components/library/TrackList";
import { Button, IconButton } from "@/components/ui/Button";
import { Segmented, TextField } from "@/components/ui/Controls";
import { Cover } from "@/components/ui/Cover";
import { EmptyState } from "@/components/ui/EmptyState";
import { count, key, matches, totalDuration } from "@/lib/format";
import type { Item } from "@/lib/types";
import { isAccountConnected, libraryItems, useLibrary } from "@/state/library";
import { coverUrl } from "@/state/player";
import { go, type LibraryTab } from "@/state/router";

interface ArtistGroup {
  name: string;
  items: Item[];
  cover: string | null;
}

interface AlbumGroup {
  name: string;
  artist: string;
  items: Item[];
  cover: string | null;
}

const collator = new Intl.Collator("pt-BR", { sensitivity: "base", numeric: true });

function firstCover(items: Item[]): string | null {
  for (const i of items) {
    const c = coverUrl(i);
    if (c) return c;
  }
  return null;
}

function groupArtists(songs: Item[]): ArtistGroup[] {
  const groups = new Map<string, ArtistGroup>();
  for (const song of songs) {
    if (!song.artist) continue;
    const k = key(song.artist);
    const g = groups.get(k) ?? { name: song.artist, items: [], cover: null };
    g.items.push(song);
    groups.set(k, g);
  }
  return [...groups.values()]
    .map((g) => ({ ...g, cover: firstCover([...g.items].sort((a, b) => b.playCount - a.playCount)) }))
    .sort((a, b) => collator.compare(a.name, b.name));
}

function groupAlbums(songs: Item[]): AlbumGroup[] {
  const groups = new Map<string, AlbumGroup>();
  for (const song of songs) {
    if (!song.album) continue;
    const k = `${key(song.album)}\u0000${key(song.artist)}`;
    const g = groups.get(k) ?? { name: song.album, artist: song.artist, items: [], cover: null };
    g.items.push(song);
    groups.set(k, g);
  }
  return [...groups.values()].map((g) => ({ ...g, cover: firstCover(g.items) })).sort((a, b) => collator.compare(a.name, b.name));
}

const searchable = (i: Item) => `${i.title} ${i.artist} ${i.album ?? ""} ${i.channel}`;

export function LibraryPage({ tab }: { tab: LibraryTab }) {
  const items = useLibrary((s) => s.items);
  const settings = useLibrary((s) => s.settings);
  const local = useLibrary((s) => s.local);
  const [filter, setFilter] = useState("");
  const [sort, setSort] = useState<TrackSort>({ key: "added", dir: "desc" });

  const all = useMemo(() => libraryItems(items), [items]);
  const songs = useMemo(() => all.filter((i) => i.kind === "music"), [all]);
  const videos = useMemo(() => all.filter((i) => i.kind === "video"), [all]);
  const artists = useMemo(() => groupArtists(songs), [songs]);
  const albums = useMemo(() => groupAlbums(songs), [songs]);

  const q = filter.trim();
  const shownSongs = useMemo(() => sortTracks(q ? songs.filter((i) => matches(searchable(i), q)) : songs, sort), [songs, q, sort]);
  const shownVideos = useMemo(() => (q ? videos.filter((i) => matches(searchable(i), q)) : videos), [videos, q]);
  const shownArtists = useMemo(() => (q ? artists.filter((a) => matches(a.name, q)) : artists), [artists, q]);
  const shownAlbums = useMemo(() => (q ? albums.filter((a) => matches(`${a.name} ${a.artist}`, q)) : albums), [albums, q]);

  if (!all.length) {
    const connected = isAccountConnected(settings);
    if (local.running) {
      return (
        <div className="flex min-h-full items-center justify-center">
          <EmptyState icon={<FolderOpen weight="duotone" />} title="Lendo o seu computador">
            As músicas e os vídeos das suas pastas de música aparecem aqui em instantes.
          </EmptyState>
        </div>
      );
    }
    return (
      <div className="flex min-h-full items-center justify-center">
        <EmptyState
          icon={<Books weight="duotone" />}
          title="Nada guardado ainda"
          actions={
            <>
              <Button variant="primary" size="lg" onClick={() => go({ name: "search" })}>
                Buscar uma música
              </Button>
              <Button variant="ghost" size="lg" icon={<FolderOpen className="h-5 w-5" />} onClick={() => go({ name: "settings", section: "folders" })}>
                Escolher pastas de música
              </Button>
              {!connected && (
                <Button variant="ghost" size="lg" onClick={() => go({ name: "settings", section: "account" })}>
                  Conectar o YouTube
                </Button>
              )}
            </>
          }
        >
          Aqui ficam as músicas que você baixa e as que já estão nas pastas de música do computador, separadas em músicas, vídeos, artistas e álbuns.
        </EmptyState>
      </div>
    );
  }

  const list = tab === "music" ? shownSongs : tab === "videos" ? shownVideos : [];
  const nothing =
    (tab === "music" && !shownSongs.length) ||
    (tab === "videos" && !shownVideos.length) ||
    (tab === "artists" && !shownArtists.length) ||
    (tab === "albums" && !shownAlbums.length);

  return (
    <div className="px-8 pb-12 pt-8">
      <h1 className="font-display text-5xl font-semibold text-foreground">Biblioteca</h1>
      {local.running && (
        <p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground" aria-live="polite">
          <FolderOpen className="h-4 w-4 animate-pulse text-primary" />
          Lendo as pastas de música do computador
        </p>
      )}
      <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
        <Segmented<LibraryTab>
          label="Mostrar"
          value={tab}
          onChange={(t) => go({ name: "library", tab: t })}
          options={[
            { value: "music", label: "Músicas", count: songs.length },
            { value: "videos", label: "Vídeos", count: videos.length },
            { value: "artists", label: "Artistas", count: artists.length },
            { value: "albums", label: "Álbuns", count: albums.length },
          ]}
        />
        <TextField
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          placeholder="Filtrar a biblioteca"
          aria-label="Filtrar a biblioteca"
          spellCheck={false}
          className="w-72"
          icon={<MagnifyingGlass className="h-4 w-4 shrink-0" />}
          end={
            filter ? (
              <IconButton label="Limpar o filtro" size="sm" className="-mr-2" onClick={() => setFilter("")}>
                <X className="h-4 w-4" />
              </IconButton>
            ) : null
          }
        />
      </div>

      {(tab === "music" || tab === "videos") && list.length > 0 && (
        <div className="mt-8 flex items-center gap-4">
          <PlayActions items={list} />
          <span className="ml-2 text-sm text-muted-foreground">
            {count(list.length, tab === "music" ? "música" : "vídeo", tab === "music" ? "músicas" : "vídeos")}, {totalDuration(list.reduce((s, i) => s + (i.duration ?? 0), 0))}
          </span>
        </div>
      )}

      <div className="mt-8">
        {nothing ? (
          <TabEmpty tab={tab} filter={q} onClear={() => setFilter("")} />
        ) : tab === "music" ? (
          <TrackList items={shownSongs} sort={sort} onSort={setSort} />
        ) : tab === "videos" ? (
          <div className="-mx-2 grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-y-2">
            {shownVideos.map((item, i) => (
              <VideoTile key={item.id} item={item} row={shownVideos} index={i} width="w-full" />
            ))}
          </div>
        ) : tab === "artists" ? (
          <div className="-mx-2 grid grid-cols-[repeat(auto-fill,minmax(176px,1fr))] gap-y-4">
            {shownArtists.map((a) => (
              <ArtistTile key={a.name} artist={a} />
            ))}
          </div>
        ) : (
          <div className="-mx-2 grid grid-cols-[repeat(auto-fill,minmax(176px,1fr))] gap-y-4">
            {shownAlbums.map((a) => (
              <AlbumTile key={`${a.name}-${a.artist}`} album={a} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function TabEmpty({ tab, filter, onClear }: { tab: LibraryTab; filter: string; onClear: () => void }) {
  if (filter) {
    return (
      <div className="max-w-xl">
        <p className="text-[15px] text-foreground">Nada aqui combina com "{filter}".</p>
        <Button variant="ghost" size="sm" className="-ml-3 mt-2" onClick={onClear}>
          Limpar o filtro
        </Button>
      </div>
    );
  }
  const text: Record<LibraryTab, string> = {
    music: "Nenhuma música ainda. As que você ouve no YouTube chegam aqui na próxima sincronização.",
    videos: "Nenhum vídeo ainda. Os que você assiste no YouTube chegam aqui na próxima sincronização.",
    artists: "Os artistas aparecem quando as músicas baixadas trazem o nome de quem canta.",
    albums: "Os álbuns aparecem quando as músicas baixadas vêm do YouTube Music com o nome do disco.",
  };
  return <p className="max-w-xl text-[15px] text-muted-foreground">{text[tab]}</p>;
}

function ArtistTile({ artist }: { artist: ArtistGroup }) {
  return (
    <button
      type="button"
      onClick={() => go({ name: "artist", artist: artist.name })}
      className="rounded-surface p-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised focus-visible:bg-raised"
    >
      <Cover src={artist.cover} kind="artist" className="w-full" rounded="rounded-full" />
      <div className="mt-3 truncate text-[15px] font-medium text-foreground">{artist.name}</div>
      <div className="truncate text-[13px] text-muted-foreground">{count(artist.items.length, "música", "músicas")}</div>
    </button>
  );
}

function AlbumTile({ album }: { album: AlbumGroup }) {
  return (
    <button
      type="button"
      onClick={() => go({ name: "album", album: album.name, artist: album.artist })}
      className="rounded-surface p-2 text-left transition-[background-color] [transition-duration:var(--dur-fast)] hover:bg-raised focus-visible:bg-raised"
    >
      <Cover src={album.cover} kind="music" className="w-full" />
      <div className="mt-2 truncate text-[15px] font-medium text-foreground">{album.name}</div>
      <div className="truncate text-[13px] text-muted-foreground">{album.artist}</div>
    </button>
  );
}

export function ArtistPage({ artist }: { artist: string }) {
  const items = useLibrary((s) => s.items);
  const [sort, setSort] = useState<TrackSort>({ key: "added", dir: "desc" });
  const all = useMemo(() => libraryItems(items), [items]);
  const k = key(artist);
  const songs = useMemo(() => all.filter((i) => i.kind === "music" && key(i.artist) === k), [all, k]);
  const videos = useMemo(() => all.filter((i) => i.kind === "video" && (key(i.artist) === k || key(i.channel) === k)), [all, k]);
  const albums = useMemo(() => groupAlbums(songs), [songs]);
  const shown = useMemo(() => sortTracks(songs, sort), [songs, sort]);
  const cover = useMemo(() => firstCover([...songs].sort((a, b) => b.playCount - a.playCount)), [songs]);

  if (!songs.length && !videos.length) return <Gone what="Esse artista saiu da biblioteca." />;

  return (
    <div className="pb-12">
      <BackBar />
      <Hero
        cover={<Cover src={cover} kind="artist" className="w-52" rounded="rounded-full" />}
        label="Artista"
        title={artist}
        meta={[songs.length ? count(songs.length, "música", "músicas") : "", videos.length ? count(videos.length, "vídeo", "vídeos") : ""].filter(Boolean).join(", ")}
        actions={<PlayActions items={songs.length ? shown : videos} />}
      />
      <div className="flex flex-col gap-10 px-8">
        {songs.length > 0 && <TrackList items={shown} sort={sort} onSort={setSort} />}
        {albums.length > 0 && (
          <Section title="Álbuns">
            <div className="-mx-2 grid grid-cols-[repeat(auto-fill,minmax(176px,1fr))]">
              {albums.map((a) => (
                <AlbumTile key={a.name} album={a} />
              ))}
            </div>
          </Section>
        )}
        {videos.length > 0 && (
          <Section title="Vídeos">
            <Shelf>
              {videos.map((item, i) => (
                <VideoTile key={item.id} item={item} row={videos} index={i} />
              ))}
            </Shelf>
          </Section>
        )}
      </div>
    </div>
  );
}

export function AlbumPage({ album, artist }: { album: string; artist: string }) {
  const items = useLibrary((s) => s.items);
  const all = useMemo(() => libraryItems(items), [items]);
  // Album order: disc, then track number from the tags or YouTube Music, then arrival.
  const songs = useMemo(
    () =>
      all
        .filter((i) => i.kind === "music" && i.album && key(i.album) === key(album) && key(i.artist) === key(artist))
        .sort(
          (a, b) =>
            (a.discNumber ?? 1) - (b.discNumber ?? 1) ||
            (a.trackNumber ?? Number.MAX_SAFE_INTEGER) - (b.trackNumber ?? Number.MAX_SAFE_INTEGER) ||
            a.addedAt - b.addedAt,
        ),
    [all, album, artist],
  );
  if (!songs.length) return <Gone what="Esse álbum saiu da biblioteca." />;
  const total = songs.reduce((s, i) => s + (i.duration ?? 0), 0);
  const year = songs.find((s) => s.year)?.year;
  return (
    <div className="pb-12">
      <BackBar />
      <Hero
        cover={<Cover src={firstCover(songs)} kind="music" className="w-52" rounded="rounded-surface" />}
        label="Álbum"
        title={album}
        meta={
          <>
            <button type="button" onClick={() => go({ name: "artist", artist })} className="font-medium text-foreground hover:underline">
              {artist}
            </button>
            {year ? `, ${year}` : ""}, {count(songs.length, "música", "músicas")}, {totalDuration(total)}
          </>
        }
        actions={<PlayActions items={songs} />}
      />
      <div className="px-8">
        <TrackList items={songs} showAlbum={false} />
      </div>
    </div>
  );
}

function Gone({ what }: { what: string }) {
  return (
    <div className="flex min-h-full items-center justify-center">
      <EmptyState
        icon={<Books weight="duotone" />}
        title="Não está mais aqui"
        actions={
          <Button variant="primary" onClick={() => go({ name: "library" })}>
            Ir para a biblioteca
          </Button>
        }
      >
        {what}
      </EmptyState>
    </div>
  );
}
