# 사용한 Pleos Connect SDK API (javap로 확인, 2026-11-02)

Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`, group `ai.pleos.playground`

## Vehicle 2.3.2 (`ai.pleos.playground.vehicle`)

진입점 `Vehicle(context)` → `initialize()` / `release()` / `getGear()`, `getDoor()`, `getDrivingMode()`, `getLight()`, `getWiper()`, `getFuel()`, `getOdometer()`, `getHVAC()` …

| 도메인 | 쓰는 API | 리스너 콜백 | 퍼미션 |
|---|---|---|---|
| Gear | `getCurrentGearSelectionState(onComplete, onFailed)`, `registerGearSelection(l)` | `GearSelectionListener.onSelected(VehicleGear)` | CAR_DRIVE / CAR_POWERTRAIN |
| Door | `getCurrentDoorPosition(DoorArea, onComplete, onFailed)`, `registerDoorPosition(area, l)` | `DoorPositionListener.onPositionUpdated(Int?)` | CAR_DOORS |
| DrivingMode | `getCruiseControlState(...)`, `registerCruiseControlState(l)` | `CruiseControlStateListener.onCruiseControlStateUpdated(CruiseControl)` | CAR_DRIVE / READ_ADAS_STATES |
| Light | `getCurrentLowBeamLightState(...)`, `registerLowBeamLightState(l)` | `LightStateListener.onLightStateUpdated(LightState)` | CAR_LIGHTS |
| Wiper | `getCurrentWiperState(WindowArea, ...)`, `registerWiperState(area, l)` | `WiperStateListener.onWiperStateUpdated(WiperState)` | CAR_WINDOWS / READ_WINDSHIELD_WIPERS |
| Fuel | `getFuelPercent(...)`, `registerFuelPercent(l)` | `FuelPercentListener.onFuelPercentUpdated(Int?)` | CAR_ENERGY |
| Odometer | `getRangeRemainingDistance(...)`, `registerRangeRemainingListener(l)`, (`getTripCurrentDrivingTime`) | `RangeRemainingListener.onRangeRemainingChanged(Float?)` | CAR_MILEAGE / CAR_DRIVE |
| HVAC | `setHvacRecirculation(SeatArea, Boolean, onComplete, onFailed)`, `setHvacFanSpeed(SeatArea, Int, ...)` | — | CAR_CLIMATE + CONTROL_CAR_CLIMATE |

- 모든 get/set/check는 비동기: `onComplete`/`onFailed` 람다. 리스너는 `VehicleListener.onFailed(Exception)`도 구현.
- 상수: `VehicleGear.GEAR_PARK/GEAR_DRIVE/…`, `CruiseControl.OTHER/ENABLED/ACTIVATED/USER_OVERRIDE/SUSPENDED/FORCED_DEACTIVATION_WARNING`, `LightState.OFF/ON/DAYTIME_RUNNING`, `WiperState.OTHER/OFF/ON/SERVICE`, `DoorValue.ROW_1_LEFT…`, `WindowValue.FRONT_WINDSHIELD…`, `SeatValue.ROW_1_LEFT…`, `FanDirectionType`, `TemperatureUnit.CELSIUS`.
- 속도는 SDK에서 제거됨(2.3.2 릴리즈 노트) → AAOS `CarPropertyManager.PERF_VEHICLE_SPEED` (MVP 미사용).

## NaviHelper 3.0.5 (`ai.pleos.playground.navi.helper`)

`NaviHelper(context)`, `initialize()` / `initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit)`, `addListener(NaviHelperEventListener)`, `removeListener`, `release()`
`requestRoute(RouteInfo)`, `requestRouteWithWaypoints(RouteWithWaypointsRequest)`, `addWaypoint(RequestWaypointInfo)`, `removeWaypoint(WaypointIndex)`, `cancelRoute()`, `changeRouteOption(RouteOption)`, `requestReRoute()`, `getDestinationInfo()`, `getCurrentLocationInfo()`, `getRouteStateInfo()`, `getWaypointInfo()`, `getTBTInfo()`, `getChargerOperatorInfo()`, `requestNavigationScreen(NavigationScreenRequest)`, `requestTrip(TripRequest)`

데이터(`ai.pleos.playground.navi.data`): `RouteInfo(latitude, longitude, displayLatitude?, displayLongitude?, poiName, poiId, address, poiSubId, routeOption=RECOMMENDED, poiProvider, isMobileRequest)`, `RequestWaypointInfo(latitude, longitude, poiName, waypointIndex, poiId, address, poiSubId, poiProvider)`, `DestinationInfo(latitude, longitude, …, name, address, remainDistance:Int, remainTime:Int, tollFare, poiId, poiSubId, poiProvider)`, `DestinationArrivedInfo(duration, distance, name, address)`, `WaypointArrivedInfo(duration, distance, index, name, address)`, `RouteStartInfo(duration, distance, tollFare, name, remainDestinationChargingValue)`, `CurrentLocationInfo(latitude, longitude, heading, address, routeId, currentRouteIndex)`, `TripInfo(distance, eta, estimatedSocAtDest, energyConsumption, summary)`.
상수: `WaypointIndex.FIRST/SECOND/ALL`, `RouteOption.RECOMMENDED/SHORT_DISTANCE/SHORT_TIME/FREE/HIGHWAY/COMFORTABLE/SCHOOL_ZONE`, `NavigationStatus.INACTIVE/INITIALIZING/FREE_DRIVE/ROUTE_DRIVE`, `NaviErrorCode.CAUSE_LOW_BATTERY/CAUSE_SEARCH_ROUTE_OVER_DISTANCE/CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE/CAUSE_SEARCH_ROUTE`.
리스너 콜백 25종: onRouteStarted, onRoutingCompleted, onRouteCancelled, onReRouteEnded, onRouteOptionChanged, onDestinationChanged, onDestinationArrived, onWaypointChanged, onWayPointArrived, onBookmarkInfo, onBookMarkChanged, onRecentDestinationInfo, onCurrentLocationInfo, onDestinationInfo, onWayPointInfo, onRouteStateInfo, onTBTInfo, onChargerOperatorInfo, onTripInfo, onNavigationStatus, onDrivingInfo, onCurrentRoadSpeedLimit(Int), onCameraAlert, onCautionPointInfo, onError.
퍼미션: NAVI_ROUTE(get*), NAVI_ROUTE_SEARCH(request/add/remove/cancel), NAVI_CUSTOM_MAP(requestNavigationScreen).

## TextToSpeech 3.3.1 (`ai.pleos.playground.tts`)

`TextToSpeech(context, Mode.ON_DEVICE|SERVER|HYBRID)`, `initialize(OnInitializeListener{onSuccess, onFailure(ErrorType)})`, `addEventListener(EventListener{onReady,onStart,onDone,onStop,onUpdatedRms(Double),onError(ErrorType)})`, `setLocale(Locale)`, `speak(text, isFinal=true)`, `stop()`, `release()`, 서버 모드만 `registerApp(clientId, clientSecret, OnServerConnectionListener)`. 퍼미션 `pleos.car.permission.TTS_SERVICE`.

## SpeechToText 3.3.1 (`ai.pleos.playground.stt`)

`SpeechToText(context, Mode.ON_DEVICE|SERVER|HYBRID)`, `initialize(OnInitializeListener)`, `addListener(ResultListener{onReady,onStartedRecognition,onUpdated(text, completed),onUpdatedEpdData,onUpdatedRms,onEndedRecognition,onError(ErrorType)})`, `setLocale`, `request()`(차량 마이크로 인식 시작), `stop()`, `sendAudio(ByteArray)`/`completeAudioSend()`(PCM 직접 입력), `release()`. 퍼미션 `pleos.car.permission.STT_SERVICE`.

## AAOS (`android.car`, `useLibrary("android.car")`)

`Car.createCar(context)`, `getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager`, `currentCarUxRestrictions.isRequiresDistractionOptimization`, `registerListener { r -> … }`. 액티비티 메타데이터 `distractionOptimized=true`.

---

## Downshift에서 추가로 확인한 것 (javap, 2026-11-03)

원본 출력: `docs/m0/vehicle_sdk_javap.txt`, `vehicle_data_javap.txt`, `navihelper_javap.txt`, `navi_data_javap.txt`. SDK는 본체와 데이터 모듈로 나뉜다: Vehicle 2.3.2 + vehicle-data 1.1.11 + vehicle-aidl 1.1.1, NaviHelper 3.0.5 + navi-data 3.0.4 + navi-aidl 3.0.1.

### Vehicle 2.3.2

| 도메인 | 쓰는 API | 리스너 콜백 | 쓸 곳 |
|---|---|---|---|
| Gear | `registerGearSelection(l)` — **deprecated: "use the Google GEAR_SELECTION property"** | `GearSelectionListener.onSelected(VehicleGear)` | P↔D 화면 전환 (AAOS `GEAR_SELECTION`으로 옮길 것) |
| Brake | `getCurrentBrakePedalStatus(onComplete, onFailed)`, `registerBrakePedalStatus(l)`, `checkBrakePedalStatusCapabilities(...)` | `BrakePedalStatusListener.onBrakePedalStatusUpdated(Boolean?)` | 감속 판단 |
| Odometer | `getCurrentDistanceDriven`, `registerDistanceDriven(DistanceDrivenListener)`, `getTripCurrentDrivingDistance` / `registerTripCurrentDrivingDistance(l)` (+ Instantaneous·Cumulative·SinceRefill), `getTripCurrentDrivingTime` 등, 각 `check*Capabilities` | `DistanceDrivenListener.onDistanceDrivenUpdated(Float?)` | 거리 포인트 |
| Seat | `checkSeatOccupancyCapabilities(onComplete: (List<SeatArea>), onFailed)`, `getCurrentSeatOccupancy(SeatArea, ...)`, `registerSeatOccupancy(SeatArea, l)`; 안전벨트 `registerSeatBeltBuckledState(SeatArea, l)` | `SeatOccupancyListener.onSeatOccupancyUpdated(Boolean?)` | 동승자 볼륨 (안전벨트는 점수 후보) |
| CarInfo | `getVINInfo`, `getCRNInfo`, `getCapabilityInfo` — **연료 타입 없음** | — | 연료 타입은 AAOS `INFO_FUEL_TYPE` (값은 vehicle-data `VehicleFuelTypeValue`: UNLEADED, DIESEL_1/2, LPG, ELECTRIC, HYDROGEN …) |

### NaviHelper 3.0.5 (`NaviHelperEventListener`는 Java default 메서드만 가진 인터페이스 → 필요한 것만 override, 프로브는 동적 프록시)

| 콜백 | 데이터 (navi-data 3.0.4) | 쓸 곳 |
|---|---|---|
| `onCurrentRoadSpeedLimit(Int)` | km/h | 제한속도 준수 포인트 |
| `onCameraAlert(List<CameraAlert>)` | `CameraAlert(id, type: CameraType, status, speedLimit, remainDistance, sectionAvrSpeed, isOverSpeed, isOverSectionAvrSpeed)`. `CameraType`: SPEED_CAMERA_FIX/MOV/SECTION_START/SECTION_END/REAR/TRAFFIC_LIGHT, SECTION_SPEED_ALERT, VARIABLE_SPEED_CAMERA, **SPEED_CAMERA_SCHOOL_ZONE**, SPEED_CAMERA_SCHOOL_ZONE_TRAFFIC_LIGHT | 카메라 구간 준수(`isOverSpeed` 그대로 사용) |
| `onCautionPointInfo(List<CautionPoint>)` | `CautionPoint(id, type: CautionPointType, remainDistance)`. `CautionPointType`: **SCHOOL_ZONE**, SPEED_BUMP, ACCIDENT_PRONE_AREA, RAILROAD_CROSSING, TRAFFIC_LIGHT_VIOLATION_ENFORCEMENT 외 다수 | 스쿨존 조용 모드·스쿨존 포인트 |
| `onTBTInfo(List<TBTInfo>)` | `TBTInfo(type: TurnType, distance: Double, description)` | 방향지시등 포인트 (회전 지점 30m 전) |
| `onDrivingInfo(DrivingInfo)` | `DrivingInfo(destination: DrivingState, waypoints)`, `DrivingState(name, duration, distance, …충전 정보)` | (미사용) |
| `onTripInfo(TripInfo)` | `TripInfo(distance, eta, estimatedSocAtDest, energyConsumption, summary)` | (미사용) |
