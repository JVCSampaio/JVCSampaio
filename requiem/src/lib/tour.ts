import { libraryItems, useLibrary } from "@/state/library";
import { playItem, playList, seekFromEnd, soundState, usePlayer } from "@/state/player";
import { go } from "@/state/router";
import type { Backend } from "./backend";

const pause = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

async function until(check: () => boolean, ms: number): Promise<boolean> {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (check()) return true;
    await pause(500);
  }
  return check();
}

/**
 * The CI screenshot tour (REQUIEM_TOUR): the real app goes through its first run, the songs
 * already on the computer (the workflow leaves some in REQUIEM_MUSIC_DIR), one of them playing,
 * a search, a download, a video, the library and the settings, printing a mark at each screen
 * for the workflow to capture. Every wait has a limit, so a YouTube that refuses the runner
 * still ends the tour, with pictures of what the person would see.
 */
export async function runTour(api: Backend) {
  const state = useLibrary.getState;
  const mark = async (name: string) => {
    await pause(1500);
    await api.tourMark(name);
    await pause(2500);
  };
  const settled = (id: string | null) => {
    if (!id) return true;
    const status = state().items[id]?.status;
    return !status || status === "done" || status === "failed" || status === "skipped";
  };
  const done = (id: string | null): id is string => !!id && state().items[id]?.status === "done";

  go({ name: "home" });
  await mark("01-first-run");

  await until(() => !state().local.running && libraryItems(state().items).some((i) => i.source === "local"), 60_000);
  go({ name: "library" });
  await mark("02-library");
  const onDisk = libraryItems(state().items).filter((i) => i.source === "local" && i.kind === "music");
  if (onDisk.length) {
    playList(
      onDisk.map((i) => i.id),
      0,
      { shuffle: false },
    );
    usePlayer.setState({ panel: "lyrics" });
    await pause(5000);
    await mark("03-playing-from-disk");
    await soundReport(api, onDisk.length > 1);
  }

  await until(() => !!state().tools.status?.ready && !state().tools.installing, 6 * 60_000);
  go({ name: "search", query: "Saint-Saëns Danse Macabre" });
  await until(() => !!document.querySelector("[data-search-state=done], [data-search-state=error]"), 120_000);
  await mark("04-search");

  const button = document.querySelector<HTMLButtonElement>("[data-result] [data-action=download]");
  const songId = button?.closest<HTMLElement>("[data-result]")?.dataset.result ?? null;
  button?.click();

  let videoId: string | null = null;
  try {
    const videos = await api.search("Danse Macabre orchestra", "video");
    const pick = videos.find((v) => !v.isShort && (v.duration ?? 0) > 60 && (v.duration ?? 0) < 600);
    if (pick) {
      await api.download(pick, "video", "search");
      videoId = pick.id;
    }
  } catch (e) {
    console.warn("tour: video search failed", e);
  }

  // A Spotify playlist pasted in the search: the list as the real page gives it.
  go({ name: "search", query: "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M" });
  await until(() => !!document.querySelector("[data-action=import], [data-search-state=error]"), 60_000);
  await mark("05-spotify-list");
  const listed = document.querySelectorAll("[data-search-state] ol li").length;
  await api.tourLog(`spotify: ${listed} songs listed from the embed page`);

  await pause(3000);
  go({ name: "downloads" });
  await mark("05-downloads");

  await until(() => settled(songId), 5 * 60_000);
  if (done(songId)) {
    playItem(songId);
    usePlayer.setState({ panel: "lyrics" });
    await pause(8000);
    await mark("06-playing-download");
  }

  await until(() => settled(videoId), 5 * 60_000);
  if (done(videoId)) {
    playItem(videoId);
    usePlayer.setState({ panel: "queue" });
    await pause(6000);
    await mark("07-video");
  }

  // The engine measured the songs on disk once the tools were in: the workflow compares these
  // with its own FFmpeg.
  await until(() => libraryItems(state().items).filter((i) => i.source === "local").every((i) => i.loudness != null), 60_000);
  for (const item of libraryItems(state().items).filter((i) => i.source === "local")) {
    await api.tourLog(`loudness: ${item.title} = ${item.loudness?.toFixed(1) ?? "none"} LUFS, peak ${item.peak?.toFixed(1) ?? "none"} dBFS`);
  }

  go({ name: "home" });
  await mark("08-home");
  go({ name: "library" });
  await mark("09-library-after");
  go({ name: "downloads" });
  await mark("10-downloads-after");
  go({ name: "settings", section: "folders" });
  await mark("11-settings-folders");
  go({ name: "settings" });
  await mark("12-settings");
  await api.tourMark("done");
}

/** The sound path in the real web view: the chain's state and level while a song plays, the
 *  system's media controls (the workflow reads MPRIS when it sees the mark), and a crossfade
 *  between two songs measured as the time both sound together. */
async function soundReport(api: Backend, crossfade: boolean) {
  const now = soundState();
  await api.tourLog(
    `sound: mode=${now.mode} chain=${now.chain ?? "none"} level=${now.level?.toFixed(4) ?? "none"} active=${now.active}${now.reason ? ` reason=${now.reason}` : ""}`,
  );
  await api.tourMark("mpris");
  if (!crossfade) return;
  const save = useLibrary.getState().saveSettings;
  const before = useLibrary.getState().settings;
  if (!before) return;
  await save({ ...before, audio: { ...before.audio, crossfadeSeconds: 4 } });
  seekFromEnd(7);
  let both = 0;
  for (let i = 0; i < 32; i++) {
    await pause(250);
    const playing = soundState().decks.filter((d) => d.name !== "video" && !d.paused).length;
    if (playing === 2) both += 0.25;
  }
  const after = soundState();
  await api.tourLog(`crossfade: both songs sounded for ${both.toFixed(2)} s of a 4 s fade, mode=${after.mode}, now on deck ${after.active}`);
  await save({ ...useLibrary.getState().settings!, audio: { ...before.audio } });
}
