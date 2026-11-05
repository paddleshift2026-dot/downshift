# M5 결과 — 내 목소리 파츠 (2026-11-05)

설계: [`superpowers/specs/2026-11-05-voice-parts-design.md`](superpowers/specs/2026-11-05-voice-parts-design.md). 계획: `현기차_경진대회/.claude/plans/downshift-m5-voice.plan.md`. 브랜치 `feature/m5-voice`(`feature/m4-submit`에서). 측정은 `docs/m5/`에 있다. 사용자 녹음(`docs/m5/voice_user/`)과 합성 샘플(`docs/m5/samples/`)은 git에 넣지 않는다.

## 만든 것

| 부분 | 내용 |
|---|---|
| 녹음 칸 (`core/voice/VoiceSlot`) | 엔진 "부르르르", 휘슬 "쉬이이—", 블로오프 "피슈!", 배기 팝 "팡!", 변속음 "치키차". 각 칸에 탭, 지속음·원샷 구분, 원샷 최대 길이(블로오프 1.0초, 팝 0.4초, 변속 0.8초), 녹음 창 문구가 있다 |
| 녹음 점검 (`VoiceCheck`) | 48kHz 모노 2.5초. 앞뒤 무음(10ms 구간 −40dBFS 미만)을 자르고 판정한다: 안 들림, 작음(피크 −30dBFS 미만), 큼(1% 이상 클리핑), 짧음(지속음 0.25초, 원샷 0.05초 미만), 좋음. 좋은 녹음은 피크 −3dBFS로 맞추고 앞 2ms, 뒤 5ms를 페이드한다 |
| 재료 자르기 (`VoiceCut`, `VoiceKit`) | 엔진: 가장 고른 0.3초에서 30ms 토막 4개(Hann 창, 피크 1). 고른 구간은 가장 큰 구간의 30% 이상인 곳에서만 찾는다(무음이 가장 고르게 나오지 않게). 휘슬: 가장 고른 0.4초를 20ms 이음으로 이은 루프. `VoiceKit`은 켠 카드의 재료만 엔진 출력 속도로 바꿔 묶는다 |
| 엔진 토막 모드 (`EngineVoice`) | 실린더가 터질 때마다 압력 펄스 대신 토막을 차례로 재생한다. 재생 속도는 공회전 1.0, 레드라인 1.25이고, 동시에 6개 넘게 울리면 더 빨리 재생한다(최대 4배). 목소리 모드에서는 배기 빗살 반사를 ×0.3으로 줄이고 머플러를 9kHz 이상으로 열어 목소리가 뭉개지지 않게 했다 |
| 대체 규칙 (`SoundDirector`) | 휘슬(슈퍼차저는 윙 소리)은 루프로 바꾼다. 재생 속도는 휘슬 Hz÷3,000(윙은 Hz÷600)이고 0.6~2.0으로 제한한다. 블로오프는 한 번, 감속 팝은 0.85~1.15배로 흔들어 재생한다. 변속은 올릴 때 1.0, 내릴 때 0.9로 재생하고 DCT·수동 블립 팝을 대신한다 |
| 믹서 (`EngineMixer`) | 목소리 루프(레벨 0.35)와 원샷(레벨 0.6, 따로 8칸)을 배기 체인 뒤, 마스터 아래에서 섞는다. 볼륨, 상황 줄이기, 동승자 상한, 리미터가 그대로 걸린다 |
| 런타임 (`EngineRuntime.setVoices`) | 오디오 스레드에 다음 블록부터 넘긴다. 목소리 원샷 횟수를 센다(`voice shots {…}` 로그) |
| 상태 | `GarageState.voiceOn`(지갑마다 켠 탭)과 `files/voice/<칸>.wav`(앱 내부, 지갑과 무관). 녹음이 없는 카드는 켤 수 없고, 파일이 지워지면 꺼진 것으로 본다 |
| Android | `VoiceStore`(WAV 읽기·쓰기·지우기), `MicRecorder`(`AndroidMic`: VOICE_RECOGNITION, 안 되면 MIC. `FileMic`: adb로 넣은 WAV를 실시간 속도로), `VoiceHost`(켜짐·녹음을 엔진에 반영, adb). `EngineHost`는 녹음을 VoiceKit으로 만들어 넘기고, 녹음 창의 "들어보기"에서는 아직 저장하지 않은 녹음으로 미리 듣기를 한다. 엔진 목소리가 켜지면 녹음 팩 대신 합성 엔진으로 재생한다 |
| 화면 | 엔진·흡기·배기·변속기 탭 맨 끝의 목소리 카드(녹음 전·녹음됨·켜짐)와 액션 바(들어보기·다시 녹음·녹음하기/켜기/끄기), 녹음 창(안내 → 3·2·1 → 2.5초와 레벨 바 → 점검 → 들어보기 → "이걸로 쓸게요", 휘슬 다음 블로오프, 건너뛰기), 세팅 탭 "내 목소리 녹음 모두 지우기"(확인 창, 두 지갑의 카드 모두 끔), 개러지와 시동 화면의 "내 목소리" 표시. 녹음은 정차 중에만 하고, 녹음을 시작하면 엔진을 끈다. 마이크 권한은 첫 녹음 때만 묻는다 |
| adb | `voice --es action open|mic|load|on|off|clear`. 대역 WAV는 앱의 `files/voice_in/`에 run-as로 넣는다(디버그 빌드) |
| 도구 | `tools/voice_samples.py`(마이크 없이 점검할 합성 녹음 5개), `show.sh voice`, `show.sh voicecpu` |

테스트: 299개(M4 263 + 녹음 점검 7, 자르기 4, VoiceKit 4, 카드 상태 4, 토막 모드 4, 대체 규칙 6, 믹서 4, 런타임 3).

## 측정 (Pleos Connect Emulator v2.1.0, 디버그 빌드)

| 항목 | 결과 | 근거 |
|---|---|---|
| 내 목소리 파츠 10항목 | 13개 확인 모두 PASS. 목소리 카드가 탭 끝에 있음, 녹음 창과 안내, 대역 녹음의 점검 통과, 저장하면 카드가 켜지고 엔진 토막 4개, 나머지 넷도 좋음, 기어 N 잠금, 녹음 중 DO가 걸리면 그 녹음은 버림, 주행 중 목소리 변속 4번과 팝 7번, 모두 지우면 모든 카드가 꺼짐 | `m5/voice.txt` |
| 주행 규제 (M4 점검 다시) | 10/10 | `m5/ux.txt` |
| CPU (정차 레브 20초, 1코어 = 100) | 오디오 스레드는 다섯 카드를 켜도 4.6, 꺼도 4.6이다. 앱 전체는 42.4와 40.5로, 대부분 시동 화면의 바늘 다시 그리기다(M4 `cpu.txt`) | `m5/cpu.txt` |
| 카드가 모두 꺼졌을 때 | 소리가 샘플 하나까지 지금과 같다 | `EngineRuntimeVoiceTest.withNoVoicesTheSoundIsUnchanged` |
| 스쿨존·안내 줄이기 | 목소리 원샷도 마스터를 따른다(마스터 0.25에서 0.25배). 상황 줄이기는 마스터에 곱해진다. 그래서 에뮬레이터 dB 측정 대신 이 두 테스트로 확인했다 | `EngineMixerTest.voiceShotsFollowTheMaster`, `EngineRuntimeGainTest.theSituationGainScalesTheEngine` |

## 점검하면서 고친 것

- **adb 대역 파일:** 차는 앱을 사용자 10으로 실행한다. adb는 사용자 10의 외부 저장소에 쓸 수 없어서, 대역 WAV를 앱 내부 `files/voice_in/`에 run-as로 넣도록 바꿨다.
- **녹음 시작 버튼:** 주황 원 아래의 "녹음 시작" 글자는 누를 수 없었다. 원과 글자를 한 버튼으로 묶었다.
- **카드 설명:** 한국어는 음절 단위로 줄이 바뀌어서 "…만들어요"의 "요"만 다음 줄로 넘어갔다. 부품 카드처럼 한 줄에 들어가게 줄였다.
- **점검 스크립트:**
  - uiautomator는 따옴표가 든 문구를 작은따옴표 속성으로 쓴다. 그래서 `onscreen`이 녹음 안내를 읽지 못해, 다른 문구로 확인하도록 바꿨다.
  - 마이크 권한은 `pm grant --user 10`으로 줘야 한다.

## 사용자 목소리

아직 녹음하지 않았다. 다음 단계에서 사용자가 PC 마이크로 녹음한다(에뮬레이터 가상 마이크를 호스트 오디오 입력으로 켠다).

## 알려진 제한

- 실차에서 앱이 차량 마이크를 쓸 수 있는지는 확인하지 못했다(에뮬레이터는 PC 마이크). 사무국 문의 항목이다.
- 엔진 목소리는 합성 엔진으로만 재생한다. 녹음 팩이 깔린 엔진은 엔진 목소리를 켜 두는 동안 합성 엔진으로 바뀐다.
- adb 대역 파일(`voice mic|load`)은 run-as를 쓰므로 디버그 빌드에서만 된다.

## 다시 해 보는 명령

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
./gradlew testDebugUnitTest      # 299개
bash tools/show.sh voice         # docs/m5/voice.txt
bash tools/show.sh voicecpu      # docs/m5/cpu.txt
bash tools/show.sh ux            # docs/m4/ux.txt (이번 결과는 docs/m5/ux.txt로 옮김)
```
