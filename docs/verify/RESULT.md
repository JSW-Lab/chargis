# 3단계 검증 결과 (2026-11-09, Pleos Connect 에뮬레이터)

- 환경: Pleos Connect 에뮬레이터 v2.1.0(API 34, 2560×1440), Virtual Console HTTP API(`127.0.0.1:8088`), Gleo `ai.umos.ambientai`
- 빌드: release 0.2.0(versionCode 2, 서명 APK)으로 Gleo 응답을 확인하고, 시나리오 흐름은 같은 코드의 debug 빌드(DEV 배속)로 돌렸다.
- Gleo 액션은 음성 대신 `adb shell am broadcast -a com.chargis.app.<함수> -n com.chargis.app/.data.sdk.GleoActionReceiver`로 보냈다.

## 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| 3-3 Gleo 응답 대상(1-3) | 통과 | 응답 Intent가 `pkg=ai.umos.ambientai`로 Gleo에 닿아 `processAppActionResponse`까지 감(대기 상태라 Reject는 정상) |
| 3-3 오래된 답(1-1) | 통과 | 앱 종료 직후 PLAN은 저장된 답, ACCEPT는 "Chargis is not open". 10분 뒤(02:43) PLAN·STATUS 모두 "no trip information" |
| 3-4 권한 없는 호출 | 알려진 한계 | adb 셸에서 보낸 ACCEPT도 받아들여진다. Gleo 권한이 문서에 없어 receiver 권한을 걸지 않았다(PT Q&A에 설명) |
| 3-1a S1 흐름 | 통과 | 발권 → 추천(B 50%) → 수락 → 도착 → 충전(한도 60%) → 목표 도달 시 충전 스위치 끔 → 출발 |
| 3-1b D1 배지·자동 경유지 | 통과 | 내비 목적지 없음 → 도장 "앱에서 안내"(06). 내비에 속초해변 설정 → 10초 안에 "B 휴게소를 경유지로 넣었습니다", 내비 "경유지 1개 포함"(10), 도장 "경유지 등록"으로 복귀(12) |
| 3-1c D2 예열 | 코드 확인 | 실차 분기는 버튼 없이 안내 줄(`drive_precondition_suggest_manual`). S4 화면 확인은 하지 않음 |
| 3-2 충전 한도 복원(1-2) | 통과 | 충전 중 force-stop → 재실행 시 "차량 충전 한도 60%" 유지(17) → 충전 완료 후 출발 시 `charge limit set 100%`와 되돌림 안내 |
| 3-5 S3 E 휴게소 표(1-7) | 통과 | E 도착 화면 "앞으로의 충전소"에 F 휴게소(17%·100kW·3/6·대기 5분)(24) |
| 3-6 분할 화면 | 부분 확인 | Pleos 기본 레이아웃(왼쪽 차량 패널 + 오른쪽 앱 칸)에서 발권·주행·정차·충전 화면이 잘리지 않음. 앱 2개를 나란히 띄우는 분할은 시험하지 않음 |
| 3-7·3-8 음성 명령(STT)·TTS 자기 인식 | 미실시 | gRPC injectAudio 준비(JWT)가 필요해 다음으로 미룸 |
| 3-9 내비 위치 이동 | 미실시 | 내비 위치는 계속 수정구 시흥동(37.4127, 127.0947) |

## 시험 중 찾아 고친 문제

1. **여행을 다시 시작해도 순정 내비 경유지가 남음.** 데모 경로는 가상이라 내비가 경유지를 지나가지 않는다. S1(B)·S2(C)를 돌린 뒤 S3에서 수락하자 "경유지 두 곳이 이미 차 있음"으로 실패했고(21), 차가 배터리 0%까지 갔다(20). → 충전을 마치고 떠날 때와 여행을 다시 시작할 때 수락했던 경유지를 내비에서 뺀다(`TripController.depart`, `resetTrip`). 회귀 테스트 `AppOnlyWaypointTest.naviWaypointRemovedOnDepartAndRestart`.
2. **Gleo 수락이 실제로는 성공했는데 "실패"로 답함.** 수락 처리 뒤 음성 안내가 앞 안내("이어서 F 휴게소…")가 끝나길 기다려, Gleo 대기 5초를 넘겼다. → 안내 음성을 따로 내도록 바꿔, 같은 상황에서 34ms 만에 "성공(앱에서 안내)"으로 답함을 확인했다.

## 참고
- 발권 화면 맨 위 "실제 주행 · 속초해변" 표는 내비에 목적지가 있을 때만 나온다(정상).
- 에뮬레이터의 옛 debug 앱과 서명이 달라 release를 설치하려면 먼저 지워야 한다(`adb uninstall com.chargis.app`).
