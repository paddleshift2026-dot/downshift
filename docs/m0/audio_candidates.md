# M0 엔진 음원 후보 (2026-11-03 조사)

기준: ⓐ 엔진 종류 표기 ⓑ rpm 구간 루프 4단 이상(가속·감속 구분이면 더 좋음) ⓒ 배포되는 앱 안에 넣어도 되는 라이선스 ⓓ 합계 5만 원 이하.

| 팩 | 가격 | 엔진 표기 | 루프 구성 | 라이선스 핵심 | 추천 |
|---|---|---|---|---|---|
| [Japanese i4 Engine Sound](https://www.gamedevmarket.net/asset/i4-japanese-engine-sound-pack) (GameDev Market, slaczky) | $10 | 직렬 4기통 1종 | startup·engine_off·idle, idle_low·low·low_med·med·med_high·high·very_high 각각 on/off(가속/감속), maxRPM, aggressiveness on/off, 전부 실내 버전 포함. 44.1kHz 16bit WAV, 86MB | GameDev Market Pro Licence | 1차 직4 |
| [American Classic V8 Engine Sound](https://www.gamedevmarket.net/asset/v8-american-classic-engine-sound-pack) (slaczky) | $10 | V8 1종 | 위와 같음. 59MB. 판매자 표기 "Contain computer generated sounds" | Pro Licence | 1차 V8 (청음으로 실감 확인) |
| [V12 Engine Sounds](https://www.gamedevmarket.net/asset/v12-engine-sound-packs) (slaczky) | $20 | V12 2종 (British, Italian) | 위와 같음. 97.5MB | Pro Licence | 1차 V12 |
| [Exotic Cars Engine Sounds](https://www.gamedevmarket.net/asset/exotic-cars-engine-sounds) (slaczky) | $100 | V8 3·V10·V12 4·W16·Flat6 | 위와 같음 + 터보·변속 효과 샘플(FMOD). 717MB | Pro Licence | 예산 초과. 2차 엔진(V10·플랫6) 후보 |
| [Car Engines](https://magicsoundeffects.itch.io/car-engines) (itch.io, Magic Sound Effects) | $15~ | 도시형 8·스포츠 4·디젤·오프로드 (기통 표기 없음) | 5단 rpm 루프, 가속·감속·전환음 294개 | "personal use and one commercial project"와 "no limit on the number of projects" 문구가 충돌 | 제외 |

같은 판매자의 다른 단품(2차 후보): Japanese i6 $20, German V8 $40, V8 Engine Sounds $60, i4 Engine Sounds $40, Turbo Sounds $12, Rally Engine Sounds Vol.1 $30.

## 라이선스 (GameDev Market Pro Licence, [약관](https://www.gamedevmarket.net/terms-conditions/#pro-licence))

- "Media Product"는 소프트웨어·애플리케이션을 포함한다 → 차량용 앱에 상업적으로, 프로젝트 수 제한 없이 사용 가능.
- 금지: 음원이나 가공본을 Media Product 밖으로 판매·공유·재배포, **사용자가 앱에서 음원을 꺼내 앱 밖에서 쓰게 두는 것**, 로고·상표, NFT. 상품 페이지 표기 "Allows usage with AI? No".
- M1 과제: 출시 빌드에는 WAV를 그대로 넣지 않는다(앱 전용 형식으로 변환·난독화해 assets에 둔다). M0의 debug assets는 로컬 청음 전용이고 커밋하지 않는다.

## 구매 선택지

- 세 개 다 ($40, 부가세가 붙으면 5만 원을 조금 넘을 수 있음): 1차 엔진 3종이 같은 구조로 한 번에 갖춰진다.
- 예산 안 ($30, i4 + V12): V8은 Engine Simulator 렌더와 비교한 뒤 필요하면 $10 단품을 추가한다.
