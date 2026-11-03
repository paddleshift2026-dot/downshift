#!/usr/bin/env bash
# Downshift M4 demo and submission checks on the Pleos emulator. Requirements: emulator booted, app installed
# (./gradlew installDebug), `bash tools/probe.sh grant`, `bash tools/sound.sh forward`. Results go to docs/m4/.
#
#   bash tools/show.sh wallet     # the demo wallet apart from the real one, demo-only injections -> docs/m4/wallet.txt
#   bash tools/show.sh latency    # web pedal message -> sound, the way sound.sh latency does it -> docs/m4/latency.txt
#   bash tools/show.sh cpu        # %CPU (one core = 100) with demo mode and the web pedal -> docs/m4/cpu.txt
#   bash tools/show.sh background # the engine and the trip behind the navigation app -> docs/m4/background.txt
#   bash tools/show.sh pt         # PT rehearsal scene by scene (Enter); PT_AUTO=1 straight through, PT_PEDAL=web for
#                                 #   the revs on the web pedal, PT_VOLUME=0..45 the car volume (default 35)
#   bash tools/show.sh video      # the PT flow recorded with sound (car volume 45, then 20) -> docs/m4/demo.webm,
#                                 #   demo.srt (subtitles), youtube.md (title, description, chapters)
#   bash tools/show.sh shots      # screenshots for the planning document and the README -> docs/m4/shots/*.jpg
#   bash tools/show.sh volume N   # the car's media volume index, 0..45 (the emulator starts at 20)
#   bash tools/show.sh soak [MIN] # MIN minutes (30) of drive cycles with V12 running: restarts, CPU, PSS -> docs/m4/soak.txt
#   bash tools/show.sh coldstart  # five cold starts (am start -W) -> docs/m4/coldstart.txt
#   bash tools/show.sh release    # the release APK: contents, install, demo mode, adb, then the debug build back
#   bash tools/show.sh ux         # the driving restrictions: neutral stops a preview and locks the garage -> docs/m4/ux.txt
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
onscreen() { if [ -n "$(ui_node "$1")" ]; then echo yes; else echo no; fi; }

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

# The engine thread's total CPU time (TIME+) and the process's state; a frozen or stopped engine stops counting.
engine_time() { "$ADB" shell "top -H -b -n 1 -p $1" < /dev/null | tr -d '\r' | awk '/DownshiftEngine/ { print $11; exit }'; }
proc_state() {
  "$ADB" shell dumpsys activity processes "$PKG" < /dev/null | tr -d '\r' \
    | grep -o -E 'procState=[A-Z_]+|curProcState=[0-9]+|isFrozen=[a-z]+|mFrozen=[a-z]+|frozen=[a-z]+' | sort -u | tr '\n' ' '
}

background() {
  echo "the app behind the navigation -> docs/m4/background.txt"
  start_clean
  drive screen --es to start; sleep 2; app --es cmd start; sleep 3
  local p uid s
  p="$(pid)"
  uid="$("$ADB" shell pm list packages -U --user 10 "$PKG" < /dev/null | tr -d '\r' | grep -o 'uid:[0-9]*' | cut -d: -f2)"
  {
    echo "the app behind the navigation app, $(date '+%Y-%m-%d %H:%M'): engine idling, demo mode, pid $p uid $uid"
    drive route --es value cancel; sleep 3 # cancelling a route brings the navigation app forward
    echo "front: $("$ADB" shell dumpsys activity activities < /dev/null | tr -d '\r' | grep -m1 'topResumedActivity' | sed 's/^ *//' | cut -c1-140)"
    for s in 0 15 30 45 60 75 90; do
      [ "$s" -gt 0 ] && sleep 15
      echo "+${s}s: engine thread TIME+ $(engine_time "$p") | $(proc_state)"
    done
    echo "players: $("$ADB" shell dumpsys audio < /dev/null | tr -d '\r' | grep "AudioPlaybackConfiguration" | grep "u/pid:$uid" | grep -o 'state:[a-z]*' | sort | uniq -c | tr -s ' ' | tr '\n' ' ')"
    "$ADB" logcat -c
    post "/vehicle/gear?gear=Drive"; sleep 3
    echo "a trip started behind the navigation: $(log | grep -c 'trip start')"
    post "/vehicle/gear?gear=Park"; sleep 2
    echo "and was settled: $(log | grep -c 'trip done')"
    echo "pid after: $(pid) (same = not killed)"
  } | tee "$OUT/background.txt"
  "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1
  app --es cmd stop
}

# Forwards, the app's permissions, a clean demo start and the car volume ($1).
setup() {
  "$ADB" forward tcp:8088 tcp:8088 > /dev/null; "$ADB" forward tcp:8790 tcp:8790 > /dev/null
  bash "$HERE/probe.sh" grant > /dev/null 2>&1; sleep 3
  start_clean
  volume "$1"
}

step() { echo "== $1"; if [ "${PT_WAIT:-0}" = 1 ]; then read -r -p "   Enter: 다음 장면 " _ < /dev/tty; fi; }

# The console pedal at PCT % for SECS seconds, then off; with PT_PEDAL=web a judge presses the web pedal instead.
rev() {
  if [ "${PT_PEDAL:-}" = web ]; then echo "   웹 페달을 밟아 보세요 (${2}초)"; sleep "$2"; return; fi
  post "/vehicle/controls?accelerator=$1"; sleep "$2"; post "/vehicle/controls?accelerator=0"
}

# The PT flow, about 2 min 50 s: the first-run note, garage previews, the start and revs, a drive (a signalled turn,
# a school zone, a guidance prompt, a camera), the summary with V12 unlocked, a V12 start. Captions mark each part.
scenes() {
  step "1 첫 실행 안내"
  caption "첫 실행 안내 · 엔진음은 실내 스피커로만, 내비 안내와 경고가 먼저" 11 "첫 실행 안전 안내"
  sleep 6; tap_text "위 내용을 확인했고"; sleep 1.5; tap_text "동의하고 개러지로"; sleep 2.5
  step "2 개러지와 미리 듣기"
  caption "개러지 · 잠긴 부품도 정차 중엔 미리 들어 봐요 · V8" 11 "개러지와 미리 듣기"
  garage tab --es tab engine; sleep 1; garage preview --es part v8; sleep 9
  caption "터보 3단 · 휘슬과 블로오프" 10
  garage tab --es tab intake; sleep 1; garage preview --es part t3; sleep 9
  caption "트랙 배기 · 직관 사운드를 실내에서만" 10
  garage tab --es tab exhaust; sleep 1; garage preview --es part track; sleep 9
  step "3 시동과 레브"
  caption "시동 · START를 길게 누르면 바늘이 한 번 쓸고 공회전으로" 9 "시동과 정차 중 레브"
  drive screen --es to start; sleep 2; hold_text "시동 걸기" 1300; sleep 4
  caption "정차 중 레브 · 페달을 떼면 회전이 떨어지며 팝콘" 10
  rev 70 1.2; sleep 2; rev 90 1.0; sleep 3
  step "4 주행"
  caption "기어 D · 주행 중엔 Pleos가 앱 화면을 가려요. Downshift는 화면 대신 소리로" 9 "주행: 화면 대신 소리"
  post "/vehicle/gear?gear=Drive"; sleep 3; drive limit --ei kph 50
  caption "가속하면 rpm이 차오르고, 페달을 떼면 팝콘, 감속하면 다운시프트 블립" 14
  hold_speed 50 8; post "/vehicle/controls?accelerator=0"; sleep 3; post "/vehicle/controls?brake=30"; sleep 2; post "/vehicle/controls?brake=0"
  caption "회전 30m 전부터 방향지시등을 켜면 +0.5P" 13
  drive turn --es dir left --ei m 80; post "/vehicle/turn-signal?mode=BlinkLeft"; hold_speed 40 12; post "/vehicle/turn-signal?mode=Off"
  # 22 km/h: hold_speed swings a few km/h either way, and the zone is kept only if it never goes over 30
  caption "스쿨존 100m 전부터 엔진음을 줄여요" 11
  drive school --ei m 120; hold_speed 22 10
  caption "내비 안내가 나오면 잠시 줄여요" 5
  drive guide; hold_speed 22 3
  caption "단속 카메라 앞에서 규정 속도를 지키면 +6P (시연 배속 ×20)" 13
  drive lapse --ei value 20; hold_speed 22 3; drive camera --ei kph 50 --ei m 1500; hold_speed 45 9
  step "5 결산과 V12 해금"
  caption "기어 P · 이번 주행 결산: 거리·제한속도·스쿨존·카메라·방향지시등 포인트" 10 "주행 결산과 V12 해금"
  stop_car; post "/vehicle/gear?gear=Park"; sleep 8
  caption "포인트로 V12 해금" 8
  tap_text "개러지에서 해금하기"; sleep 2; garage tab --es tab engine; sleep 1
  garage unlock --es part v12; sleep 1.5; garage equip --es part v12; sleep 2; app --es cmd stop; sleep 1
  step "6 V12 시동"
  caption "V12로 다시 시동 · 차 안에서만 들리는 내 엔진, Downshift" 14 "V12 시동"
  drive screen --es to start; sleep 2; hold_text "시동 걸기" 1300; sleep 3; rev 80 1.0; sleep 3; app --es cmd stop; sleep 2
  drive lapse --ei value 1
}

pt() {
  echo "PT rehearsal; PT_AUTO=1 runs straight through, PT_PEDAL=web leaves the revs to the web pedal"
  setup "${PT_VOLUME:-35}"
  demo_mode on "${PT_BUTTONS:-off}"; drive lapse --ei value 1
  drive screen --es to safety; sleep 2
  "$ADB" logcat -c
  REC_T0="$(date +%s%3N)"; SRT=/dev/null
  [ "${PT_AUTO:-0}" = 1 ] || PT_WAIT=1
  scenes
  echo "done in $(( ($(date +%s%3N) - REC_T0) / 1000 )) s; $(log | grep -c 'trip done') trip(s) settled"
  log | grep "trip done" | tail -1 | sed -E 's/.*trip done //'
  demo_mode on on
}

youtube() {
  cat <<EOF
# 시연 영상 YouTube 업로드용

- 제목: Downshift (다운시프트) — 차 안에서만 들리는 내 엔진 | 2026 현대자동차그룹 차량용 앱 개발 공모전 시연
- 공개 범위: 일부 공개(미등록)
- 자막: \`demo.srt\`를 YouTube Studio → 자막 → 파일 업로드(타이밍 포함)

## 설명

Downshift는 법규를 지키며 달려 모은 포인트로 가상 엔진을 조립·튜닝하고, 내 페달에 반응하는 그 엔진 소리를 차 실내 스피커로만 듣는 Pleos Connect 앱입니다.
Pleos Connect Emulator v2.1.0에서 실제로 돌린 화면과 소리입니다. 기어·페달·좌석은 Virtual Console로 바꾸고, 에뮬레이터 내비가 보내지 않는 제한속도·스쿨존·단속 카메라는 시연 모드에서 넣었습니다.
주행 중에는 Pleos 규정대로 앱 화면이 가려지고, Downshift는 화면 대신 소리와 기록으로 동작합니다. 결산의 포인트는 시연 지갑의 포인트이고, 카메라 구간부터는 시연 배속 ×20으로 센 거리입니다.

## 챕터

${CHAPTERS}
EOF
}

# Ends every subtitle 0.1 s before the next one starts, so a long cue never overlaps the next scene's.
srt_trim() {
  python -I - "$1" <<'PY'
import re, sys
p = sys.argv[1]
cues = [c for c in open(p, encoding="utf-8").read().strip().split("\n\n") if c.strip()]
ms = lambda t: ((int(t[0:2]) * 60 + int(t[3:5])) * 60 + int(t[6:8])) * 1000 + int(t[9:12])
fmt = lambda v: "%02d:%02d:%02d,%03d" % (v // 3600000, v // 60000 % 60, v // 1000 % 60, v % 1000)
times = [re.search(r"(\S+) --> (\S+)", c).groups() for c in cues]
out = []
for i, c in enumerate(cues):
    start, end = ms(times[i][0]), ms(times[i][1])
    if i + 1 < len(cues):
        end = min(end, ms(times[i + 1][0]) - 100)
    lines = c.split("\n")
    out.append("\n".join([lines[0], f"{fmt(start)} --> {fmt(end)}"] + lines[2:]))
open(p, "w", encoding="utf-8", newline="\n").write("\n\n".join(out) + "\n")
PY
}

video() {
  echo "the PT flow with sound -> docs/m4/demo.webm, demo.srt, youtube.md"
  setup 45
  demo_mode on off; drive lapse --ei value 1
  drive screen --es to safety; sleep 2
  SRT="$OUT/demo.srt"; : > "$SRT"; CUE=0; CHAPTERS=""
  "$ADB" logcat -c
  rec_start demo 178
  scenes
  rec_stop; sleep 2
  volume 20
  demo_mode on on
  python -I "$HERE/webm_audio.py" "$OUT/demo.webm"
  srt_trim "$OUT/demo.srt"
  youtube > "$OUT/youtube.md"
  echo "  demo.srt ($CUE cues), youtube.md"
  log | grep "trip done" | tail -1 | sed -E 's/.*trip done //'
}

# "yes" when the Pleos home covers the app (its cover is light grey where the app is dark).
covered() {
  local tmp; tmp="$(mktemp)"
  "$ADB" exec-out screencap -p > "$tmp"
  python -I -c "import sys; from PIL import Image; p = Image.open(sys.argv[1]).convert('RGB').getpixel((1100, 300)); print('yes' if sum(p) > 400 else 'no')" "$tmp"
  rm -f "$tmp"
}

# A drive screen capture; M3's (taken in D at rest, the dev row cut off) while the Pleos home covers the app in D.
drive_shot() {
  if [ "$(covered)" = no ]; then shot "$1"; return; fi
  python -I -c "import sys; from PIL import Image; Image.open(sys.argv[1]).crop((0, 40, 1682, 1210)).save(sys.argv[2])" "$HERE/../docs/m3/$2.png" "$OUT/$1.png"
  echo "  $1.png (M3's $2.png: the Pleos home covers the drive screen in D now)"
}

shots() {
  echo "screenshots -> docs/m4/shots/ and 현기차_경진대회/화면캡처_다운시프트/"
  setup 20
  demo_mode on off; drive lapse --ei value 1
  local keep="$OUT" dst="$HERE/../../현기차_경진대회/화면캡처_다운시프트"
  OUT="$keep/shots/png"; mkdir -p "$OUT"
  drive screen --es to safety; sleep 2.5; shot 01_onboarding
  tap_text "위 내용을 확인했고"; sleep 1; tap_text "동의하고 개러지로"; sleep 2.5
  garage tab --es tab engine; sleep 1; garage preview --es part v8; sleep 9; shot 02_garage_engine
  garage tab --es tab intake; sleep 1.5; garage preview --es part t3; sleep 9; shot 03_garage_intake
  drive screen --es to start; sleep 2; hold_text "시동 걸기" 1300; sleep 3.5; shot 04_start_idle; shot 09_full_screen full
  post "/vehicle/gear?gear=Drive"; sleep 2.5; drive_shot 05_drive 04_drive_d
  post "/vehicle/seat?kind=occupied&area=passenger&value=true"; sleep 2.5; drive_shot 07_passenger 07_passenger
  post "/vehicle/seat?kind=occupied&area=passenger&value=false"; sleep 1
  # A trip that keeps every rule, as in the video: a signalled turn, a school zone at 22 km/h, a camera under its limit.
  drive limit --ei kph 50; hold_speed 40 5
  drive turn --es dir left --ei m 80; post "/vehicle/turn-signal?mode=BlinkLeft"; hold_speed 40 9; post "/vehicle/turn-signal?mode=Off"
  drive school --ei m 60; hold_speed 22 10; stop_car; sleep 2; drive_shot 06_school_zone 05_school_zone
  drive lapse --ei value 20; hold_speed 22 3; drive camera --ei kph 50 --ei m 1500; hold_speed 45 8
  stop_car; post "/vehicle/gear?gear=Park"; sleep 3; shot 08_summary
  drive lapse --ei value 1; app --es cmd stop; demo_mode on on
  OUT="$keep"
  python -I "$HERE/export_shots.py" "$OUT/shots/png" "$OUT/shots"
  mkdir -p "$dst"; cp "$OUT"/shots/*.jpg "$dst"/
  echo "  copied to $(cd "$dst" && pwd)"
}

pss_kb() { "$ADB" shell dumpsys meminfo "$1" < /dev/null | tr -d '\r' | grep -o 'TOTAL PSS: *[0-9]*' | grep -o '[0-9]*$'; }

soak() {
  local mins="${1:-30}" p0 end cycle=0 c pss
  echo "a ${mins}-minute drive loop -> docs/m4/soak.txt"
  start_clean
  garage tab --es tab engine; sleep 1; garage unlock --es part v12; sleep 1; garage equip --es part v12; sleep 1 # the heaviest engine
  drive screen --es to start; sleep 2; app --es cmd start; sleep 3
  p0="$(pid)"; end=$((SECONDS + mins * 60))
  "$ADB" logcat -c
  {
    echo "soak, $(date '+%Y-%m-%d %H:%M'): ${mins} min of drive cycles with V12 running, demo mode, pid $p0, PSS $(pss_kb "$p0") KB"
    while [ "$SECONDS" -lt "$end" ]; do
      cycle=$((cycle + 1))
      post "/vehicle/gear?gear=Drive"; sleep 1; drive limit --ei kph 50
      hold_speed 40 15
      drive turn --es dir left --ei m 80; post "/vehicle/turn-signal?mode=BlinkLeft"; hold_speed 40 9; post "/vehicle/turn-signal?mode=Off"
      if [ $((cycle % 2)) = 1 ]; then drive school --ei m 60; hold_speed 22 20; fi
      hold_speed 45 12 & c="$(cpu_over 10)"; wait
      stop_car; post "/vehicle/gear?gear=Park"; sleep 4
      drive screen --es to start; sleep 1
      pss=""; if [ $((cycle % 5)) = 0 ]; then pss=", PSS $(pss_kb "$p0") KB"; fi
      echo "cycle $cycle at $((mins * 60 - end + SECONDS)) s: pid $(pid), driving %CPU $c$pss"
      if [ "$(pid)" != "$p0" ]; then echo "the app restarted"; break; fi
    done
    echo "end: PSS $(pss_kb "$p0") KB; trips settled $(log | grep -c 'trip done') of $cycle"
    echo "crash buffer lines for the app: $("$ADB" logcat -d -b crash < /dev/null | grep -c "$PKG")"
    echo "FATAL or ANR for the app: $("$ADB" logcat -d < /dev/null | grep -E 'FATAL EXCEPTION|ANR in' | grep -c "$PKG")"
  } | tee "$OUT/soak.txt"
  app --es cmd stop
}

coldstart() {
  echo "cold starts -> docs/m4/coldstart.txt"
  local t all="" front
  for i in 1 2 3 4 5; do
    "$ADB" shell am force-stop "$PKG" < /dev/null; sleep 3
    t="$("$ADB" shell am start -W -n "$PKG/.MainActivity" < /dev/null | tr -d '\r')"
    front="$(echo "$t" | awk '/^Activity:/ { print $2 }')"
    all="$all $(echo "$t" | awk '/^TotalTime:/ { print $2 }')"; sleep 5
  done
  {
    echo "cold start (am force-stop, am start -W), $(date '+%Y-%m-%d %H:%M'): TotalTime ms:$all (first frame: $front)"
    echo "median $(echo "$all" | tr ' ' '\n' | grep . | sort -n | awk '{ v[NR] = $1 } END { print v[int((NR + 1) / 2)] }') ms (PRD: start within 10 s)"
  } | tee "$OUT/coldstart.txt"
}

release() {
  echo "the release APK -> docs/m4/release.txt"
  local apk="$HERE/../app/build/outputs/apk/release/app-release.apk" texts
  (cd "$HERE/.." && ./gradlew --offline assembleRelease -q) || { echo "release build failed"; return 1; }
  {
    echo "release APK, $(date '+%Y-%m-%d %H:%M'): $(du -h "$apk" | cut -f1)"
    echo "audio files inside: $(unzip -l "$apk" | grep -c -i -E '\.(wav|dsa)$') (wav/dsa), engines/: $(unzip -l "$apk" | grep -c 'assets/engines/')"
    echo "pedal page inside: $(unzip -l "$apk" | grep -c 'assets/pedal/index.html')"
    "$ADB" install -r "$apk" < /dev/null | tr -d '\r' | tail -1
    "$ADB" shell am start -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1; sleep 5
    "$ADB" logcat -c
    demo_mode on on; sleep 1; drive screen --es to garage; sleep 2
    texts="$("$ADB" shell uiautomator dump //sdcard/ui.xml < /dev/null > /dev/null 2>&1; "$ADB" shell cat //sdcard/ui.xml < /dev/null)"
    echo "dev row (사운드 랩) on screen: $(echo "$texts" | grep -c '사운드 랩')"
    echo "adb demo command reached the release app: $("$ADB" logcat -d -s DownshiftDemo < /dev/null | grep -c 'command demo')"
    "$ADB" forward tcp:8790 tcp:8790 > /dev/null
    echo "web pedal page: $(curl -s -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:8790/)"
    demo_mode off; sleep 1; drive limit --ei kph 50; sleep 1
    echo "a point injection with demo mode off: $("$ADB" logcat -d -s DownshiftLab < /dev/null | grep -c 'drive limit ignored') ignored"
    demo_mode on on
  } | tee "$OUT/release.txt"
  (cd "$HERE/.." && ./gradlew --offline installDebug -q) && echo "  debug build back on the emulator"
}

# The driving restrictions (CarUxRestrictions): Pleos asks for DO in neutral at a standstill, so a garage preview stops
# early and the lock view stands in for the garage; park opens it again; drive shows the drive screen as before.
ux() {
  echo "driving restrictions -> docs/m4/ux.txt"
  start_clean
  drive screen --es to garage; sleep 2
  "$ADB" logcat -c
  local over lock="주행 중에는 쓸 수 없어요"
  {
    echo "driving restrictions (CarUxRestrictions), $(date '+%Y-%m-%d %H:%M')"
    garage preview --es part v8; sleep 1.5
    check "1 a V8 preview plays in park" "$(seen 'preview ENGINE')" yes
    post "/vehicle/gear?gear=Neutral"; sleep 2.5
    check "2 neutral at a standstill asks for DO (no video, no setup)" "$(seen 'ux DO=true active=0x50')" yes
    over="$(log | grep -o 'preview over at [0-9.]* of [0-9.]*' | tail -1)"
    check "2 ... the preview stops before its end ($over)" "$(echo "$over" | awk '{ print ($4 < $6) ? "yes" : "no" }')" yes
    check "2 ... the lock view stands in for the garage" "$(onscreen "$lock")" yes
    post "/vehicle/gear?gear=Park"; sleep 2.5
    check "3 park asks for no DO" "$(seen 'ux DO=false')" yes
    check "3 ... the garage is back" "$(onscreen "$lock")" no
    post "/vehicle/gear?gear=Drive"; sleep 2.5
    check "4 drive opens the drive screen" "$(onscreen '음소거')" yes
    check "4 ... not the lock view" "$(onscreen "$lock")" no
    stop_car; post "/vehicle/gear?gear=Park"; sleep 2.5
    check "5 back in park, no lock view" "$(onscreen "$lock")" no
  } | tee "$OUT/ux.txt"
}

cmd="${1:-}"
case "$cmd" in
  soak) soak "${2:-30}" ;;
  coldstart) coldstart ;;
  release) release ;;
  ux) ux ;;
  wallet) wallet ;;
  latency) latency ;;
  cpu) cpu ;;
  background) background ;;
  pt) pt ;;
  video) video ;;
  shots) shots ;;
  volume) volume "${2:?index 0..45}"; echo "car volume $(volume_index) / 45" ;;
  *) echo "usage: show.sh wallet|latency|cpu|background|pt|video|shots|volume N|soak [MIN]|coldstart|release|ux"; exit 1 ;;
esac
