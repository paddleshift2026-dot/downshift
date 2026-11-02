# Downshift M0 프로브 보고서
 
생성: 2026-11-02 10:33:06
 
## 신호
 
| 신호 | 출처 | 상태 | 마지막 값 | 횟수 | 메모 |
|---|---|---|---|---|---|
| 차속 | AAOS PERF_VEHICLE_SPEED | 수신 | 0.0 | 632 |  |
| 가속 페달 | AAOS ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE | 미지원 | - | 0 | 이 SDK에 상수 없음 |
| 브레이크 페달 깊이 | AAOS BRAKE_PEDAL_COMPRESSION_PERCENTAGE | 미지원 | - | 0 | 이 SDK에 상수 없음 |
| 엔진 rpm | AAOS ENGINE_RPM | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 기어 선택 | AAOS GEAR_SELECTION | 수신 | 4 | 1 |  |
| 현재 기어 | AAOS CURRENT_GEAR | 수신 | 4 | 1 |  |
| 배터리 순간 충방전 | AAOS EV_BATTERY_INSTANTANEOUS_CHARGE_RATE | 수신 | 0.0 | 101 |  |
| 누적 주행거리 | AAOS PERF_ODOMETER | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 방향지시등 | AAOS TURN_SIGNAL_STATE | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 좌석 점유 | AAOS SEAT_OCCUPANCY | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 연료 타입 | AAOS INFO_FUEL_TYPE | 수신 | 10 | 1 |  |
| 시동 상태 | AAOS IGNITION_STATE | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 주차 브레이크 | AAOS PARKING_BRAKE_ON | 수신 | false | 1 |  |
| 기어 | Pleos Vehicle SDK | 수신 | GEAR_PARK | 1 |  |
| 브레이크 페달 | Pleos Vehicle SDK | 수신 | false | 2 |  |
| 방향지시등 | Pleos Vehicle SDK | 수신 | OFF | 2 |  |
| 누적 주행거리 | Pleos Vehicle SDK | 변화 확인 | 1.5 | 25 |  |
| 이번 트립 거리 | Pleos Vehicle SDK | 대기 | - | 0 |  |
| 좌석 점유 | Pleos Vehicle SDK | 변화 확인 | [ROW_1_RIGHT] false | 14 |  |
| 제한속도 | NaviHelper | 수신 | 0 | 96 |  |
| 단속 카메라 | NaviHelper | 대기 | - | 0 |  |
| 주의구간(스쿨존) | NaviHelper | 대기 | - | 0 |  |
| 회전 안내 | NaviHelper | 대기 | - | 0 |  |
| 주행 정보 | NaviHelper | 대기 | - | 0 |  |
| 트립 정보 | NaviHelper | 대기 | - | 0 |  |
| 내비 상태 | NaviHelper | 대기 | - | 0 |  |
| 재생 중인 오디오 용도 | AudioPlaybackCallback | 변화 확인 | GAME | 8 |  |
| 안내음 포커스 | AudioTrack 내비 안내 용도 | 수신 | 요청 허용 | 1 |  |
| 가속 페달 (Virtual Console) | 127.0.0.1:8088/vehicle/controls | 변화 확인 | 0% | 6 |  |
 
## 오디오
 
- rate=48000 usage=14 burst=720 buffer=2308f(~48ms) perf=1 | 페달→출력 p50 66 / p95 77 / max 77 ms (n=6) | 콘솔 읽기 왕복 p50 6 / p95 16 ms (n=1668)
 
## 앱에서 보이는 AAOS 속성 (36개)
 
```
0x11100101 INFO_MAKE access=1 change=0 rate=0.0..0.0 areas=0
0x11100102 INFO_MODEL access=1 change=0 rate=0.0..0.0 areas=0
0x11200402 PARKING_BRAKE_ON access=1 change=1 rate=0.0..0.0 areas=0
0x11200403 PARKING_BRAKE_AUTO_APPLY access=1 change=1 rate=0.0..0.0 areas=0
0x11200405 FUEL_LEVEL_LOW access=1 change=1 rate=0.0..0.0 areas=0
0x11200f42 EV_CHARGE_SWITCH access=3 change=1 rate=0.0..0.0 areas=0
0x11400103 INFO_MODEL_YEAR access=1 change=0 rate=0.0..0.0 areas=0
0x11400108 INFO_FUEL_DOOR_LOCATION access=1 change=0 rate=0.0..0.0 areas=0
0x11400109 INFO_EV_PORT_LOCATION access=1 change=0 rate=0.0..0.0 areas=0
0x11400400 GEAR_SELECTION access=1 change=1 rate=0.0..0.0 areas=0
0x11400401 CURRENT_GEAR access=1 change=1 rate=0.0..0.0 areas=0
0x1140040c EV_BRAKE_REGENERATION_LEVEL access=3 change=1 rate=0.0..0.0 areas=0
0x1140040d EV_STOPPING_MODE access=3 change=1 rate=0.0..0.0 areas=0
0x11400f39 ELECTRONIC_TOLL_COLLECTION_CARD_TYPE access=1 change=1 rate=0.0..0.0 areas=0
0x11400f3a ELECTRONIC_TOLL_COLLECTION_CARD_STATUS access=1 change=1 rate=0.0..0.0 areas=0
0x11400f41 EV_CHARGE_STATE access=1 change=1 rate=0.0..0.0 areas=0
0x11400f43 EV_CHARGE_TIME_REMAINING access=1 change=2 rate=1.0..10.0 areas=0
0x11400f44 EV_REGENERATIVE_BRAKING_STATE access=1 change=1 rate=0.0..0.0 areas=0
0x11400f47 GENERAL_SAFETY_REGULATION_COMPLIANCE access=1 change=0 rate=0.0..0.0 areas=0
0x11410105 INFO_FUEL_TYPE access=1 change=0 rate=0.0..0.0 areas=0
0x11410107 INFO_EV_CONNECTOR_TYPE access=1 change=0 rate=0.0..0.0 areas=0
0x1141010b INFO_EXTERIOR_DIMENSIONS access=1 change=0 rate=0.0..0.0 areas=0
0x1141010c INFO_MULTI_EV_PORT_LOCATIONS access=1 change=0 rate=0.0..0.0 areas=0
0x11510306 WHEEL_TICK access=1 change=2 rate=1.0..10.0 areas=0
0x11600104 INFO_FUEL_CAPACITY access=1 change=0 rate=0.0..0.0 areas=0
0x11600106 INFO_EV_BATTERY_CAPACITY access=1 change=0 rate=0.0..0.0 areas=0
0x11600207 PERF_VEHICLE_SPEED access=1 change=2 rate=10.0..20.0 areas=0
0x11600208 PERF_VEHICLE_SPEED_DISPLAY access=1 change=2 rate=10.0..20.0 areas=0
0x11600307 FUEL_LEVEL access=1 change=2 rate=1.0..100.0 areas=0
0x11600308 RANGE_REMAINING access=3 change=2 rate=1.0..2.0 areas=0
0x11600309 EV_BATTERY_LEVEL access=1 change=2 rate=1.0..100.0 areas=0
0x1160030c EV_BATTERY_INSTANTANEOUS_CHARGE_RATE access=1 change=2 rate=1.0..10.0 areas=0
0x1160030d EV_CURRENT_BATTERY_CAPACITY access=1 change=1 rate=0.0..0.0 areas=0
0x11600f3f EV_CHARGE_CURRENT_DRAW_LIMIT access=3 change=1 rate=0.0..0.0 areas=0
0x11600f40 EV_CHARGE_PERCENT_LIMIT access=3 change=1 rate=0.0..0.0 areas=0
0x1540010a INFO_DRIVER_SEAT access=1 change=0 rate=0.0..0.0 areas=0
```
 
