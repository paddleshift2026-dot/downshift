#!/usr/bin/env bash
# Downshift M2 garage checks on the Pleos emulator: the PT flow with screenshots, saved state across a force stop,
# CPU, memory and frame times. Requirements: emulator booted, app installed (./gradlew installDebug) and open on the
# garage, `bash tools/sound.sh forward` once. Results go to docs/m2/.
#
#   bash tools/garage.sh flow      # demo state -> V8 preview -> turbo 3 / track previews -> +1,000 P -> V12 unlock, equip
#                                  #   -> start; a screenshot per step (docs/m2/NN_*.png), the sound in docs/m2/flow.wav
#   bash tools/garage.sh persist   # force stop and reopen: balance, owned parts, build and tune must be unchanged
#   bash tools/garage.sh cpu       # %CPU of the app (top, one core = 100): engine off, previews with spins, engine idling
#   bash tools/garage.sh mem       # PSS after every engine's turntable has been shown -> docs/m2/memory.txt
#   bash tools/garage.sh frames    # gfxinfo over tab switches and engine picks -> docs/m2/frames.txt
set -u
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
PKG="com.downshift.app"
RX="$PKG/.adapters.demo.DemoReceiver"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../docs/m2"
mkdir -p "$OUT"

app() { "$ADB" shell am broadcast -n "$RX" "$@" < /dev/null >/dev/null 2>&1; }
garage() { app --es cmd garage --es action "$@"; }
pid() { "$ADB" shell pidof "$PKG" < /dev/null | tr -d '\r'; }

# The app's window inside the Pleos home screen, as x1 y1 x2 y2.
window() {
  "$ADB" shell dumpsys window windows < /dev/null | tr -d '\r' | grep -o "$PKG/$PKG.MainActivity, frame=\[[0-9,]*\]\[*" | head -1 \
    | grep -o '[0-9]*,[0-9]*,[0-9]*,[0-9]*' | tr ',' ' '
}

# Screenshot cropped to the app window -> docs/m2/NAME.png
shot() {
  local name="$1" tmp
  tmp="$(mktemp)"
  "$ADB" exec-out screencap -p > "$tmp"
  # shellcheck disable=SC2046
  python -I - "$tmp" "$OUT/$name.png" $(window) <<'PY'
import sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
box = tuple(map(int, sys.argv[3:7])) if len(sys.argv) >= 7 else None
im = Image.open(src).convert("RGB")
(im.crop(box) if box else im).save(dst, optimize=True)
PY
  rm -f "$tmp"
  echo "  $name.png"
}

# Mean and max %CPU of the app over N one-second samples (toybox top: PID USER PR NI VIRT RES SHR S %CPU ...).
cpu_over() {
  local secs="$1" p
  p="$(pid)"
  "$ADB" shell "top -b -d 1 -n $secs -p $p" < /dev/null | tr -d '\r' \
    | awk -v pid="$p" '$1 == pid { n++; s += $9; if ($9 > m) m = $9 } END { printf "mean %.1f, max %.1f (%d samples)", s / n, m, n }'
}

flow() {
  echo "PT flow -> docs/m2/"
  app --es cmd stop
  garage reset; garage tab --es tab engine; sleep 2
  app --es cmd record --es value on --es name flow
  shot 01_demo_state
  garage preview --es part v8; sleep 2.5; shot 02_preview_v8; sleep 3.5
  garage tab --es tab intake; sleep 1; garage preview --es part t3; sleep 4; shot 03_preview_turbo3; sleep 2.5
  garage tab --es tab exhaust; sleep 1; garage preview --es part track; sleep 4; shot 04_preview_track; sleep 5.5
  garage add; sleep 1; shot 05_points_added
  garage tab --es tab engine; sleep 1; garage unlock --es part v12; sleep 1.5; shot 06_v12_unlocked
  garage equip --es part v12; sleep 1.5; shot 07_v12_equipped
  app --es cmd start; sleep 3.5; shot 08_engine_started
  app --es cmd stop; sleep 2.5
  app --es cmd record --es value off --es name flow; sleep 1
  "$ADB" exec-out "run-as $PKG --user 10 cat files/flow.wav" > "$OUT/flow.wav"
  echo "  flow.wav ($(wc -c < "$OUT/flow.wav") bytes)"
}

persist() {
  local before after
  before="$("$ADB" exec-out "run-as $PKG --user 10 cat shared_prefs/garage.xml" | tr -d '\r' | grep -o '{.*}')"
  "$ADB" shell am force-stop "$PKG" < /dev/null
  "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null >/dev/null; sleep 6
  shot 09_after_restart
  after="$("$ADB" exec-out "run-as $PKG --user 10 cat shared_prefs/garage.xml" | tr -d '\r' | grep -o '{.*}')"
  {
    echo "saved before the force stop: $before"
    echo "saved after reopening:       $after"
    if [ -n "$before" ] && [ "$before" = "$after" ]; then echo "result: kept"; else echo "result: CHANGED"; fi
  } | sed 's/&quot;/"/g' | tee "$OUT/persist.txt"
}

cpu() {
  local idle busy running
  app --es cmd stop; garage tab --es tab engine; sleep 5
  idle="$(cpu_over 30)"
  # Previews with a spin to each tab's part, back to back, while top samples.
  (
    for _ in 1 2; do
      garage tab --es tab intake; sleep 0.5; garage preview --es part t3; sleep 5
      garage tab --es tab exhaust; sleep 0.5; garage preview --es part track; sleep 8
      garage tab --es tab trans; sleep 0.5; garage preview --es part mt; sleep 9
    done
  ) &
  busy="$(cpu_over 40)"
  wait
  app --es cmd start; sleep 4
  running="$(cpu_over 20)"
  app --es cmd stop
  {
    echo "garage, app %CPU (top, one core = 100), $(date '+%Y-%m-%d %H:%M')"
    echo "engine off, garage idle:            $idle   (budget 5)"
    echo "previews with spins, back to back:  $busy   (budget 20)"
    echo "engine idling in park, garage open: $running"
  } | tee "$OUT/cpu.txt"
}

mem() {
  local p
  app --es cmd stop; garage tab --es tab engine; sleep 1
  for e in i4 v8 v12; do garage preview --es part "$e"; sleep 2; garage preview --es part "$e"; sleep 1; done
  garage tab --es tab intake; sleep 3
  p="$(pid)"
  {
    echo "app memory after showing every engine's turntable, $(date '+%Y-%m-%d %H:%M')"
    "$ADB" shell dumpsys meminfo "$p" < /dev/null | tr -d '\r' | grep -E "TOTAL PSS|TOTAL RSS|Java Heap|Native Heap|Graphics|TOTAL:" | sed 's/^ *//'
    echo "(budget: PSS <= 400 MB)"
  } | tee "$OUT/memory.txt"
}

frames() {
  app --es cmd stop; garage tab --es tab engine; sleep 1
  "$ADB" shell dumpsys gfxinfo "$PKG" reset < /dev/null >/dev/null
  for t in intake exhaust trans tune engine intake engine; do garage tab --es tab "$t"; sleep 1.5; done
  for e in v8 v12 i4; do garage preview --es part "$e"; sleep 1.5; garage preview --es part "$e"; sleep 1; done
  {
    echo "garage frames over 7 tab switches (with spins) and 3 engine picks, $(date '+%Y-%m-%d %H:%M')"
    "$ADB" shell dumpsys gfxinfo "$PKG" < /dev/null | tr -d '\r' | grep -E "Total frames rendered|Janky frames|percentile|Number Slow|Number Frame deadline" | head -12 | sed 's/^ *//'
  } | tee "$OUT/frames.txt"
}

cmd="${1:-}"
case "$cmd" in
  flow) flow ;;
  persist) persist ;;
  cpu) cpu ;;
  mem) mem ;;
  frames) frames ;;
  *) echo "usage: garage.sh flow|persist|cpu|mem|frames"; exit 1 ;;
esac
