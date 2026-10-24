#!/usr/bin/env bash
# Installs the release APK on a running emulator, walks through the main screens, shares a
# Creative Commons test video to the app and waits for yt-dlp to download it.
# Usage: smoke-test.sh <apk> <output dir>
set -uo pipefail

APK="$1"
OUT="$2"
PKG="io.github.jvcsampaio.sonar"
# Long-lived public videos used by many download tools as test fixtures, tried in order.
VIDEOS="dQw4w9WgXcQ jNQXAC9IVRw"
mkdir -p "$OUT"

shot() {
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1
  echo "== $1: $(python3 - "$OUT/$1.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read() if len(sys.argv) > 1 else ""
texts = [t for t in re.findall(r' text="([^"]+)"', xml) if t.strip()]
print(" | ".join(texts)[:600])
PY
)"
}

# Taps the first on-screen element whose text or content description matches.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/tap.xml" > /dev/null 2>&1
  local point
  point=$(python3 - "$OUT/tap.xml" "$1" <<'PY'
import re, sys
xml, label = open(sys.argv[1], encoding="utf-8").read(), sys.argv[2]
for node in re.findall(r"<node [^>]*>", xml):
    text = re.search(r' text="([^"]*)"', node).group(1)
    desc = re.search(r' content-desc="([^"]*)"', node).group(1)
    if label in (text, desc):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$point" ]; then echo "!! '$1' not on screen"; return 1; fi
  adb shell input tap $point
}

adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
shot 01-home

tap "SINCRONIZAR" && shot 02-sync
tap "Ajustes" && shot 03-settings
adb shell input keyevent KEYCODE_BACK
tap "BIBLIOTECA" && shot 04-library-empty

result="none"
for id in $VIDEOS; do
  echo "== sharing https://youtu.be/$id"
  adb shell am start -a android.intent.action.SEND -t text/plain \
    --es android.intent.extra.TEXT "https://youtu.be/$id" -n "$PKG/.MainActivity"
  shot "05-queued-$id"
  result="timeout"
  for _ in $(seq 1 60); do
    log=$(adb logcat -d -s DownloadWorker:* 2>/dev/null)
    if echo "$log" | grep -q "Downloaded $id"; then result="downloaded"; break; fi
    if echo "$log" | grep -q "Download failed for $id"; then result="failed"; break; fi
    sleep 5
  done
  echo "== $id: $result"
  [ "$result" = "downloaded" ] && break
done
adb logcat -d -s DownloadWorker:* | tail -20
shot 06-sync-after

tap "INÍCIO" && shot 07-home
if [ "$result" = "downloaded" ]; then
  tap "TOCAR" && shot 08-playing
  adb shell am start -n "$PKG/.MainActivity" --ez open_player true
  shot 09-player
  adb shell dumpsys media_session | grep -E "state=PlaybackState|description=" | head -4
fi

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "!! app crashed"
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
  exit 1
fi
echo "$result" > "$OUT/download-result.txt"
echo "== no crashes"
