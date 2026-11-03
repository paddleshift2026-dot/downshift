# Downshift (다운시프트)

차 안에서만 들리는 내 엔진. 페달·차속·기어에 맞춰 가상 엔진음을 틀어 주는 Pleos Connect(AAOS) 앱이다. 내연기관·하이브리드·전기차 모두에서 동작하고, 엔진·흡기·배기·변속기를 게임처럼 골라 조합한다. 2026 현대자동차그룹 차량용 앱 공모전 출품작.

## 빌드와 설치

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
./gradlew installDebug
bash tools/probe.sh grant        # 차속 권한(CAR_SPEED) 등 런타임 권한, 앱은 user 10
```

앱을 열면 첫 탭이 사운드 랩이다. START를 0.9초 누르면 시동이 걸린다.

## 에뮬레이터 시연

에뮬레이터 안 Virtual Console의 페달을 앱이 직접 읽는다(30ms마다). 콘솔에는 물리 모델이 없어서, 앱이 페달로 속도를 계산해 콘솔에 되쓰고 계기판이 따라온다.

```bash
bash tools/sound.sh forward                      # PC에서 콘솔(8088)에 닿게
bash tools/sound.sh build engine=V8 intake=TURBO2 exhaust=SPORT trans=DCT pops=0.8
bash tools/sound.sh scenario v8_turbo           # 시동 → 레브 → 주행 → 감속 → 시동 끄기, 앱 안에서 녹음
bash tools/sound.sh latency                      # 콘솔 페달 → 소리 지연
bash tools/sound.sh cpu 26                       # 시나리오 중 앱 CPU
```

콘솔 페달 슬라이더나 G29 페달(콘솔 입력 모드 `http-g29`)로 직접 몰아도 된다. 차가 움직이는 동안 Pleos 홈 화면이 앱 화면을 가리지만 소리는 계속 난다.

## 엔진 음원

구매한 음원(GameDev Market Pro Licence)은 라이선스상 저장소에 넣지 않는다. 앱은 음원이 없으면 합성 엔진 팩으로 동작한다. 음원이 있으면:

```bash
python -I tools/pack_builder.py audio_test/pack/<엔진 폴더> app/src/main/assets/engines/i4 --engine I4 --estimate-rpm 4
```

`.dsa`는 이름을 씨앗으로 한 난독화 PCM이다(추출 방지용, DRM 아님). `audio_test/`와 `app/src/main/assets/engines/`는 `.gitignore`에 있다.

## 테스트

```bash
./gradlew testDebugUnitTest                       # 코어 133개 + 오프라인 렌더 app/build/renders/*.wav
python -I tools/pack_builder.py --selftest
python -I tools/audio_inventory.py --selftest
```

## 구조

- `core/` 순수 Kotlin (android.* 없음, JVM 테스트로 검증)
  - `powertrain/` 가상 파워트레인, 변속기, 부하 추정, 시연 차량 모델
  - `sound/` rpm 구간 크로스페이드, 흡기(터보·슈퍼차저), 팝콘, 디렉터
  - `dsp/` 바이쿼드, 배기 체인, 믹서, WAV 쓰기
  - `pack/` 매니페스트 규칙, `.dsa` 코덱, 합성 팩
  - `runtime/` 블록마다 파워트레인 → 디렉터 → 믹서
  - `input/` 입력 우선순위(콘솔 > 수동 > 추정), 차속 스무딩
- `adapters/` 안드로이드 쪽: AudioTrack 출력, 에셋 팩 로더, AAOS 차량 신호, 콘솔 주행, adb 명령(DemoBus)
- `ui/` 사운드 랩(개발용), M0 프로브
- `tools/` `sound.sh`, `probe.sh`, `pack_builder.py`, `audio_inventory.py`
- `docs/` `m0-findings.md`(신호·오디오 검증), `m1-results.md`(사운드 엔진 결과)
