// In-memory stand-in for the Rust engine, used by the browser preview and the screenshot
// script. Same contract as the Tauri backend; downloads advance on a timer.

import type { Backend } from "./backend";
import { mockArt } from "./mockArt";
import type { EngineEvent, Entry, Item, Kind, Lyrics, Play, Playlist, Settings, Stats, SyncStatus, ToolsStatus } from "./types";

const DAY = 86_400_000;
const now = Date.now();

type Seed = [id: string, kind: Kind, title: string, artist: string, album: string | null, duration: number, daysAgo: number, plays: number];

const seeds: Seed[] = [
  ["dmacabre001", "music", "Danse Macabre, Op. 40", "Camille Saint-Saëns", "Poemas Sinfônicos", 431, 0.1, 14],
  ["toccata0002", "music", "Tocata e Fuga em Ré Menor", "Johann Sebastian Bach", "Obras para Órgão", 547, 0.3, 9],
  ["baldmount03", "music", "Uma Noite no Monte Calvo", "Modest Mussorgsky", null, 662, 0.5, 6],
  ["mountking04", "music", "Na Gruta do Rei da Montanha", "Edvard Grieg", "Peer Gynt", 152, 1, 22],
  ["funeral0005", "music", "Sonata nº 2, Marcha Fúnebre", "Frédéric Chopin", "Sonatas para Piano", 548, 1.2, 4],
  ["lacrimosa06", "music", "Lacrimosa", "Wolfgang Amadeus Mozart", "Réquiem em Ré Menor", 196, 1.5, 31],
  ["diesirae007", "music", "Dies Irae", "Giuseppe Verdi", "Messa da Requiem", 133, 2, 12],
  ["nocturne008", "music", "Noturno em Dó Sustenido Menor", "Frédéric Chopin", "Noturnos", 247, 2.4, 8],
  ["moonlight09", "music", "Sonata ao Luar, 3º movimento", "Ludwig van Beethoven", "Sonatas para Piano", 413, 3, 17],
  ["erlking0010", "music", "O Rei dos Elfos", "Franz Schubert", "Lieder", 238, 3.5, 3],
  ["gymnope0011", "music", "Gymnopédie nº 1", "Erik Satie", null, 189, 4, 25],
  ["valsetr0012", "music", "Valsa Triste", "Jean Sibelius", "Kuolema", 330, 5, 2],
  ["neblina0013", "music", "Neblina Sobre o Cemitério", "Os Coveiros", "Vigília", 214, 6, 11],
  ["corvo000014", "music", "O Corvo", "Os Coveiros", "Vigília", 268, 6, 7],
  ["velas000015", "music", "Velas no Porão", "Noite Alta", "Lua Minguante", 201, 8, 5],
  ["campanas016", "music", "Sinos da Meia-Noite", "Noite Alta", "Lua Minguante", 243, 9, 1],
  ["requiem0017", "music", "Introitus", "Wolfgang Amadeus Mozart", "Réquiem em Ré Menor", 291, 11, 0],
  ["fantasia018", "music", "Fantasia Cromática", "Johann Sebastian Bach", "Obras para Teclado", 465, 40, 2],
  ["mangue00019", "music", "Maracatu da Lua Cheia", "Assombração do Recife", null, 236, 45, 0],
  ["vampira0020", "music", "Valsa da Vampira", "Assombração do Recife", null, 199, 50, 1],
  ["local-0000000000000001", "music", "Prelúdio em Mi Menor", "Frédéric Chopin", "Prelúdios", 135, 30, 6],
  ["local-0000000000000002", "music", "Clair de Lune", "Claude Debussy", "Suíte Bergamasque", 302, 31, 9],
  ["local-0000000000000003", "music", "Pavana para uma Infanta Defunta", "Maurice Ravel", null, 389, 32, 2],
  ["local-0000000000000004", "music", "Adagio para Cordas", "Samuel Barber", null, 476, 33, 4],
  ["local-0000000000000005", "music", "Canção da Bruxa", "Noite Alta", "Lua Minguante", 228, 34, 1],
  ["vid00000001", "video", "Como restauramos um órgão de igreja de 1890", "Oficina de Tubos", null, 1412, 0.2, 2],
  ["vid00000002", "video", "Danse Macabre ao vivo na Sala São Paulo", "Orquestra Noturna", null, 512, 0.8, 4],
  ["vid00000003", "video", "Lendas urbanas do Rio de Janeiro", "Arquivo Assombrado", null, 1880, 2.2, 1],
  ["vid00000004", "video", "Maquiagem de caveira em 10 minutos", "Ateliê Sombrio", null, 640, 3.1, 0],
  ["vid00000005", "video", "A história do Dies Irae no cinema", "Trilha e Tela", null, 1105, 4.6, 3],
  ["vid00000006", "video", "Gravando sinos de verdade às 3 da manhã", "Campo Sonoro", null, 903, 7, 0],
];

/** Songs the person already had on the computer, found in the Music folder. */
const localIds = new Set(["nocturne008", "gymnope0011", "valsetr0012", "erlking0010", "fantasia018"]);

function seedItem([id, kind, title, artist, album, duration, daysAgo, plays]: Seed, i: number): Item {
  const at = now - daysAgo * DAY;
  return {
    id,
    kind,
    title,
    artist,
    album,
    channel: artist,
    duration,
    source: id.startsWith("local-") || localIds.has(id) ? "local" : i % 4 === 3 ? "search" : i % 3 === 0 ? "liked" : "history",
    status: "done",
    error: null,
    filePath: `/Músicas/${artist} - ${title} [${id}].m4a`,
    thumbPath: null,
    thumbnailUrl: mockArt(album ?? id, kind === "video"),
    fileSize: duration * 16_000,
    width: kind === "video" ? 1280 : null,
    height: kind === "video" ? 720 : null,
    addedAt: at,
    seenAt: at,
    downloadedAt: at,
    playCount: plays,
    lastPlayedAt: plays ? at + DAY / 3 : null,
    likedAt: plays >= 9 ? at + DAY / 2 : null,
    trackNumber: trackNumbers[id] ?? null,
    discNumber: trackNumbers[id] ? 1 : null,
    year: album ? 1950 + (title.length % 60) : null,
    loudness: null,
    peak: null,
    resumeAt: null,
  };
}

/** Album order for the songs of "Lua Minguante" and "Vigília", out of download order on purpose. */
const trackNumbers: Record<string, number> = {
  campanas016: 1,
  velas000015: 2,
  "local-0000000000000005": 3,
  corvo000014: 1,
  neblina0013: 2,
};

const pendingSeeds: Array<Partial<Item> & Pick<Item, "id" | "title" | "artist" | "status">> = [
  { id: "pend0000001", title: "Carmina Burana, O Fortuna", artist: "Carl Orff", status: "downloading", kind: "music", duration: 158 },
  { id: "pend0000002", title: "Canção de Ninar Assombrada", artist: "Noite Alta", status: "queued", kind: "auto", duration: 222 },
  { id: "pend0000003", title: "Passeio noturno pela Catedral da Sé", artist: "Caminhos de Pedra", status: "queued", kind: "auto", duration: 1320 },
  { id: "pend0000004", title: "Ave Maria (ao vivo)", artist: "Coral das Almas", status: "failed", kind: "music", duration: 301, error: "O vídeo é só para membros do canal." },
  { id: "pend0000005", title: "Trailer: A Casa da Colina", artist: "Cine Medo", status: "skipped", kind: "auto", duration: 48, error: "Short" },
];

export function createMockBackend(): Backend {
  const items = new Map<string, Item>();
  seeds.forEach((s, i) => {
    const it = seedItem(s, i);
    items.set(it.id, it);
  });
  pendingSeeds.forEach((p, i) => {
    items.set(p.id, {
      kind: "auto",
      album: null,
      channel: p.artist,
      duration: null,
      source: "history",
      error: null,
      filePath: null,
      thumbPath: null,
      thumbnailUrl: mockArt(p.id, (p.duration ?? 0) > 900),
      fileSize: null,
      width: null,
      height: null,
      addedAt: now - i * 60_000,
      seenAt: now - i * 60_000,
      downloadedAt: null,
      playCount: 0,
      lastPlayedAt: null,
      likedAt: null,
      trackNumber: null,
      discNumber: null,
      year: null,
      loudness: null,
      peak: null,
      resumeAt: null,
      ...p,
    } as Item);
  });

  // A play log that matches the play counts, spread over the last two months.
  const plays: Play[] = [];
  let nextPlay = 1;
  for (const it of items.values()) {
    for (let k = 0; k < it.playCount; k++) {
      const playedAt = now - ((k * 7 + it.title.length * 3) % 60) * DAY - (k % 5) * 3_600_000;
      plays.push({ id: nextPlay++, itemId: it.id, playedAt, seconds: Math.round((it.duration ?? 200) * (0.6 + ((k * 13) % 40) / 100)) });
    }
  }

  let playlists: Playlist[] = [
    { id: 1, name: "Vigília das três da manhã", itemIds: ["gymnope0011", "nocturne008", "neblina0013", "valsetr0012", "lacrimosa06"], createdAt: now - 20 * DAY },
    { id: 2, name: "Coral e órgão", itemIds: ["toccata0002", "diesirae007", "requiem0017", "lacrimosa06"], createdAt: now - 12 * DAY },
    { id: 3, name: "Festa no cemitério", itemIds: ["dmacabre001", "mountking04", "mangue00019", "vampira0020", "corvo000014"], createdAt: now - 3 * DAY },
  ];
  let nextPlaylist = 4;

  let settings: Settings = {
    libraryDir: "/home/voce/Música/Requiem",
    account: { mode: "app" },
    autoSync: true,
    syncIntervalMinutes: 30,
    syncHistory: true,
    syncLikes: true,
    syncMusicLikes: true,
    firstSyncLimit: 30,
    downloadMusic: true,
    downloadVideos: true,
    downloadShorts: false,
    maxMusicMinutes: 20,
    maxVideoMinutes: 180,
    videoHeight: 720,
    audioFormat: "m4a",
    closeToTray: true,
    launchAtLogin: false,
    musicFolders: null,
  };
  let sync: SyncStatus = { running: false, lastRunAt: now - 12 * 60_000, lastFound: 3, lastError: null, nextRunAt: now + 18 * 60_000 };
  const tools: ToolsStatus = { ytdlp: "2026.10.18", deno: "2.5.4", ffmpeg: "6.1.1", ready: true };

  const newer = { version: "0.1.9", url: "https://github.com/JVCSampaio/JVCSampaio/releases/tag/requiem-latest" };
  const listeners = new Set<(e: EngineEvent) => void>();
  const emit = (e: EngineEvent) => listeners.forEach((l) => l(e));

  // Downloads advance in the background.
  let fraction = 0.37;
  setInterval(() => {
    const active = [...items.values()].find((i) => i.status === "downloading");
    if (!active) {
      const next = [...items.values()].find((i) => i.status === "queued");
      if (next) {
        next.status = "downloading";
        next.kind = next.kind === "auto" ? ((next.duration ?? 0) > 900 ? "video" : "music") : next.kind;
        fraction = 0;
        emit({ type: "item", item: { ...next } });
      }
      return;
    }
    fraction = Math.min(1, fraction + 0.04);
    emit({ type: "progress", id: active.id, fraction, speed: 2_400_000, eta: Math.round((1 - fraction) * 25), stage: "Baixando" });
    if (fraction >= 1) {
      active.status = "done";
      active.downloadedAt = Date.now();
      active.filePath = `/Requiem/${active.title}.m4a`;
      emit({ type: "item", item: { ...active } });
    }
  }, 700);

  const wait = <T,>(value: T, ms = 250) => new Promise<T>((r) => setTimeout(() => r(value), ms));

  const searchPool: Entry[] = [
    ["s0000000001", "Danse Macabre", "Camille Saint-Saëns", 420],
    ["s0000000002", "Danse Macabre (versão para violino)", "Quarteto Crepúsculo", 395],
    ["s0000000003", "Danse Macabre, piano solo", "Ana Noturna", 402],
    ["s0000000004", "Danse Macabre em 8 bits", "Fantasma Digital", 188],
    ["s0000000005", "Dança Macabra (tema de abertura)", "Trilha e Tela", 96],
    ["s0000000006", "Saint-Saëns: Danse Macabre, Orquestra Jovem", "Orquestra Jovem do Sul", 455],
    ["dmacabre001", "Danse Macabre, Op. 40", "Camille Saint-Saëns", 431],
    ["pend0000001", "Carmina Burana, O Fortuna", "Carl Orff", 158],
  ].map(([id, title, artist, duration]) => ({
    id: id as string,
    title: title as string,
    artist: artist as string,
    channel: artist as string,
    duration: duration as number,
    thumbnail: mockArt(id as string),
    isShort: false,
  }));

  const toItem = (entry: Entry, kind: Kind, source: string): Item => ({
    id: entry.id,
    kind,
    title: entry.title,
    artist: entry.artist,
    album: null,
    channel: entry.channel,
    duration: entry.duration,
    source,
    status: "queued",
    error: null,
    filePath: null,
    thumbPath: null,
    thumbnailUrl: entry.thumbnail || null,
    fileSize: null,
    width: null,
    height: null,
    addedAt: Date.now(),
    seenAt: Date.now(),
    downloadedAt: null,
    playCount: 0,
    lastPlayedAt: null,
    likedAt: null,
    trackNumber: null,
    discNumber: null,
    year: null,
    loudness: null,
    peak: null,
    resumeAt: null,
  });

  const lyrics: Lyrics = {
    instrumental: false,
    plain: null,
    lines: [
      { ms: 0, text: "Zig, zig, zag, a Morte em cadência" },
      { ms: 6000, text: "Batendo com o calcanhar numa tumba" },
      { ms: 12000, text: "A Morte à meia-noite toca uma dança" },
      { ms: 18000, text: "Zig, zig, zag, no seu violino" },
      { ms: 24000, text: "O vento de inverno sopra, a noite é escura" },
      { ms: 30000, text: "Gemidos saem das tílias" },
      { ms: 36000, text: "Esqueletos brancos atravessam a sombra" },
      { ms: 42000, text: "Correndo e saltando sob seus sudários" },
      { ms: 48000, text: "Zig, zig, zag, todos se agitam" },
      { ms: 54000, text: "Ouvem-se estalar os ossos dos dançarinos" },
      { ms: 60000, text: "Mas, psiu, de repente deixam a ronda" },
      { ms: 66000, text: "Empurram-se, fogem, o galo cantou" },
    ],
  };

  return {
    overview: () =>
      wait(
        {
          version: "0.1.0",
          mediaBase: "",
          dataDir: "/home/voce/.local/share/requiem",
          libraryDir: settings.libraryDir,
          platform: "linux",
          localFolders: settings.musicFolders ?? ["/home/voce/Música"],
          local: { running: false, found: [...items.values()].filter((i) => i.source === "local").length },
          update: new URLSearchParams(location.search).has("update") ? newer : null,
        },
        50,
      ),
    toolsStatus: () => wait({ status: tools, installing: false, step: null, error: null }, 50),
    toolsInstall: () => wait(undefined),
    toolsUpdate: () => wait(tools, 900),
    library: () => wait([...items.values()].filter((i) => i.status === "done").sort((a, b) => (b.downloadedAt ?? 0) - (a.downloadedAt ?? 0)), 80),
    queue: () => wait([...items.values()].filter((i) => ["queued", "downloading", "failed", "skipped"].includes(i.status)), 80),
    playlists: () => wait(playlists.map((p) => ({ ...p, itemIds: [...p.itemIds] })), 60),
    itemPlayed: async (id) => {
      const it = items.get(id);
      if (it) {
        it.playCount += 1;
        it.lastPlayedAt = Date.now();
        emit({ type: "item", item: { ...it } });
      }
      const play = { id: nextPlay++, itemId: id, playedAt: Date.now(), seconds: 0 };
      plays.push(play);
      return play.id;
    },
    playSeconds: async (playId, seconds) => {
      const play = plays.find((p) => p.id === playId);
      if (play) play.seconds = Math.max(play.seconds, seconds);
    },
    itemLike: async (id, liked) => {
      const it = items.get(id);
      if (!it) return;
      it.likedAt = liked ? it.likedAt ?? Date.now() : null;
      emit({ type: "item", item: { ...it } });
    },
    itemResume: async (id, seconds) => {
      const it = items.get(id);
      if (it) it.resumeAt = seconds;
    },
    itemEdit: async (id, title, artist, album) => {
      const it = items.get(id);
      if (!it) return;
      Object.assign(it, { title: title.trim(), artist: artist.trim(), album: album?.trim() || null });
      emit({ type: "item", item: { ...it } });
    },
    history: (limit) => wait([...plays].filter((p) => items.get(p.itemId)?.status === "done").sort((a, b) => b.playedAt - a.playedAt).slice(0, limit), 120),
    stats: (since) => wait(mockStats(plays, items, since), 200),
    itemDelete: async (id) => {
      items.delete(id);
      playlists = playlists.map((p) => ({ ...p, itemIds: p.itemIds.filter((x) => x !== id) }));
      emit({ type: "removed", id });
      emit({ type: "playlists" });
    },
    itemReveal: () => wait(undefined),
    search: (query) => wait(query.trim() ? searchPool : [], 900),
    resolve: async (text) => {
      if (text.includes("list=")) return wait({ type: "playlist" as const, title: "Clássicos para uma noite fria", entries: searchPool.slice(0, 5) }, 800);
      if (text.includes("youtu")) return wait({ type: "video" as const, entry: searchPool[0] }, 800);
      return { type: "query" as const, text };
    },
    download: async (entry, kind, source = "search") => {
      const existing = items.get(entry.id);
      const it = existing && existing.status === "done" ? existing : toItem(entry, kind, source);
      items.set(it.id, it);
      emit({ type: "item", item: { ...it } });
      return it;
    },
    downloadMany: async (entries, kind, playlist) => {
      entries.forEach((e) => {
        const it = toItem(e, kind, "playlist");
        items.set(it.id, it);
        emit({ type: "item", item: { ...it } });
      });
      if (!playlist) return null;
      const id = nextPlaylist++;
      playlists.push({ id, name: playlist, itemIds: entries.map((e) => e.id), createdAt: Date.now() });
      emit({ type: "playlists" });
      return id;
    },
    downloadRetry: async (id) => {
      const it = items.get(id);
      if (it) {
        it.status = "queued";
        it.error = null;
        emit({ type: "item", item: { ...it } });
      }
    },
    downloadCancel: async (id) => {
      items.delete(id);
      emit({ type: "removed", id });
    },
    downloadsClear: async () => {
      let n = 0;
      for (const it of [...items.values()]) {
        if (it.status === "failed" || it.status === "skipped") {
          items.delete(it.id);
          emit({ type: "removed", id: it.id });
          n++;
        }
      }
      return n;
    },
    playlistCreate: async (name, itemIds) => {
      const id = nextPlaylist++;
      playlists.push({ id, name, itemIds: [...itemIds], createdAt: Date.now() });
      emit({ type: "playlists" });
      return id;
    },
    playlistRename: async (id, name) => {
      playlists = playlists.map((p) => (p.id === id ? { ...p, name } : p));
      emit({ type: "playlists" });
    },
    playlistDelete: async (id) => {
      playlists = playlists.filter((p) => p.id !== id);
      emit({ type: "playlists" });
    },
    playlistAdd: async (id, itemIds) => {
      playlists = playlists.map((p) => (p.id === id ? { ...p, itemIds: [...p.itemIds, ...itemIds.filter((x) => !p.itemIds.includes(x))] } : p));
      emit({ type: "playlists" });
    },
    playlistRemove: async (id, itemId) => {
      playlists = playlists.map((p) => (p.id === id ? { ...p, itemIds: p.itemIds.filter((x) => x !== itemId) } : p));
      emit({ type: "playlists" });
    },
    playlistReorder: async (id, itemIds) => {
      playlists = playlists.map((p) => (p.id === id ? { ...p, itemIds: [...itemIds] } : p));
      emit({ type: "playlists" });
    },
    settingsGet: () => wait(settings, 50),
    settingsSet: async (next) => {
      settings = next;
      emit({ type: "settings", settings });
      return settings;
    },
    syncStatus: () => wait(sync, 50),
    syncNow: async () => {
      sync = { ...sync, running: true };
      emit({ type: "sync", status: sync });
      setTimeout(() => {
        sync = { ...sync, running: false, lastRunAt: Date.now(), lastFound: 0, nextRunAt: Date.now() + 30 * 60_000 };
        emit({ type: "sync", status: sync });
      }, 2500);
    },
    accountLogin: () => wait(undefined),
    accountTest: () => wait({ ok: true, message: "A conta respondeu com o seu histórico.", sample: searchPool.slice(0, 5) }, 1200),
    accountImport: () => wait(undefined),
    accountDisconnect: async () => {
      settings = { ...settings, account: { mode: "none" } };
    },
    lyrics: (id) => wait(id === "dmacabre001" || id === "lacrimosa06" ? lyrics : null, 400),
    openLibraryFolder: () => wait(undefined),
    updateCheck: () => wait(newer, 900),
    updateOpen: () => wait(undefined),
    libraryRescan: async () => {
      const found = [...items.values()].filter((i) => i.source === "local").length;
      emit({ type: "local", running: true, found: 0 });
      setTimeout(() => emit({ type: "local", running: false, found }), 1200);
    },
    pickFolder: () => wait(null),
    pickCookieFile: () => wait(null),
    tourMark: () => wait(false, 0),
    onEvent: (handler) => {
      listeners.add(handler);
      return () => listeners.delete(handler);
    },
    onTray: () => () => {},
    setFullscreen: () => wait(undefined, 0),
  };
}

function mockStats(plays: Play[], items: Map<string, Item>, since: number): Stats {
  const recent = plays.filter((p) => p.playedAt >= since && items.get(p.itemId)?.status === "done");
  const byItem = new Map<string, [number, number]>();
  const byArtist = new Map<string, [string, number, number]>();
  const byHour = new Map<number, number>();
  for (const p of recent) {
    const it = items.get(p.itemId)!;
    const [n, sec] = byItem.get(p.itemId) ?? [0, 0];
    byItem.set(p.itemId, [n + 1, sec + p.seconds]);
    if (it.kind === "music" && it.artist) {
      const key = it.artist.toLowerCase();
      const [name, an, as] = byArtist.get(key) ?? [it.artist, 0, 0];
      byArtist.set(key, [name, an + 1, as + p.seconds]);
    }
    const hour = new Date(p.playedAt).getHours();
    byHour.set(hour, (byHour.get(hour) ?? 0) + p.seconds);
  }
  const rank = <T extends [unknown, number, number]>(a: T, b: T) => b[1] - a[1] || b[2] - a[2];
  return {
    plays: recent.length,
    seconds: recent.reduce((s, p) => s + p.seconds, 0),
    songs: byItem.size,
    artists: byArtist.size,
    topItems: [...byItem].map(([id, [n, sec]]) => [id, n, sec] as [string, number, number]).sort(rank).slice(0, 20),
    topArtists: [...byArtist.values()].sort(rank).slice(0, 10),
    topHour: byHour.size ? [...byHour].sort((a, b) => b[1] - a[1])[0][0] : null,
  };
}
