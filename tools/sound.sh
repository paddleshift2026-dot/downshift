#!/usr/bin/env bash
# Downshift M1 sound checks on the Pleos emulator: scripted drives recorded in the app, pedal-to-sound latency and CPU.
# Requirements: emulator booted, app installed (./gradlew installDebug) and open on the sound lab tab,
# `bash tools/sound.sh forward` once. The app keeps the console bridge alive itself.
#
#   bash tools/sound.sh forward
#   bash tools/sound.sh build engine=V8 intake=TURBO2 exhaust=SPORT trans=DCT pops=0.8 redline=0 volume=0.8
#   bash tools/sound.sh scenario NAME   # start, idle, rev to the limiter in P, pull in D, lift, brake, park, stop
#                                       #   recorded in the app -> docs/m1/renders/NAME.wav
#   bash tools/sound.sh cpu [SECONDS]   # %CPU of the app (top, one core = 100) while a scenario runs -> docs/m1/cpu.txt
#   bash tools/sound.sh latency         # console pedal POST -> "pedal applied" in the app + audio buffer -> docs/m1/latency.txt
#   bash tools/sound.sh log
set -u
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
C="http://127.0.0.1:8088"
PKG="com.downshift.app"
RX="$PKG/.adapters.demo.DemoReceiver"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../docs/m1"
mkdir -p "$OUT/renders"

post() { curl -s -m 10 -X POST "$C$1" -o /dev/null -w "POST $1 -> %{http_code}\n"; }
app() { "$ADB" shell am broadcast -n "$RX" "$@" >/dev/null 2>&1 && echo "app: $*"; }

# key=value pairs -> DemoReceiver extras (numbers as float or int, the rest as strings)
build_args() {
  local args=(--es cmd build) kv k v
  for kv in "$@"; do
    k="${kv%%=*}"; v="${kv#*=}"
    case "$k" in
      pops|volume) args+=(--ef "$k" "$v") ;;
      redline) args+=(--ei "$k" "$v") ;;
      *) args+=(--es "$k" "$v") ;;
    esac
  done
  printf '%s\n' "${args[@]}"
}

scenario() {
  local name="$1"
  post "/vehicle/controls?accelerator=0" >/dev/null; post "/vehicle/controls?brake=0" >/dev/null
  post "/vehicle/controls?speed=0" >/dev/null; post "/vehicle/gear?gear=Park" >/dev/null
  app --es cmd record --es value on --es name "$name"
  app --es cmd start; sleep 3
  post "/vehicle/controls?accelerator=100"; sleep 1.5
  post "/vehicle/controls?accelerator=0"; sleep 1.5
  post "/vehicle/gear?gear=Drive"; sleep 0.5
  post "/vehicle/controls?accelerator=70"; sleep 8
  post "/vehicle/controls?accelerator=0"; sleep 3
  post "/vehicle/controls?brake=60"; sleep 5
  post "/vehicle/controls?brake=0"; post "/vehicle/gear?gear=Park"; sleep 1
  app --es cmd stop; sleep 2
  app --es cmd record --es value off --es name "$name"; sleep 1
  post "/vehicle/controls?speed=0" >/dev/null
  "$ADB" exec-out "run-as $PKG --user 10 cat files/$name.wav" > "$OUT/renders/$name.wav"
  echo "$name -> docs/m1/renders/$name.wav ($(wc -c < "$OUT/renders/$name.wav") bytes)"
}

cmd="${1:-}"
case "$cmd" in
  forward) "$ADB" forward tcp:8088 tcp:8088; "$ADB" forward --list ;;
  build)
    shift
    mapfile -t extras < <(build_args "$@")
    app "${extras[@]}"
    ;;
  scenario) scenario "${2:?usage: sound.sh scenario NAME}" ;;
  cpu)
    secs="${2:-30}"
    pid=$("$ADB" shell pidof "$PKG" | tr -d '\r')
    [ -n "$pid" ] || { echo "app not running"; exit 1; }
    "$ADB" shell "top -b -d 1 -n $secs -p $pid" | tr -d '\r' > "$OUT/cpu_top.txt" &
    top_job=$!
    scenario cpu_run
    wait "$top_job"
    # toybox top: PID USER PR NI VIRT RES SHR S %CPU %MEM TIME+ ARGS
    awk -v pid="$pid" '$1 == pid { n++; s += $9; if ($9 > m) m = $9 } END { printf "app %%CPU over %d samples (1 core = 100): mean %.1f, max %.1f\n", n, s / n, m }' \
      "$OUT/cpu_top.txt" | tee "$OUT/cpu.txt"
    ;;
  latency)
    # console accelerator POST (host clock) -> "pedal applied" log (device clock, offset measured below) + audio buffer
    off=$(for i in 1 2 3 4 5; do h1=$(date +%s%3N); d=$("$ADB" shell 'date +%s%3N' | tr -d '\r'); h2=$(date +%s%3N); echo "$((h2 - h1)) $((d - (h1 + h2) / 2))"; done | sort -n | head -1)
    buf=$("$ADB" logcat -d -s DownshiftAudio 2>/dev/null | tr -d '\r' | grep -o 'buffer=[0-9]*f(~[0-9]*ms)' | tail -1 | grep -o '~[0-9]*' | tr -d '~')
    echo "device clock - host clock = ${off#* } ms (adb round trip ${off%% *} ms), audio buffer ${buf:-?} ms"
    "$ADB" logcat -c
    : > "$OUT/latency_host.txt"
    # Each line: value, host send time, and the console's own timestamp of the write (device clock, from its reply).
    # 2.5 s apart, so every step meets the app's slower steady-pedal polling (the worst case).
    for v in 30 80 0 60 0 100 20 0; do
      t=$(date +%s%3N)
      ts=$(curl -s -m 10 -X POST "$C/vehicle/controls?accelerator=$v" | grep -o '"controls":{[^}]*' | grep -o '"timestamp_ms":[0-9]*' | grep -o '[0-9]*$')
      echo "$v $t ${ts:-0}" >> "$OUT/latency_host.txt"
      sleep 2.5
    done
    "$ADB" logcat -d -s DownshiftAudio 2>/dev/null | tr -d '\r' | grep -o 'pedal applied v=[0-9]* at=[0-9]*' | sed 's/pedal applied v=//; s/ at=/ /' > "$OUT/latency_app.txt"
    python -I - "$OUT/latency_host.txt" "$OUT/latency_app.txt" "${off#* }" "${buf:-0}" <<'PY' | tee "$OUT/latency.txt"
import sys
host = [tuple(map(int, l.split())) for l in open(sys.argv[1]) if l.strip()]
app = [tuple(map(int, l.split())) for l in open(sys.argv[2]) if l.strip()]
off, buf = int(sys.argv[3]), int(sys.argv[4])
exact, rough = [], []
for v, t, ts in host:
    # console write -> sound: both clocks are the device's, so no offset is needed
    hit = next((a for (val, a) in app if val == v and ts and a >= ts - 5), None)
    host_hit = next((a - off for (val, a) in app if val == v and a - off >= t - 20), None)
    if hit is None:
        print(f"pedal {v:3d}%: not seen")
        continue
    exact.append(hit - ts + buf)
    if host_hit is not None:
        rough.append(host_hit - t + buf)
    print(f"pedal {v:3d}%: console write -> sound {hit - ts + buf} ms (app write {hit - ts} + buffer {buf})"
          + (f" | host POST -> sound {host_hit - t + buf} ms" if host_hit is not None else ""))
for name, xs in (("console write -> sound", exact), ("host POST -> sound (clock offset, +-adb RTT/2)", rough)):
    if xs:
        xs.sort()
        print(f"{name}: median {xs[len(xs) // 2]} ms, max {xs[-1]} ms, n={len(xs)} (target <= 120)")
PY
    ;;
  log) "$ADB" logcat -d -s DownshiftLab DownshiftAudio DownshiftConsole DownshiftCar DownshiftPack DownshiftDemo 2>/dev/null | tail -60 ;;
  *) echo "usage: sound.sh forward|build k=v ...|scenario NAME|cpu [SECONDS]|latency|log"; exit 1 ;;
esac
