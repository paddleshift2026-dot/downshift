# Downshift M0 프로브 보고서
 
생성: 2026-11-02 10:24:55
 
## 신호
 
| 신호 | 출처 | 상태 | 마지막 값 | 횟수 | 메모 |
|---|---|---|---|---|---|
| 차속 | AAOS PERF_VEHICLE_SPEED | 변화 확인 | 0.0 | 822 |  |
| 가속 페달 | AAOS ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE | 미지원 | - | 0 | 이 SDK에 상수 없음 |
| 브레이크 페달 깊이 | AAOS BRAKE_PEDAL_COMPRESSION_PERCENTAGE | 미지원 | - | 0 | 이 SDK에 상수 없음 |
| 엔진 rpm | AAOS ENGINE_RPM | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 기어 선택 | AAOS GEAR_SELECTION | 변화 확인 | 4 | 5 |  |
| 현재 기어 | AAOS CURRENT_GEAR | 수신 | 4 | 1 |  |
| 배터리 순간 충방전 | AAOS EV_BATTERY_INSTANTANEOUS_CHARGE_RATE | 수신 | 0.0 | 131 |  |
| 누적 주행거리 | AAOS PERF_ODOMETER | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 방향지시등 | AAOS TURN_SIGNAL_STATE | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 좌석 점유 | AAOS SEAT_OCCUPANCY | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 연료 타입 | AAOS INFO_FUEL_TYPE | 수신 | 10 | 1 |  |
| 시동 상태 | AAOS IGNITION_STATE | 미지원 | - | 0 | 앱에 안 보임 (차량 미제공 또는 권한 없음) |
| 주차 브레이크 | AAOS PARKING_BRAKE_ON | 수신 | false | 1 |  |
| 기어 | Pleos Vehicle SDK | 변화 확인 | GEAR_PARK | 4 |  |
| 브레이크 페달 | Pleos Vehicle SDK | 변화 확인 | false | 4 |  |
| 방향지시등 | Pleos Vehicle SDK | 변화 확인 | OFF | 5 |  |
| 제한속도 | NaviHelper | 수신 | 0 | 130 |  |
| 단속 카메라 | NaviHelper | 대기 | - | 0 |  |
| 주의구간(스쿨존) | NaviHelper | 대기 | - | 0 |  |
| 회전 안내 | NaviHelper | 변화 확인 | [TBTInfo(type=TURN_LEFT, distance=80.0, description=기업지원허브 방면), TBTInfo(type=TURN_RIGHT, distance=380.0, description=판교IC 방면), TBTInfo(type=TURN_AVOID_UNDER_R, distance=730.0, description=판교IC 방면), TB | 2 |  |
| 주행 정보 | NaviHelper | 변화 확인 | DrivingInfo(destination=DrivingState(name=서울역, duration=1680, distance=27000, remainDestinationChargingValue=73779, isChargingStation=false, isRecommendedChargingStation=false, maxChargingPower=0, tar | 2 |  |
| 트립 정보 | NaviHelper | 대기 | - | 0 |  |
| 내비 상태 | NaviHelper | 변화 확인 | ROUTE_DRIVE | 2 |  |
| 재생 중인 오디오 용도 | AudioPlaybackCallback | 변화 확인 | GAME | 56 |  |
| 안내음 포커스 | AudioTrack 내비 안내 용도 | 수신 | 요청 허용 | 4 |  |
| navi.onDestinationInfo |  | 변화 확인 | DestinationInfo(latitude=0.0, longitude=0.0, displayLatitude=null, displayLongitude=null, name=, address=, remainDistance=0, remainTime=0, tollFare=0, poiId=, poiSubId=null, poiProvider=) | 3 |  |
| navi.onRouteCancelled |  | 수신 |  | 1 |  |
| navi.onWayPointInfo |  | 수신 | [] | 1 |  |
| navi.onRouteStateInfo |  | 변화 확인 | RouteStateInfo(termsAgreement=true, naviMode=ROUTE_GUIDING, isRouting=false, routeOption=RECOMMENDED, remainDestinationChargingInt=73779, remainWaypointChargingInt=[0, 0], trafficStatus=TrafficStatus( | 2 |  |
| navi.onRouteStarted |  | 수신 | RouteStartInfo(duration=1680, distance=27000, tollFare=700, name=서울역, remainDestinationChargingValue=73779) | 1 |  |
| navi.onRouteEnded |  | 수신 |  | 1 |  |
| 포커스 실험 NONE | AudioManager | 수신 | 요청 안 함 | 2 |  |
| 포커스 실험 GAME | AudioManager | 수신 | 요청 허용 | 1 |  |
| 포커스 실험 MEDIA | AudioManager | 수신 | 요청 허용 | 1 |  |
| 포커스 실험 SONIFICATION | AudioManager | 수신 | 요청 허용 | 1 |  |
 
## 오디오
 
- rate=48000 usage=14 burst=720 buffer=2308f(~48ms) perf=1 | 페달→출력 p50 72 / p95 77 / max 78 ms (n=20)
 
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
 
