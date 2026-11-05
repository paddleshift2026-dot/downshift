#!/usr/bin/env bash
# Downshift M4 demo and submission checks on the Pleos emulator. Requirements: emulator booted, app installed
# (./gradlew installDebug), `bash tools/probe.sh grant`, `bash tools/sound.sh forward`. Results go to docs/m4/.
#
#   bash tools/show.sh wallet     # the demo wallet apart from the real one, demo-only injections -> docs/m4/wallet.txt
#   bash tools/show.sh latency    # web pedal message -> sound, the way sound.sh latency does it -> docs/m4/latency.txt
#   bash tools/show.sh cpu        # %CPU (one core = 100) with demo mode and the web pedal -> docs/m4/cpu.txt
#   bash tools/show.sh background # the engine and the trip behind the navigation app -> docs/m4/background.txt
#   bash tools/show.sh pt         # PT rehearsal scene by scene (Enter); PT_AUTO=1 straight through, PT_PEDAL=web for
#                                 #   the revs on the web pedal, PT_VOLUME=0..45 the car volume (default 35);
#                                 #   VOICE_DIR=docs/m5/voice_user (pt and video): the voice scene's takes, else synthetic ones
#   bash tools/show.sh video      # a cold start and the PT flow recorded with sound (car volume 45, then 20) -> demo.webm,
#                                 #   demo.srt (subtitles), youtube.md (title, description, chapters); then burn the
#                                 #   subtitles in: python tools/serve_save.py . docs/m4 and open
#                                 #   http://127.0.0.1:8799/tools/caption_burn.html (?size=720 for a phone copy)
#   bash tools/show.sh shots      # screenshots for the planning document and the README -> docs/m4/shots/*.jpg
#   bash tools/show.sh volume N   # the car's media volume index, 0..45 (the emulator starts at 20)
#   bash tools/show.sh soak [MIN] # MIN minutes (30) of drive cycles with V12 running: restarts, CPU, PSS -> docs/m4/soak.txt
#   bash tools/show.sh coldstart  # five cold starts (am start -W) -> docs/m4/coldstart.txt
#   bash tools/show.sh release    # the release APK: contents, install, demo mode, adb, then the debug build back
#   bash tools/show.sh ux         # the driving restrictions: neutral stops a preview and locks the garage -> docs/m4/ux.txt
#   bash tools/show.sh voice      # the voice parts: record sheet, takes, cards, parked-only, a drive with every voice -> docs/m5/voice.txt
#   bash tools/show.sh voicecpu   # %CPU revving in park with all five voice cards on, then none -> docs/m5/cpu.txt
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

# The PT flow, about 2 min 45 s: the first-run note, garage previews, the start and revs, a drive (a signalled turn,
# a school zone, a guidance prompt, a camera), the summary with V12 unlocked, a V12 start, and the voice parts (a take
# from $VOICE_DIR or the synthetic ones, already in the app's voice_in: see voice_ready). Captions mark each part.
scenes() {
  step "1 첫 실행 안내"
  caption "첫 실행 안내 · 엔진음은 실내 스피커로만, 내비 안내와 경고가 먼저" 9 "첫 실행 안전 안내"
  sleep 3; tap_text "위 내용을 확인했고"; sleep 1; tap_text "동의하고 개러지로"; sleep 2
  step "2 개러지와 미리 듣기"
  caption "개러지 · 잠긴 부품도 P에선 미리 들어 봐요 · V8" 8 "개러지와 미리 듣기"
  garage tab --es tab engine; sleep 1; garage preview --es part v8; sleep 5
  caption "터보 3단 · 휘슬과 블로오프" 7
  garage tab --es tab intake; sleep 0.8; garage preview --es part t3; sleep 5
  caption "트랙 배기 · 직관 사운드를 실내에서만" 7
  garage tab --es tab exhaust; sleep 0.8; garage preview --es part track; sleep 5
  step "3 시동과 레브"
  caption "시동 · START를 길게 누르면 바늘이 한 번 쓸고 공회전으로" 8 "시동과 정차 중 레브"
  drive screen --es to start; sleep 1.5; hold_text "시동 걸기" 1300; sleep 3
  caption "정차 중 레브 · 페달을 떼면 회전이 떨어지며 팝콘" 7
  rev 70 1.2; sleep 1.5; rev 90 1.0; sleep 2
  step "4 주행"
  caption "기어 N · 주행 규제(DO)가 걸리면 정차 전용 화면은 잠겨요" 4 "주행 규제와 주행 화면"
  post "/vehicle/gear?gear=Neutral"; sleep 4
  caption "기어 D · 주행 화면은 이번 주행 포인트와 엔진음 상태만, rpm·속도는 없어요" 7
  post "/vehicle/gear?gear=Drive"; sleep 2; drive limit --ei kph 50
  caption "가속하면 rpm이 차오르고, 페달을 떼면 팝콘, 감속하면 다운시프트 블립" 10
  hold_speed 50 6; post "/vehicle/controls?accelerator=0"; sleep 2; post "/vehicle/controls?brake=30"; sleep 1.5; post "/vehicle/controls?brake=0"
  caption "회전 30m 전부터 방향지시등을 켜면 +0.5P" 9
  drive turn --es dir left --ei m 80; post "/vehicle/turn-signal?mode=BlinkLeft"; hold_speed 40 8; post "/vehicle/turn-signal?mode=Off"
  # 22 km/h: hold_speed swings a few km/h either way, and the zone is kept only if it never goes over 30
  caption "스쿨존 100m 전부터 엔진음을 줄여요" 8
  drive school --ei m 120; hold_speed 22 7
  caption "내비 안내가 나오면 잠시 줄여요" 4
  drive guide; hold_speed 22 3
  caption "단속 카메라 앞에서 규정 속도를 지키면 +6P (시연 배속 ×20)" 10
  drive lapse --ei value 20; hold_speed 22 2; drive camera --ei kph 50 --ei m 1200; hold_speed 45 6
  step "5 결산과 V12 해금"
  caption "기어 P · 이번 주행 결산: 거리·제한속도·스쿨존·카메라·방향지시등 포인트" 8 "주행 결산과 V12 해금"
  stop_car; post "/vehicle/gear?gear=Park"; drive lapse --ei value 1; sleep 5
  caption "포인트로 V12 해금" 8
  tap_text "개러지에서 해금하기"; sleep 1.5; garage tab --es tab engine; sleep 1
  garage unlock --es part v12; sleep 1.2; garage equip --es part v12; sleep 1.2; app --es cmd stop; sleep 1
  step "6 V12 시동"
  caption "V12로 다시 시동" 8 "V12 시동"
  drive screen --es to start; sleep 1.2; hold_text "시동 걸기" 1300; sleep 1.5; rev 80 1.0; sleep 1; app --es cmd stop; sleep 0.5
  step "7 내 목소리 파츠"
  caption "내 목소리 파츠 · 엔진 탭 맨 끝 카드에서 녹음하기" 6 "내 목소리 파츠"
  # the card is tapped on screen; its 녹음하기 is the same as adb voice open, which skips a two-second screen read
  drive screen --es to garage; sleep 1; garage tab --es tab engine; sleep 0.8; tap_text "내 목소리 엔진"; sleep 0.8
  app --es cmd voice --es action open --es slot engine; sleep 1
  caption "안내대로 '부르르르' 2초 · 녹음은 정차 중에만, 차 안에만 저장" 7
  app --es cmd voice --es action mic --es file engine.wav; tap_text "녹음 시작"; sleep 5.5
  caption "내 목소리로 들어보기 · V12 구조 그대로, 소리만 내 목소리" 6
  tap_text "들어보기"; sleep 4.5
  caption "이걸로 쓸게요 · 휘슬·블로오프·팝·치키차도 같은 방법으로" 4
  tap_text "이걸로 쓸게요"; sleep 0.5
  for s in whistle blowoff pop shift; do app --es cmd voice --es action load --es slot "$s" --es file "$s.wav"; done; sleep 1
  caption "출발하면 부아앙 — 치키차 — 부아앙 · 차 안에서만 들리는 내 엔진, Downshift" 20
  drive screen --es to start; sleep 1.2; hold_text "시동 걸기" 1300; sleep 1.5
  # 45% to about 38 km/h shifts up once, a light pedal once more, a short kick down twice with boost, then lifting off
  # pops and blows off: every voice in under 50 km/h (V12 shift points: ShiftSchedule)
  post "/vehicle/gear?gear=Drive"; sleep 0.8; post "/vehicle/controls?accelerator=45"; sleep 5
  post "/vehicle/controls?accelerator=10"; sleep 1.5; post "/vehicle/controls?accelerator=90"; sleep 1.2
  post "/vehicle/controls?accelerator=0"; sleep 1.5; stop_car; post "/vehicle/gear?gear=Park"; sleep 1; app --es cmd stop
}

# Scene 7's takes: $VOICE_DIR's five WAVs (the user's, exported from a debug build) or the synthetic ones, into the
# app's voice_in; every voice card off and no take saved, so the scene records from scratch; the microphone granted.
voice_ready() {
  app --es cmd voice --es action clear; sleep 1
  if [ -n "${VOICE_DIR:-}" ]; then
    "$ADB" shell "run-as $PKG --user 10 mkdir -p files/voice_in" < /dev/null
    for f in engine whistle blowoff pop shift; do
      "$ADB" exec-in "run-as $PKG --user 10 sh -c 'cat > files/voice_in/$f.wav'" < "$VOICE_DIR/$f.wav"
    done
  else
    push_samples
  fi
  "$ADB" shell pm grant --user 10 "$PKG" android.permission.RECORD_AUDIO < /dev/null
}

pt() {
  echo "PT rehearsal; PT_AUTO=1 runs straight through, PT_PEDAL=web leaves the revs to the web pedal"
  setup "${PT_VOLUME:-35}"
  demo_mode on "${PT_BUTTONS:-off}"; drive lapse --ei value 1
  voice_ready
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
- 올릴 파일: \`demo_captioned.webm\` (설명 자막이 영상에 들어 있음, \`tools/caption_burn.html\`로 만듦). \`demo.srt\`는 올리지 않아도 됨(올리면 CC를 켠 사람에게 자막이 두 번 보임)

## 설명

Downshift는 법규를 지키며 달려 모은 포인트로 가상 엔진을 조립·튜닝하고, 내 페달에 반응하는 그 엔진 소리를 차 실내 스피커로만 듣는 Pleos Connect 앱입니다.
Pleos Connect Emulator v2.1.0에서 실제로 돌린 화면과 소리입니다. 기어·페달·좌석은 Virtual Console로 바꾸고, 에뮬레이터 내비가 보내지 않는 제한속도·스쿨존·단속 카메라는 시연 모드에서 넣었습니다.
주행 중에는 주의 분산 최적화(DO) 규칙에 맞춘 주행 화면만 보이고, 주행 규제(CarUxRestrictions)가 걸리면 개러지 같은 정차 전용 화면은 잠깁니다. 결산의 포인트는 시연 지갑의 포인트이고, 카메라 구간부터는 시연 배속 ×20으로 센 거리입니다.
마지막 장면은 내 목소리 파츠입니다. 정차 중에 녹음한 "부르르르"가 장착한 V12의 점화 순서대로 엔진음이 되고, "치키차"는 기어가 바뀔 때마다 납니다. 녹음은 차 안에만 저장합니다. 영상의 녹음은 에뮬레이터 가상 마이크(PC 마이크)로 미리 녹음한 파일을 같은 녹음 창에 넣은 것입니다.

## 챕터

$(printf '%s' "$CHAPTERS" | sed '1s/^[0-9]*:[0-9]*/0:00/')
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
  voice_ready
  SRT="$OUT/demo.srt"; : > "$SRT"; CUE=0; CHAPTERS=""
  # A cold start on camera for the team wordmark; the flow goes to the safety note while the wordmark is up.
  "$ADB" shell am force-stop "$PKG" < /dev/null; sleep 2
  "$ADB" logcat -c
  rec_start demo 178
  caption "Pleos Connect 에뮬레이터에서 Downshift 실행" 6
  "$ADB" shell am start -W -n "$PKG/.MainActivity" < /dev/null > /dev/null 2>&1
  drive screen --es to safety; sleep 2.5
  scenes
  rec_stop; sleep 2
  volume 20
  demo_mode on on
  python -I "$HERE/webm_audio.py" "$OUT/demo.webm"
  srt_trim "$OUT/demo.srt"
  youtube > "$OUT/youtube.md"
  echo "  demo.srt ($CUE cues), youtube.md"
  log | grep "trip done" | tail -1 | sed -E 's/.*trip done //'
  echo "  subtitles into the video: python tools/serve_save.py . docs/m4, then open http://127.0.0.1:8799/tools/caption_burn.html"
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
# early and the lock view stands in for the garage; park opens it again; drive shows the drive screen as before,
# without the lock view flashing in between.
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
    "$ADB" logcat -c
    post "/vehicle/gear?gear=Drive"; sleep 2.5
    check "4 drive opens the drive screen" "$(onscreen '음소거')" yes
    check "4 ... not the lock view" "$(onscreen "$lock")" no
    check "4 ... and the lock view never flashed on the way" "$(seen 'lock view shown')" no
    stop_car; post "/vehicle/gear?gear=Park"; sleep 2.5
    check "5 back in park, no lock view" "$(onscreen "$lock")" no
  } | tee "$OUT/ux.txt"
}

# The synthetic takes (voice_samples.py) into the app's files/voice_in for adb's voice mic|load. The car runs apps as
# user 10, whose external storage adb cannot write, so they are streamed in with run-as (debug builds).
push_samples() {
  local dir="$HERE/../docs/m5/samples"
  python "$HERE/voice_samples.py" "$dir" > /dev/null
  "$ADB" shell "run-as $PKG --user 10 mkdir -p files/voice_in" < /dev/null
  for f in engine whistle blowoff pop shift; do
    "$ADB" exec-in "run-as $PKG --user 10 sh -c 'cat > files/voice_in/$f.wav'" < "$dir/$f.wav"
  done
}

# The voice parts (docs/superpowers/specs/2026-11-05-voice-parts-design.md) with synthetic takes fed as the microphone.
voice() {
  local out5="$HERE/../docs/m5" shots kit
  mkdir -p "$out5"
  echo "voice parts -> docs/m5/voice.txt"
  # The record sheet asks for the microphone on the first take; the check grants it up front (the takes are files).
  "$ADB" shell pm grant --user 10 "$PKG" android.permission.RECORD_AUDIO < /dev/null
  "$ADB" shell am force-stop "$PKG" < /dev/null # a record sheet left open would cover the garage
  start_clean
  drive screen --es to garage; sleep 2
  app --es cmd voice --es action clear; sleep 1
  push_samples
  "$ADB" logcat -c
  {
    echo "voice parts, $(date '+%Y-%m-%d %H:%M')"
    garage tab --es tab engine; sleep 1.5
    check "1 the engine tab ends with the voice card" "$(onscreen '내 목소리 엔진')" yes
    tap_text "녹음해서 만들어요"; sleep 1
    check "2 its action bar offers a take" "$(onscreen '녹음하기')" yes
    app --es cmd voice --es action mic --es file engine.wav
    tap_text "녹음하기"; sleep 1.5
    # (the guide line itself has quotes, which uiautomator puts in single-quoted attributes that onscreen does not read)
    check "3 the record sheet opens with the engine guide" "$(onscreen '쓰이는 곳: 실린더가')" yes
    tap_text "녹음 시작"; sleep 6.5
    check "4 the take passes the check" "$(onscreen '잘 녹음됐어요')" yes
    tap_text "이걸로 쓸게요"; sleep 2
    check "5 keeping it turns the card on" "$(onscreen '켜짐 · 장착한')" yes
    check "5 ... and the engine got four grains" "$(more "$(log | grep -c 'voice kit engine=4')" 0)" yes
    for s in whistle blowoff pop shift; do app --es cmd voice --es action load --es slot "$s" --es file "$s.wav"; sleep 1; done
    sleep 2
    check "6 the other four load as good takes" "$(log | grep -c 'voice load [a-z]* OK')" 4
    kit="$(log | grep 'voice kit' | tail -1)"
    check "6 ... and every card is heard ($kit)" "$(echo "$kit" | grep -c 'engine=4 whistle=[1-9][0-9]* blowoff=[1-9][0-9]* pop=[1-9][0-9]* shift=[1-9][0-9]*')" 1
    post "/vehicle/gear?gear=Neutral"; sleep 2.5
    check "7 DO locks the garage, voice card and all" "$(onscreen '주행 중에는 쓸 수 없어요')" yes
    post "/vehicle/gear?gear=Park"; sleep 2.5
    garage tab --es tab exhaust; sleep 1; tap_text "내 목소리 배기"; sleep 1; tap_text "다시 녹음"; sleep 1.5
    app --es cmd voice --es action mic --es file pop.wav
    tap_text "녹음 시작"; sleep 3.4; post "/vehicle/gear?gear=Neutral"; sleep 3; post "/vehicle/gear?gear=Park"; sleep 2.5
    check "8 a take cut short by DO is dropped (the guide is back)" "$(onscreen '녹음 시작')" yes
    tap_text "닫기"; sleep 1
    "$ADB" logcat -c
    drive screen --es to start; sleep 2; hold_text "시동 걸기" 1300; sleep 4
    rev 80 1.2; sleep 2
    post "/vehicle/gear?gear=Drive"; sleep 2; hold_speed 50 10; post "/vehicle/controls?accelerator=0"; sleep 3
    stop_car; post "/vehicle/gear?gear=Park"; sleep 3; app --es cmd stop; sleep 2
    shots="$(log | grep -o 'voice shots {[^}]*}' | tail -1)"
    check "9 the drive changed gear with the voice ($shots)" "$(echo "$shots" | grep -c 'voice:shift=')" 1
    check "9 ... and popped with the voice" "$(echo "$shots" | grep -c 'voice:pop=')" 1
    app --es cmd voice --es action clear; sleep 2
    check "10 deleting the takes turns every card off" "$(log | grep 'voice kit' | tail -1 | grep -c 'engine=0 whistle=0 blowoff=0 pop=0 shift=0')" 1
  } | tee "$out5/voice.txt"
}

# Mean %CPU over $1 one-second samples of the whole app and of its audio thread (DownshiftEngine), where the voices are
# made; the start screen's needle redraws dominate the app's share on the emulator (M4 cpu.txt).
cpu_split() {
  local p; p="$(pid)"
  "$ADB" shell "top -H -b -d 1 -n $1 -p $p" < /dev/null | tr -d '\r' | awk '
    $1 == "TID" { n++; next }
    n > 0 && $1 ~ /^[0-9]+$/ { app += $9; if ($0 ~ /DownshiftEngine/) eng += $9 }
    END { printf "app %.1f, engine thread %.1f (%d samples)", app / n, eng / n, n }'
}

# Revs in park for 20 s (pedal 70% and 0% a second each) with the engine on, then stops it; prints cpu_split's line.
rev_cpu() {
  drive screen --es to start; sleep 2; app --es cmd start; sleep 3
  (for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do post "/vehicle/controls?accelerator=70"; sleep 1; post "/vehicle/controls?accelerator=0"; sleep 1; done) &
  sleep 2; cpu_split 20; wait
  app --es cmd stop; sleep 2
}

# The voice parts' cost: %CPU revving in park with all five voice cards on, then with none -> docs/m5/cpu.txt
voicecpu() {
  local out5="$HERE/../docs/m5" on off
  mkdir -p "$out5"
  echo "voice parts %CPU -> docs/m5/cpu.txt"
  start_clean
  app --es cmd voice --es action clear; sleep 1
  push_samples
  for f in engine whistle blowoff pop shift; do app --es cmd voice --es action load --es slot "$f" --es file "$f.wav"; sleep 1; done
  sleep 2
  on="$(rev_cpu)"
  app --es cmd voice --es action clear; sleep 2
  off="$(rev_cpu)"
  {
    echo "app %CPU (top, one core = 100), $(date '+%Y-%m-%d %H:%M'), demo build (I4 · TURBO 2 · SPORT · DCT), engine on in park,"
    echo "pedal 70% and 0% a second each for 20 s"
    echo "all five voice cards on: $on"
    echo "no voice card on:        $off   (M3 drive: 15.1)"
  } | tee "$out5/cpu.txt"
}

cmd="${1:-}"
case "$cmd" in
  soak) soak "${2:-30}" ;;
  coldstart) coldstart ;;
  release) release ;;
  ux) ux ;;
  voice) voice ;;
  voicecpu) voicecpu ;;
  wallet) wallet ;;
  latency) latency ;;
  cpu) cpu ;;
  background) background ;;
  pt) pt ;;
  video) video ;;
  shots) shots ;;
  volume) volume "${2:?index 0..45}"; echo "car volume $(volume_index) / 45" ;;
  *) echo "usage: show.sh wallet|latency|cpu|background|pt|video|shots|volume N|soak [MIN]|coldstart|release|ux|voice|voicecpu"; exit 1 ;;
esac
