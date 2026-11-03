# Downshift (다운시프트)

차 안에서만 들리는 내 엔진. 페달·차속·기어에 맞춰 가상 엔진음을 틀어 주는 Pleos Connect(AAOS) 앱이다. 내연기관·하이브리드·전기차 모두에서 동작하고, 엔진·흡기·배기·변속기를 게임처럼 골라 조합한다. 2026 현대자동차그룹 차량용 앱 공모전 출품작.

## 빌드와 설치

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
./gradlew installDebug
bash tools/probe.sh grant        # 차속 권한(CAR_SPEED) 등 런타임 권한, 앱은 user 10
```

처음 열면 안전 안내가 나오고, 동의하면 개러지로 간다. 개러지에서 부품을 고르고 미리 듣고, 포인트로 해금해 장착한다. '이 빌드로 시동'은 시동 화면을 열고, 거기서 START를 0.9초 누르면 시동이 걸린다. 기어를 D로 넣으면 주행 화면, P로 돌아오면 주행 결산이 저절로 뜬다. 화면 위 작은 줄에서 사운드 랩·프로브(개발용)로 갈 수 있다.

## 개러지

- 탭: 엔진 / 흡기 / 배기 / 변속기 / 세팅. 부품 카드에는 가격과 상태만 있고 성능 수치는 없다
- 미리 듣기: 잠긴 부품도 정차 중이면 그 부품을 끼운 빌드로 짧은 각본을 들려준다(엔진이 꺼져 있으면 시동부터). 끝나면 장착 빌드로 돌아간다
- 해금은 안전 주행 포인트로 한다(평균 약 7.5P/km). 주행 결산 때 들어오고, 세팅 탭의 시연 버튼으로도 늘릴 수 있다
- 3D 무대는 미리 렌더한 24장 회전 컷이다. 끌어서 돌리고, 탭을 고르면 그 부품 쪽으로 돈다
- 상태는 바뀔 때마다 SharedPreferences에 JSON으로 저장된다(`GarageRepository` 하나만 씀). 세팅 탭에서 안전 안내를 다시 볼 수 있다

```bash
R=com.downshift.app/.adapters.demo.DemoReceiver
adb shell am broadcast -n $R --es cmd garage --es action reset              # 시연 상태 (3,000P)
adb shell am broadcast -n $R --es cmd garage --es action preview --es part v8
adb shell am broadcast -n $R --es cmd garage --es action unlock --es part v12
adb shell am broadcast -n $R --es cmd garage --es action equip --es part v12
adb shell am broadcast -n $R --es cmd garage --es action add --ei points 1000
adb shell am broadcast -n $R --es cmd garage --es action tab --es tab intake # engine|intake|exhaust|trans|tune
bash tools/garage.sh flow       # PT 흐름을 단계별 캡처와 녹음으로 (docs/m2/)
bash tools/garage.sh cpu        # 엔진 꺼짐·미리 듣기·공회전 중 앱 CPU
```

## 주행과 결산

- 시동 화면: rpm 게이지(빨간 구간은 엔진 레드라인 + 세팅 오프셋), 번호판, 점화 순서. START 0.9초로 걸고 STOP으로 끈다. 시연 버튼은 '가속 페달 누르기'(콘솔 가속 85%)와 '기어 D'
- 주행 화면: 이번 주행 포인트 링(주행을 시작할 때 정한 다음 해금 부품을 향해 참), 멈춘 엔진 렌더, 상황 칩, 화면 폭 음소거 버튼. rpm·속도·기어는 없고 값은 바뀔 때만 바로 바뀐다
- 포인트: 거리 5P/km, 제한속도 이하 거리 +1P/km, 스쿨존·단속 카메라·구간 단속 준수 +6P/곳, 회전 30m 전(IC·휴게소 100m)부터 같은 쪽 지시등 +0.5P/회. 감점 없음. 판정은 `core/trip/`
- 상황 볼륨: 안내 음성·통화·알람 −12dB, 스쿨존(100m 전부터)·조용 구간 −10dB, 동승자 볼륨 상한 40%, 음소거. 칩이 이유를 보여 준다
- 결산: 항목별 포인트, 거리·준수율·잔액, 다음 해금과 남은 km, 지금 해금할 수 있는 부품. 주행 중 10초마다 체크포인트를 남겨, 앱이 꺼져도 이어 가거나 한 번만 정산한다
- 신호: 방향지시등·주행계·좌석은 Pleos Vehicle SDK(시동·주행 중에만 켬), 제한속도·카메라·스쿨존·회전 안내는 NaviHelper, 안내 음성은 재생 콜백. 에뮬레이터 내비는 회전 안내만 주므로 나머지는 adb로 넣는다

```bash
adb shell am broadcast -n $R --es cmd drive --es event limit --ei kph 50       # 제한속도 (0 = 모름)
adb shell am broadcast -n $R --es cmd drive --es event turn --es dir left --ei m 120
adb shell am broadcast -n $R --es cmd drive --es event camera --ei kph 50 --ei m 300
adb shell am broadcast -n $R --es cmd drive --es event school --ei m 150
adb shell am broadcast -n $R --es cmd drive --es event guide                    # 내비 안내음 대역 (1.5초)
adb shell am broadcast -n $R --es cmd drive --es event lapse --ei value 20      # 시연 배속 (1 = 끔)
adb shell am broadcast -n $R --es cmd drive --es event route --es value cancel  # 멈춘 길안내 지우기
bash tools/drive.sh flow        # 시동 → D → 회전·스쿨존·안내음·동승자·음소거 → 도착 → 결산 → V12 (docs/m3/)
bash tools/drive.sh trip        # 포인트를 아는 각본 주행
bash tools/drive.sh persist     # 주행 중·정차 중 강제 종료
bash tools/drive.sh cpu         # 주행 CPU (Pleos SDK 켬/끔), 개러지 대기
```

콘솔 방향지시등·좌석·기어는 `curl -X POST 127.0.0.1:8088/vehicle/...`로 바꾼다(`docs/m0-findings.md`). 에뮬레이터에서는 Pleos 좌석 값이 콘솔과 반대로 읽혀서, 콘솔이 붙어 있을 때만 앱이 뒤집어 읽는다.

에셋은 `tools/assets_build.py`가 3D 렌더와 부품 사진(저장소 밖 원본 PNG)을 WebP로 바꿔 `app/src/main/assets/engine3d/`, `parts/`에 넣은 것이다.

## 에뮬레이터 시연

에뮬레이터 안 Virtual Console의 페달을 앱이 직접 읽는다(시동 중 30~40ms, 꺼져 있으면 200ms마다). 콘솔에는 물리 모델이 없어서, 앱이 페달로 속도를 계산해 콘솔에 되쓰고 계기판이 따라온다.

```bash
bash tools/sound.sh forward                      # PC에서 콘솔(8088)에 닿게
bash tools/sound.sh build engine=V8 intake=TURBO2 exhaust=SPORT trans=DCT pops=0.8
bash tools/sound.sh scenario v8_turbo           # 시동 → 레브 → 주행 → 감속 → 시동 끄기, 앱 안에서 녹음
bash tools/sound.sh latency                      # 콘솔 페달 → 소리 지연 (엔진을 켜고)
bash tools/sound.sh cpu 26                       # 시나리오 중 앱 CPU
```

콘솔 페달 슬라이더나 G29 페달(콘솔 입력 모드 `http-g29`)로 직접 몰아도 된다. 차가 움직이는 동안 Pleos 홈 화면이 앱 화면을 가리지만 소리는 계속 난다. `sound.sh build`는 개러지를 거치지 않고 엔진에 바로 빌드를 넣는다(개발용).

## 엔진 음원

기본 소리는 실시간 합성이다. 점화 순서대로 실린더마다 배기 펄스를 쏘고 배기 경로·꼬리관 반사·몸체 공명을 입혀서, 음원 없이 모든 엔진이 동작한다. 구매한 녹음 음원(GameDev Market Pro Licence)은 선택이고, 라이선스상 저장소에 넣지 않는다. 음원이 있으면:

```bash
python -I tools/pack_builder.py audio_test/pack/<엔진 폴더> app/src/main/assets/engines/i4 --engine I4 --estimate-rpm 4
```

`.dsa`는 이름을 씨앗으로 한 난독화 PCM이다(추출 방지용, DRM 아님). `audio_test/`와 `app/src/main/assets/engines/`는 `.gitignore`에 있다.

## 테스트

```bash
./gradlew testDebugUnitTest                       # 코어 233개 + 오프라인 렌더 app/build/renders/*.wav
python -I tools/pack_builder.py --selftest
python -I tools/assets_build.py --selftest
python -I tools/audio_inventory.py --selftest
```

## 구조

- `core/` 순수 Kotlin (android.* 없음, JVM 테스트로 검증)
  - `garage/` 부품 표(`Catalog`), 저장 상태(`GarageState`), 해금·장착·번호판·km 규칙(`Garage`), 소리 성향 막대, 미리 듣기 각본(`Audition`), 3D 회전 계산(`Turntable`), 저장 코덱
  - `trip/` 거리·제한속도(`DistanceMeter`), 스쿨존·카메라(`PlaceJudge`), 방향지시등(`TurnJudge`), 주행 기록·포인트·체크포인트(`TripMeter`, `PointRules`, `TripCodec`), 주행 링·결산 문구(`TripView`)
  - `situation/` 상황 인지 볼륨(`SoundSituation`), `flow/` 화면 흐름(`ScreenFlow`)
  - `powertrain/` 가상 파워트레인, 변속기, 부하 추정, 시연 차량 모델
  - `sound/` rpm 구간 크로스페이드, 흡기(터보·슈퍼차저), 팝콘, 디렉터
  - `dsp/` 바이쿼드, 배기 체인, 믹서, WAV 쓰기
  - `synth/` 실시간 배기 펄스 합성(`EngineVoice`), 점화 순서와 뱅크(`FiringPlans`)
  - `pack/` 매니페스트 규칙, `.dsa` 코덱, 실시간 합성 팩, 미리 구운 루프 합성 팩(비교용)
  - `runtime/` 블록마다 파워트레인 → 디렉터 → 믹서 (엔진이 꺼져 조용하면 믹서를 쉼)
  - `input/` 입력 우선순위(콘솔 > 수동 > 추정), 차속 스무딩
- `EngineHost` 앱 전체가 공유하는 엔진(차량 신호·콘솔·런타임·출력)과 미리 듣기 재생
- `DriveHost` 주행 기록·정산·상황 볼륨·화면 전환 (10Hz 주행 스레드)
- `adapters/` 안드로이드 쪽: AudioTrack 출력, 재생 감지·시연 안내음(`audio/`), 에셋 팩 로더, AAOS 차량 신호, Pleos 차량 신호·내비 이벤트(`pleos/`), 콘솔 주행, 개러지 저장(`store/`), adb 명령(DemoBus)
- `ui/` 화면 전환(`AppScreen`), 첫 실행(`onboard/`), 개러지(`garage/`: 화면, 부품 카드, 3D 무대, 소리 성향, 세팅 탭, 이미지 로더), 시동(`start/`), 주행·결산(`drive/`), 게이지, 사운드 랩(개발용), M0 프로브
- `tools/` `drive.sh`, `garage.sh`, `sound.sh`, `probe.sh`, `assets_build.py`, `pack_builder.py`, `audio_inventory.py`
- `docs/` `m0-findings.md`(신호·오디오 검증), `m1-results.md`(사운드 엔진), `m2-results.md`(개러지·경제), `m3-results.md`(주행·안전·결산)
