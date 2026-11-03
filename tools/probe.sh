#!/usr/bin/env bash
# Downshift M0 probe: what the Pleos emulator really delivers to a third-party app.
# Requirements: emulator booted, app installed (./gradlew installDebug), `bash tools/probe.sh forward` once,
# the Virtual Console page open in a browser OR `bash tools/probe.sh keepalive &` running.
#
#   bash tools/probe.sh forward | keepalive
#   bash tools/probe.sh grant          # runtime car permissions for user 10, then restart the app
#   bash tools/probe.sh console        # full Virtual Console state -> docs/m0/console_state.json (pedal/rpm/seat keys?)
#   bash tools/probe.sh props          # every AAOS property config (shell view) -> docs/m0/property_config.txt
#   bash tools/probe.sh sweep          # gear P->D, speed 0->30->60->30->0, brake, turn signals through the console
#   bash tools/probe.sh pedal [V]      # inject ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE=V (id from the app log)
#   bash tools/probe.sh route          # NaviHelper route + 60 s at 60 km/h for speed-limit / camera / TBT events
#   bash tools/probe.sh audio          # throttle steps and a guidance tone under each focus mode
#   bash tools/probe.sh loop NAME      # play debug asset loops/NAME (listening test)
#   bash tools/probe.sh throttle-sweep # virtual pedal 0 -> 100% -> 0 over about 10 s
#   bash tools/probe.sh report         # app report -> docs/m0/m0_report.md
#   bash tools/probe.sh all            # grant, console, props, sweep, pedal, route, audio, report
#   bash tools/probe.sh show | log
set -u
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
C="http://127.0.0.1:8088"
PKG="com.downshift.app"
RX="$PKG/.adapters.demo.DemoReceiver"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../docs/m0"
mkdir -p "$OUT"

post() { curl -s -m 10 -X POST "$C$1" -o /dev/null -w "POST $1 -> %{http_code}\n"; }
app() { "$ADB" shell am broadcast -n "$RX" "$@" >/dev/null 2>&1 && echo "app: $*"; }
show() { "$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1; sleep 2; }

cmd="${1:-}"
case "$cmd" in
  forward) "$ADB" forward tcp:8088 tcp:8088; "$ADB" forward --list ;;
  keepalive)
    echo "keeping the Virtual Console bridge alive (Ctrl+C to stop)"
    while true; do curl -s -m 5 -X POST "$C/browser/session?client=downshift-cli" -o /dev/null; sleep 2; done
    ;;
  grant)
    for p in CAR_SPEED CAR_ENERGY CAR_POWERTRAIN CAR_ENGINE_DETAILED CAR_MILEAGE CAR_INFO CAR_EXTERIOR_LIGHTS CONTROL_CAR_SEATS; do
      if "$ADB" shell pm grant --user 10 "$PKG" "android.car.permission.$p" >/dev/null 2>&1 \
        || "$ADB" shell pm grant "$PKG" "android.car.permission.$p" >/dev/null 2>&1; then echo "granted $p"; else echo "not grantable: $p"; fi
    done
    # DriveService's notification (M4)
    if "$ADB" shell pm grant --user 10 "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1; then echo "granted POST_NOTIFICATIONS"; else echo "not grantable: POST_NOTIFICATIONS"; fi
    "$ADB" shell am force-stop "$PKG"; show; echo "app restarted"
    ;;
  console)
    curl -s -m 10 "$C/vehicle/state" > "$OUT/console_state.json"
    echo "console state -> docs/m0/console_state.json ($(wc -c < "$OUT/console_state.json") bytes)"
    grep -o -i -E '"[a-z_]*(accel|throttle|pedal|rpm|seat|occup|odometer|mileage)[a-z_]*"' "$OUT/console_state.json" | sort -u \
      || echo "no pedal/rpm/seat/odometer keys in the console state"
    ;;
  props)
    "$ADB" shell cmd car_service get-property-config > "$OUT/property_config.txt" 2>&1
    echo "property configs -> docs/m0/property_config.txt ($(wc -l < "$OUT/property_config.txt") lines)"
    grep -i -E 'PEDAL|ENGINE_RPM|PERF_VEHICLE_SPEED|GEAR_SELECTION|CURRENT_GEAR|SEAT_OCCUPANCY|INFO_FUEL_TYPE|PERF_ODOMETER|TURN_SIGNAL|IGNITION|INSTANTANEOUS' \
      "$OUT/property_config.txt" | head -40 || true
    ;;
  sweep)
    post "/vehicle/gear?gear=Park"; sleep 1
    post "/vehicle/gear?gear=Drive"; sleep 1
    for k in 0 30 60 30 0; do post "/vehicle/controls?speed=$k"; sleep 2; done
    post "/vehicle/controls?brake=70"; sleep 1; post "/vehicle/controls?brake=0"; sleep 1
    post "/vehicle/turn-signal?mode=BlinkLeft"; sleep 2
    post "/vehicle/turn-signal?mode=BlinkRight"; sleep 2
    post "/vehicle/turn-signal?mode=Off"
    post "/vehicle/gear?gear=Park"
    ;;
  pedal)
    id=$("$ADB" logcat -d -s DownshiftCar 2>/dev/null | tr -d '\r' | grep -o 'ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE id=0x[0-9a-fA-F]*' | tail -1 | sed 's/.*id=//')
    if [ -z "$id" ]; then echo "no accelerator pedal id in the app log (constant missing in this SDK)"; exit 0; fi
    v="${2:-50}"
    if "$ADB" shell cmd car_service inject-vhal-event "$id" "$v" || "$ADB" shell cmd car_service inject-vhal-event "$id" 0 "$v"; then
      echo "injected pedal $id=$v"
    else
      echo "inject-vhal-event refused for $id"
    fi
    ;;
  route)
    app --es cmd route; sleep 5
    post "/vehicle/gear?gear=Drive"; post "/vehicle/controls?speed=60"
    echo "driving at 60 km/h for 60 s"; sleep 60
    post "/vehicle/controls?speed=0"; post "/vehicle/gear?gear=Park"
    show
    ;;
  audio)
    for m in none game media sonification; do
      app --es cmd focus --es mode "$m"; sleep 1
      for t in 0 0.5 1 0.5 0; do app --es cmd throttle --ef value "$t"; sleep 1; done
      app --es cmd guide; sleep 3
    done
    app --es cmd focus --es mode none
    ;;
  loop) app --es cmd loop --es name "${2:?usage: probe.sh loop FILE.wav}" ;;
  throttle-sweep) for t in 0 0.2 0.4 0.6 0.8 1 0.8 0.6 0.4 0.2 0; do app --es cmd throttle --ef value "$t"; sleep 1; done ;;
  accel) post "/vehicle/controls?accelerator=${2:-50}" ;;
  extras)
    # passenger in and out, odometer forward 1.5 km (Pleos Seat / Odometer listeners)
    post "/vehicle/seat?kind=occupied&area=passenger&value=true"; sleep 2
    post "/vehicle/seat?kind=occupied&area=passenger&value=false"; sleep 1
    km=$(curl -s -m 5 "$C/vehicle/state" | python -I -c "import sys,json; print(json.load(sys.stdin)['odometer']['traveled_distance_km'])")
    post "/vehicle/odometer?traveled_distance_km=$(python -I -c "print(round($km + 1.5, 1))")"; sleep 2
    ;;
  accel-latency)
    # console accelerator POST (host clock) -> first app log of that value (emulator clock, offset measured below)
    off=$(for i in 1 2 3 4 5; do h1=$(date +%s%3N); d=$("$ADB" shell 'date +%s%3N' | tr -d '\r'); h2=$(date +%s%3N); echo "$((h2 - h1)) $((d - (h1 + h2) / 2))"; done | sort -n | head -1)
    echo "device clock - host clock = ${off#* } ms (adb round trip ${off%% *} ms)"
    "$ADB" logcat -c
    : > "$OUT/accel_host.txt"
    for v in 30 80 0 60 0; do echo "$v $(date +%s%3N)" >> "$OUT/accel_host.txt"; post "/vehicle/controls?accelerator=$v" >/dev/null; sleep 1; done
    "$ADB" logcat -d -s DownshiftConsole 2>/dev/null | tr -d '\r' | grep -o 'accel=[0-9]* at=[0-9]*' | sed 's/accel=//; s/ at=/ /' > "$OUT/accel_app.txt"
    python -I - "$OUT/accel_host.txt" "$OUT/accel_app.txt" "${off#* }" <<'PY'
import sys
host = [tuple(map(int, l.split())) for l in open(sys.argv[1]) if l.strip()]
app = [tuple(map(int, l.split())) for l in open(sys.argv[2]) if l.strip()]
off = int(sys.argv[3])
for v, t in host:
    hit = next((a - off for (val, a) in app if val == v and a - off >= t - 20), None)
    print(f"pedal {v:3d}%: console POST -> app {'%d ms' % (hit - t) if hit else 'not seen'}")
PY
    ;;
  report)
    app --es cmd report; sleep 2
    "$ADB" logcat -d -v raw -s DownshiftReport 2>/dev/null | tr -d '\r' \
      | awk '/^=== REPORT BEGIN/{buf=""; on=1; next} /^=== REPORT END/{on=0; last=buf; next} on{buf=buf $0 "\n"} END{printf "%s", last}' \
      > "$OUT/m0_report.md"
    echo "report -> docs/m0/m0_report.md ($(wc -l < "$OUT/m0_report.md") lines)"
    ;;
  all) for s in grant console props sweep pedal route audio report; do echo "== $s =="; bash "$0" "$s"; done ;;
  show) show ;;
  log) "$ADB" logcat -d -s DownshiftCar DownshiftPleos DownshiftAudio DownshiftDemo 2>/dev/null | tail -80 ;;
  *) echo "usage: probe.sh forward|keepalive|grant|console|props|sweep|pedal [V]|route|audio|loop NAME|throttle-sweep|report|all|show|log"; exit 1 ;;
esac
