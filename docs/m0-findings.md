# M0 결과 — 기술 검증·음원 테스트

측정: 2026-11-03, Pleos Connect v2.1 에뮬레이터(Android 14, user 빌드, 앱은 user 10), 프로브 앱 `feature/m0-probe`. 원본은 `docs/m0/` (`m0_report.md` 최종 실행, `m0_report_run1.md` 첫 실행, `console_state.json`, `console_catalog.json`, `accel_*.txt`, `*_javap.txt`).

상태: 신호·오디오 검증 완료. **엔진 음원 청음은 구매 대기 중.**

## 결론 (M1에 넘기는 결정)

| 질문 | 결과 | 근거 | 결정 |
|---|---|---|---|
| 가속 페달 값을 앱이 읽을 수 있나 | **SDK로는 못 읽는다.** 에뮬레이터(Android 14)에 AAOS `ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE` 상수가 없고, Pleos Vehicle 2.3.2에도 가속 페달 API가 없다. Virtual Console에는 `accelerator_pct`(0~100)가 있고 SDV 토픽 `SDV_MOTION_ACCELERATOR_BASE_ACCELERATORBASE_ACTUAL_PEDAL`로 나가지만, 앱에서 보이는 AAOS 속성 36개 중 대응하는 것이 없다 | m0_report `가속 페달` 행(미지원: 이 SDK에 상수 없음), console_state `controls.topics.accelerator`, 페달 60%를 6초 유지하는 동안 앱 신호 변화 없음 | ① **시연:** 앱이 에뮬레이터 안 콘솔(`127.0.0.1:8088/vehicle/controls`)을 50ms마다 읽는다 (`ConsolePedalProbe`, 에뮬레이터 전용 어댑터) ② **실차:** 차속 변화율로 부하를 추정하고, 가속 페달 신호는 사무국에 공모전용 SDK 제공 여부를 문의 ③ **정차 레브:** 시연에선 콘솔 페달로 가능 |
| 콘솔 페달 → 앱 지연 | 63~132ms, 중앙값 약 81ms (기기 시계가 PC보다 1.12초 늦어 보정, 보정 오차 ±80ms). 콘솔 한 번 읽기 왕복 p50 8ms / p95 25ms | `accel_host.txt`·`accel_app.txt`, 보고서 오디오 줄 | 폴링 주기를 50→20ms로 줄인다 (M1) |
| 차속이 콘솔에서 앱까지 오나 | **온다.** `PERF_VEHICLE_SPEED` 변화 확인(최대 20Hz). `CAR_SPEED`는 런타임 권한이라 `pm grant --user 10` 필요 | 차속 행 1,537회 | 주행 rpm 계산, 제한속도 준수, 거리 보조 적분에 사용 |
| 기어·브레이크·방향지시등 | **모두 온다.** AAOS `GEAR_SELECTION`, Pleos gear·brake·turn signal 변화 확인. Pleos 기어 리스너는 deprecated("Google GEAR_SELECTION 속성을 쓰라") | m0_report | 기어는 AAOS `GEAR_SELECTION`, 브레이크·방향지시등은 Pleos |
| 좌석 점유 | **Pleos SDK로 온다** (AAOS `SEAT_OCCUPANCY`는 앱에 안 보임: `CONTROL_CAR_SEATS`는 부여 불가). 단 콜백 값이 한 박자 늦다: 조수석을 앉히면 false, 내리면 true가 오고 8초를 기다려도 그대로 | 조수석 토글 3회 | 콜백은 '바뀜' 신호로만 쓰고 실제 값은 `getCurrentSeatOccupancy`로 다시 읽는다 (M3에서 확인) |
| 주행거리 | **Pleos `registerDistanceDriven`이 온다** (콘솔 odometer +1.5km 감지, 권한 `pleos.car.permission.CAR_DRIVE`). 트립 거리 리스너는 미발생, AAOS `PERF_ODOMETER`는 앱에 안 보임 (`CAR_MILEAGE` 부여 불가) | m0_report | 거리 포인트 = Pleos 누적 거리의 차이, 보조로 차속 적분 |
| 연료 타입 | AAOS `INFO_FUEL_TYPE` = 10 (ELECTRIC). Pleos CarInfo에는 연료 타입이 없다 | m0_report | 전기차·내연기관 판별에 사용 |
| 엔진 rpm | 앱에 안 보임 (에뮬레이터 차량이 전기차, `CAR_ENGINE_DETAILED` 부여 불가) | m0_report | 내연기관 실차에서 다시 확인 (M1 범위 밖) |
| 배터리 순간 충방전 | 보이지만 에뮬레이터에선 계속 0 | m0_report | 전기차 부하 보조 신호 후보, 실차에서 확인 |
| 내비 이벤트 | 회전 안내(TBT)·주행 정보·내비 상태(ROUTE_DRIVE)·경로 시작/종료는 온다. 제한속도는 오지만 계속 0이고, 단속 카메라·주의구간·트립 정보는 미발생. 에뮬레이터 차량 위치가 경로를 따라 움직이지 않기 때문으로 보인다(판교 출발, 서울역 경로 27km) | m0_report `navi.*` 행 | M3 시연은 DemoBus로 제한속도·카메라·스쿨존 이벤트를 주입하고 기획서엔 "실차 내비 이벤트 연동"으로 적는다. 데이터 구조는 `sdk-signatures.md` (`CameraAlert.isOverSpeed`, `CautionPointType.SCHOOL_ZONE`) |
| 페달→출력 지연 (앱 내부) | p50 72 / p95 81ms. LOW_LATENCY 모드가 적용됐고(perf=1), burst 720프레임(15ms), 버퍼 2308프레임(약 48ms) | 보고서 오디오 줄 | 앱 내부만으로는 p95 ≤ 100ms 충족. 콘솔 지연을 더하면 약 150ms라 M1에서 버퍼를 2×burst(1440프레임, 30ms)로 줄여 100ms대를 노린다 |
| 엔진음을 켜도 음악이 계속 나오나 | 음악 앱으로는 확인 못 함 (에뮬레이터 음악 앱에 재생할 콘텐츠가 없음). 포커스 없이 재생한 엔진음은 `state:started`, `mutedState:none`으로 정상 출력 | `dumpsys audio` | **오디오 포커스를 요청하지 않는다** (음악 앱을 멈추게 하지 않도록). 음악 앱과 함께 재생하는 확인은 M3 |
| 안내음이 나올 때 | GAME·MEDIA·SONIFICATION 포커스 요청은 모두 허용됐고, 안내음(GAIN_TRANSIENT_MAY_DUCK)이 나와도 엔진음 쪽 포커스 변화는 없다(동시 재생). `AudioPlaybackCallback`에는 안내음 동안 `GAME,NAVIGATION`, 끝나면 `GAME`이 보인다 | m0_report, 추가 측정 | 재생 콜백으로 NAVIGATION·CALL 재생을 감지해 앱이 스스로 엔진음을 줄인다 |
| shell로 속성 목록 보기·값 주입 | 불가: `get-property-config requires non-user build` (Pleos 이미지는 user 빌드라 `inject-vhal-event`도 같은 제한) | `property_config.txt` | 앱 안 `propertyList`와 콘솔 상태로 대체 |
| 엔진 음원 출처·4번째 엔진 | **진행 중** — 후보 비교는 `docs/m0/audio_candidates.md`, 구매 대기 | — | 청음 후 결정 |

## 시연 입력 (M4 계획 변경 제안)

Virtual Console의 입력 모드가 `http-g29`다. 콘솔이 로지텍 G29 휠·페달 입력을 받도록 만들어져 있다는 뜻이다. 앱이 콘솔의 페달 값을 읽으므로, PT에서는 콘솔의 페달 슬라이더나 G29 페달을 밟으면 엔진음이 반응한다. 그래서 계획했던 자체 웹 페달은 필요 없을 가능성이 높다 (G29 연결은 M4에서 확인).

## 콘솔 HTTP 엔드포인트 (에뮬레이터 안 `0.0.0.0:8088`, PC에서는 `adb forward tcp:8088 tcp:8088`)

모든 응답이 `Connection: close`라 요청마다 새 연결이다 (M1에서 확인).

| 용도 | 요청 |
|---|---|
| 전체 상태 | `GET /vehicle/state` (21KB) |
| 페달·브레이크·속도·조향만 | `GET /vehicle/controls` (832B) |
| 가속 페달 | `POST /vehicle/controls?accelerator=0..100` |
| 차속·브레이크 | `POST /vehicle/controls?speed=KPH`(정수만, 소수는 400 `invalid_speed`), `?brake=0..100`. 쓰기 응답은 차량 상태 전체(약 13KB) |
| 기어 | `POST /vehicle/gear?gear=Park|Reverse|Neutral|Drive` |
| 방향지시등 | `POST /vehicle/turn-signal?mode=BlinkLeft|BlinkRight|Off` |
| 좌석 점유 | `POST /vehicle/seat?kind=occupied&area=driver|passenger|rear_left|rear_right&value=true|false` |
| 누적 주행거리 | `POST /vehicle/odometer?traveled_distance_km=KM` |
| 조작 가능한 항목 목록 | `GET /vehicle/catalog` |

## 다음 할 일

1. 음원 구매(사용자) → `audio_test/pack/`에 풀기 → 목록 작성·청음(Task 10) → 이 문서의 마지막 결론 행 채우기
2. 사무국 문의(사용자가 메일 발송): 가속 페달 신호를 공모전용 SDK·권한으로 제공하는지
3. M1 시작: 입력은 콘솔 페달(시연) + 차속, 출력은 포커스 없이 재생 + 안내음 자체 감소, 버퍼·폴링 조정
