#!/usr/bin/env bash
# Downshift M3 drive checks on the Pleos emulator: the PT flow with screenshots and sound, a scripted trip whose
# points are known, trips across a force stop, CPU while driving, and music alongside the engine. Requirements:
# emulator booted, app installed (./gradlew installDebug), `bash tools/probe.sh grant`, `bash tools/sound.sh forward`.
# Results go to docs/m3/. The console has no physics: the app drives the demo car from the console pedal, and this
# script holds a speed by pressing that pedal.
#
#   bash tools/drive.sh flow      # PT steps 3-6: start -> D -> passenger, a signalled turn, school zone, guidance, mute
#                                 #   -> arrive -> summary -> V12 unlocked and started; docs/m3/NN_*.png, docs/m3/flow.wav
#   bash tools/drive.sh trip      # turns, a camera and a school zone at known speeds; checks "trip done" -> trip.txt
#   bash tools/drive.sh persist   # force stop mid-trip (carries on) and parked with a checkpoint (settled once) -> persist.txt
#   bash tools/drive.sh cpu       # %CPU (one core = 100) driving with and without the Pleos SDK, the idle garage -> cpu.txt
#   bash tools/drive.sh music     # a media player with focus beside the idling engine -> music.txt
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../docs/m3"
mkdir -p "$OUT"
# shellcheck source=lib.sh
source "$HERE/lib.sh"

flow() {
  echo "PT flow -> docs/m3/"
  start_clean
  "$ADB" logcat -c
  app --es cmd record --es value on --es name m3flow
  drive screen --es to start; sleep 2; shot 02_start_off
  hold_text "시동 걸기" 1300; sleep 2.5; shot 03_start_idle
  post "/vehicle/controls?accelerator=70"; sleep 1.2; shot 03b_start_rev
  post "/vehicle/controls?accelerator=0"; sleep 1.5
  tap_text "시연용 · 기어 D"; sleep 2.5; shot 04_drive_d
  post "/vehicle/seat?kind=occupied&area=passenger&value=true"; sleep 2.5; shot 07_passenger
  post "/vehicle/seat?kind=occupied&area=passenger&value=false"; sleep 1.5
  drive limit --ei kph 50
  drive turn --es dir left --ei m 80                       # signalled the whole way: scores
  post "/vehicle/turn-signal?mode=BlinkLeft"; hold_speed 40 12; post "/vehicle/turn-signal?mode=Off"
  hold_speed 25 5
  drive school --ei m 40
  hold_speed 25 8; stop_car; sleep 2; shot 05_school_zone
  drive guide; sleep 0.7; shot 06_guidance; sleep 2.5
  tap_text "음소거"; sleep 1.2; shot 08_muted; tap_text "소리 켜기"; sleep 1
  drive lapse --ei value 20
  hold_speed 25 4
  drive camera --ei kph 50 --ei m 1500
  hold_speed 45 12; stop_car; sleep 1
  tap_text "시연용 · 기어 P · 도착"; sleep 3; shot 09_summary
  drive lapse --ei value 1
  tap_text "개러지에서 해금하기"; sleep 2
  garage unlock --es part v12; garage equip --es part v12; app --es cmd stop; sleep 3
  drive screen --es to start; sleep 2
  hold_text "시동 걸기" 1300; sleep 3; shot 10_v12_start
  app --es cmd stop; sleep 2
  app --es cmd record --es value off --es name m3flow; sleep 1
  "$ADB" exec-out "run-as $PKG --user 10 cat files/m3flow.wav" > "$OUT/flow.wav"
  echo "  flow.wav ($(wc -c < "$OUT/flow.wav") bytes, the last 60 s)"
  log | grep -E "trip (start|done)|situation" | sed -E 's/^[0-9-]+ [0-9:.]+ +[0-9]+ +[0-9]+ I //'
}

trip() {
  echo "scripted trip -> docs/m3/trip.txt"
  start_clean
  "$ADB" logcat -c
  local odo0 odo1
  drive limit --ei kph 50
  app --es cmd start; sleep 2
  odo0="$(odometer)"
  post "/vehicle/gear?gear=Drive"; sleep 1
  hold_speed 40 6                                                    # up to speed
  drive turn --es dir left --ei m 90                                 # signalled from about 60 m: scores
  hold_speed 40 3; post "/vehicle/turn-signal?mode=BlinkLeft"; hold_speed 40 7; post "/vehicle/turn-signal?mode=Off"
  drive turn --es dir right --ei m 60                                # no signal: counted, does not score
  hold_speed 40 7
  drive camera --ei kph 50 --ei m 120                                # at 40 under 50: kept
  hold_speed 40 13
  hold_speed 25 6
  drive school --ei m 30                                             # at 25 for 300 m: kept
  hold_speed 25 50
  stop_car; post "/vehicle/gear?gear=Park"; sleep 2
  odo1="$(odometer)"
  app --es cmd stop
  local done_line
  done_line="$(log | grep "trip done" | tail -1 | sed -E 's/.*trip done //')"
  {
    echo "scripted trip, $(date '+%Y-%m-%d %H:%M'): limit 50; 40 km/h with a signalled left turn, an unsignalled right turn,"
    echo "a 50 km/h camera at 40; a school zone at 25. Expected turns=1/2, places=2/2, unknown=0.00, all distance kept."
    echo "trip done: $done_line"
    echo "console odometer: $odo0 -> $odo1"
    case "$done_line" in
      *"places=2/2 turns=1/2"*"unknown=0.00"* | *"unknown=0.00"*"places=2/2 turns=1/2"*) echo "result: as expected" ;;
      *) echo "result: DIFFERENT, see the line above" ;;
    esac
  } | tee "$OUT/trip.txt"
}

persist() {
  echo "trips across a force stop -> docs/m3/persist.txt"
  local resumed done1 settled before after again
  {
    echo "trips across a force stop, $(date '+%Y-%m-%d %H:%M')"
    echo "1) force stop while driving in D: the trip carries on from its checkpoint"
    start_clean; "$ADB" logcat -c
    app --es cmd start; post "/vehicle/gear?gear=Drive"; sleep 1
    hold_speed 40 14
    "$ADB" shell am force-stop "$PKG" < /dev/null; sleep 1
    "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1; sleep 5
    hold_speed 40 8; stop_car; post "/vehicle/gear?gear=Park"; sleep 2
    resumed="$(log | grep "trip resumed" | tail -1 | sed -E 's/.*trip resumed //')"
    done1="$(log | grep "trip done" | tail -1 | sed -E 's/.*trip done //')"
    echo "   resumed: ${resumed:-none}"
    echo "   done:    ${done1:-none}"
    echo "2) force stop in D with a checkpoint, park while the app is gone, reopen: settled once"
    start_clean; "$ADB" logcat -c
    before="$("$ADB" exec-out "run-as $PKG --user 10 cat shared_prefs/garage.xml" | tr -d '\r' | grep -o '&quot;balance&quot;:[0-9]*' | head -1)"
    post "/vehicle/gear?gear=Drive"; sleep 1
    hold_speed 30 13; stop_car; sleep 1
    "$ADB" shell am force-stop "$PKG" < /dev/null; post "/vehicle/gear?gear=Park"; sleep 1
    "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1; sleep 6
    settled="$(log | grep -E "trip settled|trip done" | tr '\n' ' ' | sed -E 's/[0-9-]+ [0-9:.]+ +[0-9]+ +[0-9]+ I DownshiftLab: //g')"
    after="$("$ADB" exec-out "run-as $PKG --user 10 cat shared_prefs/garage.xml" | tr -d '\r' | grep -o '&quot;balance&quot;:[0-9]*' | head -1)"
    "$ADB" shell am force-stop "$PKG" < /dev/null; sleep 1
    "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1; sleep 5
    again="$(log | grep -c -E "trip settled")"
    echo "   settled: ${settled:-none}"
    echo "   balance: ${before#*:} -> ${after#*:}; settlements after a second restart: $again (1 = settled once)"
  } | tee "$OUT/persist.txt"
}

# 30 s of driving with events while top samples the app.
drive_sample() {
  ( hold_speed 50 14; drive turn --es dir left --ei m 200; hold_speed 35 16 ) &
  local hold=$!
  drive camera --ei kph 60 --ei m 300
  cpu_over 28
  wait "$hold"
}

cpu() {
  echo "app %CPU while driving -> docs/m3/cpu.txt"
  start_clean
  local with without idle
  drive limit --ei kph 50; app --es cmd start; sleep 2
  post "/vehicle/gear?gear=Drive"; sleep 1
  with="$(drive_sample)"
  drive pleos --es value off; sleep 6
  without="$(drive_sample)"
  drive pleos --es value auto
  stop_car; post "/vehicle/gear?gear=Park"; sleep 2; tap_text "개러지에서 해금하기"; app --es cmd stop; sleep 8
  idle="$(cpu_over 20)"
  {
    echo "app %CPU (top, one core = 100), $(date '+%Y-%m-%d %H:%M')"
    echo "driving, engine on, D, trip, Pleos SDK on:   $with   (budget 20)"
    echo "the same, Pleos SDK off:                    $without"
    echo "garage, engine off, parked:                 $idle   (budget 5)"
  } | tee "$OUT/cpu.txt"
}

music() {
  echo "music alongside the engine -> docs/m3/music.txt"
  start_clean; "$ADB" logcat -c
  app --es cmd start; sleep 3
  drive music; sleep 3
  local players
  players="$("$ADB" shell dumpsys audio < /dev/null | tr -d '\r' | grep -E "AudioPlaybackConfiguration.*(USAGE_GAME|USAGE_MEDIA)" | sed -E 's/^ +//' | head -6)"
  local sit
  sit="$(log | grep -E "situation|priority" | tail -3 | sed -E 's/^[0-9-]+ [0-9:.]+ +[0-9]+ +[0-9]+ I //')"
  sleep 8; app --es cmd stop
  {
    echo "music (media usage, full focus) beside the idling engine (game usage, no focus), $(date '+%Y-%m-%d %H:%M')"
    echo "players while both play:"; echo "$players"
    echo "drive host:"; echo "${sit:-(no situation change: music does not duck the engine)}"
  } | tee "$OUT/music.txt"
}

cmd="${1:-}"
case "$cmd" in
  flow) flow ;;
  trip) trip ;;
  persist) persist ;;
  cpu) cpu ;;
  music) music ;;
  *) echo "usage: drive.sh flow|trip|persist|cpu|music"; exit 1 ;;
esac
