import { useLibrary } from "@/state/library";
import { playItem, usePlayer } from "@/state/player";
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
 * The CI screenshot tour (REQUIEM_TOUR): the real app goes through its first run, a search, a
 * download, a song with lyrics, a video, the library and the settings, printing a mark at each
 * screen for the workflow to capture. Every wait has a limit, so a YouTube that refuses the
 * runner still ends the tour, with pictures of what the person would see.
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
  await until(() => !!state().tools.status?.ready && !state().tools.installing, 6 * 60_000);

  go({ name: "search", query: "Saint-Saëns Danse Macabre" });
  await until(() => !!document.querySelector("[data-search-state=done], [data-search-state=error]"), 120_000);
  await mark("02-search");

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

  await pause(3000);
  go({ name: "downloads" });
  await mark("03-downloads");

  await until(() => settled(songId), 5 * 60_000);
  if (done(songId)) {
    playItem(songId);
    usePlayer.setState({ panel: "lyrics" });
    await pause(8000);
    await mark("04-playing");
  }

  await until(() => settled(videoId), 5 * 60_000);
  if (done(videoId)) {
    playItem(videoId);
    usePlayer.setState({ panel: "queue" });
    await pause(6000);
    await mark("05-video");
  }

  go({ name: "home" });
  await mark("06-home");
  go({ name: "library" });
  await mark("07-library");
  go({ name: "downloads" });
  await mark("08-downloads-after");
  go({ name: "settings" });
  await mark("09-settings");
  await api.tourMark("done");
}
