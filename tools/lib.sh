#!/usr/bin/env bash
# Shared helpers for the Downshift emulator scripts (drive.sh, show.sh): adb broadcasts to the app, the Virtual Console
# over HTTP, screenshots of the app window, taps on on-screen text, holding a speed with the console pedal, a clean
# start, demo mode and the saved wallets. The caller sets OUT (where shot() saves) before sourcing this file.
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
PKG="com.downshift.app"
RX="$PKG/.adapters.demo.DemoReceiver"
C="http://127.0.0.1:8088"

app() { "$ADB" shell am broadcast -n "$RX" "$@" < /dev/null > /dev/null 2>&1; }
garage() { app --es cmd garage --es action "$@"; }
drive() { app --es cmd drive --es event "$@"; }
post() { curl -s -m 5 -X POST "$C$1" -o /dev/null; }
speed() { curl -s -m 3 "$C/vehicle/controls" | grep -o '"speed_kph":[0-9.]*' | cut -d: -f2 | cut -d. -f1; }
odometer() { curl -s -m 5 "$C/vehicle/state" | grep -o '"traveled_distance_km":[0-9.]*' | head -1 | cut -d: -f2; }
pid() { "$ADB" shell pidof "$PKG" < /dev/null | tr -d '\r'; }
log() { "$ADB" logcat -d -s DownshiftLab < /dev/null | tr -d '\r'; }

# Demo mode on|off, and optionally the dashed demo buttons on|off (PRD feature G).
demo_mode() { app --es cmd demo --es value "$1" ${2:+--es buttons "$2"}; }

# "real=N demo=N mode=on|off" from the app's saved garage (debug builds: run-as). A demo wallet never saved is 3000.
balances() {
  "$ADB" exec-out "run-as $PKG --user 10 cat shared_prefs/garage.xml" < /dev/null | python -I -c '
import html, json, re, sys
x = sys.stdin.read()
def wallet(name):
    m = re.search(r"<string name=\"%s\">(.*?)</string>" % name, x, re.S)
    return json.loads(html.unescape(m.group(1))) if m else None
real, demo = wallet("state"), wallet("demo_state")
mode = re.search(r"<boolean name=\"demo\" value=\"(true|false)\"", x)
print("real=%s demo=%s mode=%s" % (real["balance"] if real else 0, demo["balance"] if demo else 3000,
                                   "on" if mode and mode.group(1) == "true" else "off"))
'
}

# The app's window inside the Pleos home screen, as x1 y1 x2 y2.
window() {
  "$ADB" shell dumpsys window windows < /dev/null | tr -d '\r' | grep -o "$PKG/$PKG.MainActivity, frame=\[[0-9,]*\]\[*" | head -1 \
    | grep -o '[0-9]*,[0-9]*,[0-9]*,[0-9]*' | tr ',' ' '
}

# Screenshot cropped to the app window (or the whole screen with "full" as $2) -> $OUT/NAME.png
shot() {
  local name="$1" tmp box=""
  tmp="$(mktemp)"
  "$ADB" exec-out screencap -p > "$tmp"
  [ "${2:-}" = "full" ] || box="$(window)"
  # shellcheck disable=SC2086
  python -I - "$tmp" "$OUT/$name.png" $box <<'PY'
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

# The centre "x y" of the first node whose text or content description contains $1.
node() {
  "$ADB" shell uiautomator dump //sdcard/ui.xml < /dev/null > /dev/null 2>&1
  "$ADB" shell cat //sdcard/ui.xml < /dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"[^\"]*$1" | head -1 \
    | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '[0-9]*' | tr '\n' ' ' | awk 'NF == 4 { printf "%d %d", ($1 + $3) / 2, ($2 + $4) / 2 }'
}
tap_text() { local xy; xy="$(node "$1")"; if [ -n "$xy" ]; then "$ADB" shell input tap $xy < /dev/null; else echo "  (no '$1' on screen)"; fi; }
hold_text() { local xy; xy="$(node "$1")"; if [ -n "$xy" ]; then "$ADB" shell input swipe $xy $xy "$2" < /dev/null; else echo "  (no '$1' on screen)"; fi; }

# Holds about TARGET km/h for SECS seconds with the console pedal (and a light brake when far too fast).
hold_speed() {
  local target="$1" end=$((SECONDS + $2)) v braking=0
  while [ "$SECONDS" -lt "$end" ]; do
    v="$(speed)"; v="${v:-0}"
    if [ "$v" -gt $((target + 6)) ]; then
      post "/vehicle/controls?accelerator=0"; [ "$braking" = 0 ] && post "/vehicle/controls?brake=25"; braking=1
    else
      [ "$braking" = 1 ] && post "/vehicle/controls?brake=0"; braking=0
      if [ "$v" -lt $((target - 3)) ]; then post "/vehicle/controls?accelerator=45"
      elif [ "$v" -gt $((target + 2)) ]; then post "/vehicle/controls?accelerator=0"
      else post "/vehicle/controls?accelerator=8"; fi
    fi
    sleep 0.4
  done
  [ "$braking" = 1 ] && post "/vehicle/controls?brake=0"
}

stop_car() {
  post "/vehicle/controls?accelerator=0"; post "/vehicle/controls?brake=80"
  local i=0
  while [ "$(speed)" != "0" ] && [ $i -lt 40 ]; do sleep 0.25; i=$((i + 1)); done
  post "/vehicle/controls?brake=0"
}

# Console back to an empty car in park, no route, the engine off, the safety note agreed, demo mode on with its
# buttons, the demo garage.
start_clean() {
  for a in passenger rear_left rear_right; do post "/vehicle/seat?kind=occupied&area=$a&value=false"; done
  post "/vehicle/turn-signal?mode=Off"; post "/vehicle/controls?accelerator=0"; post "/vehicle/controls?brake=0"
  post "/vehicle/controls?speed=0"; post "/vehicle/gear?gear=Park"
  drive route --es value cancel; sleep 2 # cancelling a route brings the navigation app forward, so before ours
  "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1; sleep 3
  # garage add/reset and the point injections below need demo mode (the demo wallet, PRD feature G).
  demo_mode on on
  drive agree; drive lapse --ei value 1; drive limit --ei kph 0; drive mute --es value off
  # adb's unlock, equip, preview and tab reach the garage's view model, which exists once the garage has been shown.
  drive screen --es to garage; sleep 2
  app --es cmd stop; garage reset; sleep 1.5
}

# Mean and max %CPU of the app over N one-second samples (toybox top: PID USER PR NI VIRT RES SHR S %CPU ...).
cpu_over() {
  local secs="$1" p
  p="$(pid)"
  "$ADB" shell "top -b -d 1 -n $secs -p $p" < /dev/null | tr -d '\r' \
    | awk -v pid="$p" '$1 == pid { n++; s += $9; if ($9 > m) m = $9 } END { printf "mean %.1f, max %.1f (%d samples)", s / n, m, n }'
}
