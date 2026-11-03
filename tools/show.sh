#!/usr/bin/env bash
# Downshift M4 demo and submission checks on the Pleos emulator. Requirements: emulator booted, app installed
# (./gradlew installDebug), `bash tools/probe.sh grant`, `bash tools/sound.sh forward`. Results go to docs/m4/.
#
#   bash tools/show.sh wallet     # the demo wallet apart from the real one, demo-only injections -> docs/m4/wallet.txt
#   bash tools/show.sh latency    # web pedal message -> sound, the way sound.sh latency does it -> docs/m4/latency.txt
#   bash tools/show.sh cpu        # %CPU (one core = 100) with demo mode and the web pedal -> docs/m4/cpu.txt
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../docs/m4"
mkdir -p "$OUT"
# shellcheck source=lib.sh
source "$HERE/lib.sh"

# PASS or FAIL line: name, what was seen, what was wanted.
check() { if [ "$2" = "$3" ]; then echo "PASS $1 ($2)"; else echo "FAIL $1 (got '$2', want '$3')"; fi; }
field() { balances | grep -o "$1=[a-z0-9]*" | cut -d= -f2; }
seen() { if "$ADB" logcat -d -s DownshiftLab DownshiftDemo < /dev/null | tr -d '\r' | grep -q "$1"; then echo yes; else echo no; fi; }
more() { if [ "${1:-0}" -gt "${2:-0}" ]; then echo yes; else echo no; fi; }

wallet() {
  echo "demo wallet checks -> docs/m4/wallet.txt"
  start_clean
  demo_mode off; sleep 1
  local real0 d4 d7
  real0="$(field real)"
  "$ADB" logcat -c
  {
    echo "demo wallet checks, $(date '+%Y-%m-%d %H:%M'); the real wallet starts at $real0"
    garage add; sleep 1
    check "1 garage add with demo mode off leaves the real wallet" "$(field real)" "$real0"
    check "1 ... and is logged as ignored" "$(seen 'garage add ignored')" yes
    drive limit --ei kph 50; sleep 1
    check "2 a point injection with demo mode off is ignored" "$(seen 'drive limit ignored')" yes
    demo_mode on; sleep 1; garage reset; sleep 1
    check "3 the demo wallet after reset" "$(field demo)" 3000
    garage add; sleep 1
    check "3 the demo wallet after +1,000" "$(field demo)" 4000
    check "3 the real wallet untouched" "$(field real)" "$real0"
    drive lapse --ei value 20; app --es cmd start; sleep 2
    post "/vehicle/gear?gear=Drive"; sleep 1; hold_speed 30 10; stop_car; post "/vehicle/gear?gear=Park"; sleep 2
    d4="$(field demo)"
    check "4 a demo trip is credited to the demo wallet ($d4)" "$(more "$d4" 4000)" yes
    check "4 the real wallet untouched" "$(field real)" "$real0"
    post "/vehicle/gear?gear=Drive"; sleep 2; demo_mode off; sleep 1
    check "5 switching is refused while a trip runs" "$(seen 'demo switch refused')" yes
    check "5 still in demo mode" "$(field mode)" on
    stop_car; post "/vehicle/gear?gear=Park"; sleep 2; app --es cmd stop
    demo_mode off; sleep 1
    check "6 demo mode off" "$(field mode)" off
    check "6 the real wallet as it was" "$(field real)" "$real0"
    demo_mode on; sleep 1; drive lapse --ei value 20
    d7="$(field demo)"
    post "/vehicle/gear?gear=Drive"; sleep 1; hold_speed 30 12
    "$ADB" shell am force-stop "$PKG" < /dev/null
    post "/vehicle/controls?accelerator=0"; post "/vehicle/controls?speed=0"; post "/vehicle/gear?gear=Park"; sleep 1
    "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1; sleep 6
    check "7 a demo trip across a force stop is settled after the restart" "$(seen 'trip settled after a restart')" yes
    check "7 ... into the demo wallet ($d7 -> $(field demo))" "$(more "$(field demo)" "$d7")" yes
    check "7 the real wallet untouched" "$(field real)" "$real0"
    check "8 adb broadcasts reach the app (DUMP-guarded receiver)" "$(seen 'command demo')" yes
  } | tee "$OUT/wallet.txt"
  drive lapse --ei value 1
}

latency() {
  echo "web pedal -> sound -> docs/m4/latency.txt"
  "$ADB" forward tcp:8790 tcp:8790 > /dev/null
  start_clean
  drive screen --es to start; sleep 2; app --es cmd start; sleep 3
  local off buf
  # device clock - host clock, from the adb round trip with the shortest time; and the audio buffer
  off=$(for i in 1 2 3 4 5; do h1=$(date +%s%3N); d=$("$ADB" shell 'date +%s%3N' < /dev/null | tr -d '\r'); h2=$(date +%s%3N); echo "$((h2 - h1)) $((d - (h1 + h2) / 2))"; done | sort -n | head -1)
  buf=$("$ADB" logcat -d -s DownshiftAudio < /dev/null | tr -d '\r' | grep -o 'buffer=[0-9]*f(~[0-9]*ms)' | tail -1 | grep -o '~[0-9]*' | tr -d '~')
  echo "device clock - host clock = ${off#* } ms (adb round trip ${off%% *} ms), audio buffer ${buf:-?} ms"
  "$ADB" logcat -c
  node "$HERE/pedal_client.mjs" latency > "$OUT/latency_host.txt"
  sleep 1
  "$ADB" logcat -d -s DownshiftPedal < /dev/null | tr -d '\r' | grep -o 'web pedal pct=[0-9]* at=[0-9]*' | sed 's/web pedal pct=//; s/ at=/ /' > "$OUT/latency_web.txt"
  "$ADB" logcat -d -s DownshiftAudio < /dev/null | tr -d '\r' | grep -o 'pedal applied v=[0-9]* at=[0-9]*' | sed 's/pedal applied v=//; s/ at=/ /' > "$OUT/latency_app.txt"
  app --es cmd stop
  python -I - "$OUT/latency_host.txt" "$OUT/latency_web.txt" "$OUT/latency_app.txt" "${off#* }" "${buf:-0}" <<'PY' | tee "$OUT/latency.txt"
import sys
read = lambda p: [tuple(map(int, l.split())) for l in open(p) if l.strip()]
host, web, app = read(sys.argv[1]), read(sys.argv[2]), read(sys.argv[3])
off, buf = int(sys.argv[4]), int(sys.argv[5])
exact, rough = [], []
for v, t in host:
    # the page's message as the app took it, and the first sound with that pedal after it (both device clock)
    got = next((w for (val, w) in web if val == v and w - off >= t - 20), None)
    hit = next((a for (val, a) in app if val == v and got is not None and a >= got - 5), None)
    if got is None or hit is None:
        print(f"pedal {v:3d}%: not seen")
        continue
    exact.append(hit - got + buf)
    rough.append(hit - off - t + buf)
    print(f"pedal {v:3d}%: web message -> sound {hit - got + buf} ms (app {hit - got} + buffer {buf}) | host send -> sound {hit - off - t + buf} ms")
for name, xs in (("web message -> sound", exact), ("host send -> sound (clock offset, +-adb RTT/2)", rough)):
    if xs:
        xs.sort()
        print(f"{name}: median {xs[len(xs) // 2]} ms, max {xs[-1]} ms, n={len(xs)} (PRD target: median <= 100)")
PY
}

cpu() {
  echo "app %CPU with demo mode and the web pedal -> docs/m4/cpu.txt"
  "$ADB" forward tcp:8790 tcp:8790 > /dev/null
  start_clean
  local quiet paged waving
  sleep 5; quiet="$(cpu_over 30)"
  node "$HERE/pedal_client.mjs" idle 36 &
  sleep 3; paged="$(cpu_over 30)"; wait
  drive screen --es to start; sleep 2; app --es cmd start; sleep 3
  node "$HERE/pedal_client.mjs" wave 36 &
  sleep 3; waving="$(cpu_over 30)"; wait
  app --es cmd stop
  {
    echo "app %CPU (top, one core = 100), $(date '+%Y-%m-%d %H:%M'), demo mode on, web pedal server open"
    echo "garage, engine off, no page connected:        $quiet   (budget 5)"
    echo "garage, engine off, a page connected (pings): $paged"
    echo "start screen, engine on, pedal 0..80% at 1 Hz: $waving   (M3: start screen idling 12.0)"
  } | tee "$OUT/cpu.txt"
}

cmd="${1:-}"
case "$cmd" in
  wallet) wallet ;;
  latency) latency ;;
  cpu) cpu ;;
  *) echo "usage: show.sh wallet|latency|cpu"; exit 1 ;;
esac
