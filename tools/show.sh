#!/usr/bin/env bash
# Downshift M4 demo and submission checks on the Pleos emulator. Requirements: emulator booted, app installed
# (./gradlew installDebug), `bash tools/probe.sh grant`, `bash tools/sound.sh forward`. Results go to docs/m4/.
#
#   bash tools/show.sh wallet     # the demo wallet apart from the real one, demo-only injections -> docs/m4/wallet.txt
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

cmd="${1:-}"
case "$cmd" in
  wallet) wallet ;;
  *) echo "usage: show.sh wallet"; exit 1 ;;
esac
