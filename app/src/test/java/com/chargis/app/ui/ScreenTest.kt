package com.chargis.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.chargis.app.domain.engine.station
import com.chargis.app.domain.model.ChargerRule
import com.chargis.app.domain.model.OpenHours
import com.chargis.app.domain.model.Recommendation
import com.chargis.app.domain.model.StationKind
import com.chargis.app.domain.model.UserSettings
import com.chargis.app.domain.model.WaitSource
import com.chargis.app.ui.drive.DriveCardScreen
import com.chargis.app.ui.drive.TicketMeta
import com.chargis.app.ui.parked.ParkedScreen
import com.chargis.app.ui.theme.ChargisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Compose 화면 테스트. 에뮬레이터 없이 Robolectric으로 JVM에서 화면을 그려 글자와 버튼을 확인한다.
 * 차량 화면처럼 가로로 넓은 크기로 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class ScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // 경로에서 2.5km 벗어난 마트. 시간 제한은 확인하지 못했다.
    private val mart = station("mart", 46.0, maxKw = 200.0, detourKm = 2.5, kind = StationKind.COMMERCIAL, openHours = OpenHours(600, 1320))
        .let { it.copy(name = "I 마트", rule = ChargerRule(timeLimitMin = null, amountLimitKrw = null, timeLimitKnown = false)) }

    private val rec = Recommendation(
        station = mart,
        arrivalSocPercent = 37.0,
        targetSocPercent = 50.0,
        chargeMinutes = 9,
        waitMinutes = 0,
        timeLimitMin = null,
        willHitTimeLimit = false,
        socAtTimeLimitPercent = null,
        needsAnotherStop = false,
        reasonCodes = emptyList(),
        waitSource = WaitSource.LIVE    // 대기는 알고 있다(시간 제한 칸만 "정보 없음"이 되게)
    )

    /** 주행 중 화면 제한: 글자 수 상한에 맞춰 이름을 자르고, 항목 수 상한이 작으면 종류·우회 보조 줄을 뺀다. 버튼은 남는다. */
    @Test
    fun recommendCard_followsUxLimits() {
        compose.setContent {
            ChargisTheme(night = true) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalUxLimits provides com.chargis.app.port.UxLimits(restricted = true, maxStringLength = 3, maxContentItems = 8, activeBits = 0x1ff)
                ) {
                    DriveCardScreen(
                        card = DriveCard.Recommend(rec),
                        eta = "11:20",
                        remainingKm = 160.0,
                        meta = TicketMeta("S5-1100", "11:00"),
                        routeMarks = emptyList(),
                        progress = 0.2f,
                        onAccept = {},
                        onDecline = {}
                    )
                }
            }
        }
        compose.onNodeWithText("I…").assertIsDisplayed()
        compose.onAllNodesWithText("상업시설 · 왕복 5km 우회 · 10:00~22:00 운영").assertCountEquals(0)
        compose.onNodeWithText("경유지로 수락").assertIsDisplayed()
    }

    /** 추천 카드: 휴게소가 아닌 곳은 종류·왕복 우회·운영 시간을 덧붙이고, 확인하지 못한 시간 제한은 "정보 없음", 버튼은 "다음 충전소". */
    @Test
    fun recommendCard_showsOffRouteMetaAndUnknownLimit() {
        var accepted: String? = null
        compose.setContent {
            ChargisTheme(night = true) {
                DriveCardScreen(
                    card = DriveCard.Recommend(rec),
                    eta = "11:20",
                    remainingKm = 160.0,
                    meta = TicketMeta("S5-1100", "11:00"),
                    routeMarks = emptyList(),
                    progress = 0.2f,
                    onAccept = { accepted = it },
                    onDecline = {}
                )
            }
        }
        compose.onNodeWithText("I 마트").assertIsDisplayed()
        compose.onNodeWithText("상업시설 · 왕복 5km 우회 · 10:00~22:00 운영").assertIsDisplayed()
        compose.onNodeWithText("정보 없음").assertIsDisplayed()
        compose.onNodeWithText("다음 충전소").assertIsDisplayed()
        compose.onNodeWithText("경유지로 수락").performClick()
        assertEquals("mart", accepted)
    }

    /** 고속도로 휴게소에 제한 없음이 확인된 충전기면 덧붙이는 줄이 없고 제한 칸은 "없음". */
    @Test
    fun recommendCard_highwayRestStopKeepsPlainLook() {
        val rest = rec.copy(station = station("B", 46.0).copy(name = "B 휴게소"))
        compose.setContent {
            ChargisTheme(night = true) {
                DriveCardScreen(DriveCard.Recommend(rest), "11:20", 160.0, TicketMeta("S1-0900", "09:00"), emptyList(), 0.2f, {}, {})
            }
        }
        compose.onNodeWithText("B 휴게소").assertIsDisplayed()
        compose.onNodeWithText("없음").assertIsDisplayed()
        assertFalse(compose.onAllNodesWithTextExists("왕복"))
    }

    /** 설정: "휴게소·쉼터만"을 누르면 휴게소 밖 충전소를 끄는 설정으로 바뀐다. */
    @Test
    fun settings_restOnlyToggle() {
        var settings = UserSettings(onboarded = true)
        compose.setContent {
            ChargisTheme(night = true) {
                ParkedScreen(
                    state = TripUiState(settings = settings),
                    onStartScenario = {}, onStartCharging = {}, onStopCharging = {}, onDepart = {}, onRestart = {},
                    onSettings = { transform -> settings = transform(settings) }
                )
            }
        }
        // 첫 화면에는 요약만 있고, [설정]을 누르면 설정 화면이 열린다.
        compose.onNodeWithText("휴게소 밖 포함 · 차량 데이터 · 음성 명령 끔").assertIsDisplayed()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("충전소 범위").assertIsDisplayed()
        compose.onNodeWithText("휴게소·쉼터만").performClick()
        assertFalse(settings.includeNonRoadside)
    }

    /** 경유지 안내 중 배터리가 차가우면 예열을 권하고, [배터리 예열 켜기]를 누르면 알린다. */
    @Test
    fun navigatingCard_suggestsPreconditioning() {
        var pressed = false
        val card = DriveCard.Navigating(
            stationName = "B 휴게소", distanceKm = 30.0, targetSoc = 55.0, arrivalSoc = 30.0, chargeMinutes = 21,
            timeLimitMin = 40, coldExtraMinutes = 6, preconditioning = false, suggestPreconditioning = true
        )
        compose.setContent {
            ChargisTheme(night = true) {
                DriveCardScreen(card, "18:40", 120.0, TicketMeta("S4-1700", "17:00"), emptyList(), 0.4f, {}, {}, onPrecondition = { pressed = true })
            }
        }
        compose.onNodeWithText("배터리 예열을 켜면 충전 약 6분 단축").assertIsDisplayed()
        compose.onNodeWithText("배터리 예열 켜기").performClick()
        assertEquals(true, pressed)
    }

    /** 앱에서 예열을 켤 수 없는 차량이면 버튼 없이 차량 화면에서 켜라는 안내만 한다. */
    @Test
    fun navigatingCard_manualPreconditioningHasNoButton() {
        val card = DriveCard.Navigating(
            stationName = "B 휴게소", distanceKm = 30.0, targetSoc = 55.0, arrivalSoc = 30.0, chargeMinutes = 21,
            timeLimitMin = 40, coldExtraMinutes = 6, preconditioning = false, suggestPreconditioning = true,
            canStartPreconditioning = false
        )
        compose.setContent {
            ChargisTheme(night = true) {
                DriveCardScreen(card, "18:40", 120.0, TicketMeta("S4-1700", "17:00"), emptyList(), 0.4f, {}, {})
            }
        }
        compose.onNodeWithText("차량 화면에서 예열을 켜면 충전 약 6분 단축").assertIsDisplayed()
        compose.onAllNodesWithText("배터리 예열 켜기").assertCountEquals(0)
    }

    /** 충전 중: 차량이 출력을 주지 않으면 곡선에서 읽은 값이라 "추정 출력"으로 표시한다. */
    @Test
    fun charging_labelsPowerAsEstimateWithoutVehicleValue() {
        val curve = (0..100 step 10).map { CurveSample(it.toFloat(), 100f - it * 0.6f) }
        fun stop(kw: Double?) = StopInfo(
            stationName = "B 휴게소", currentSoc = 40.0, targetSoc = 50.0, minutesToTarget = 6, timeLimitMin = null,
            minutesToLimit = null, departAt = "10:20", chargePowerKw = kw, powerCurve = curve
        )
        var current by mutableStateOf(stop(null))
        compose.setContent {
            ChargisTheme(night = true) {
                ParkedScreen(
                    state = TripUiState(phase = com.chargis.app.domain.session.TripPhase.Charging, stop = current),
                    onStartScenario = {}, onStartCharging = {}, onStopCharging = {}, onDepart = {}, onRestart = {}
                )
            }
        }
        compose.onNodeWithText("추정 출력").assertIsDisplayed()
        // 출력 값이 있어도 차량이 준 값이 아니라(Pleos Vehicle SDK에는 충전 출력이 없다) 모델값이므로 늘 "추정"이다.
        current = stop(75.0)
        compose.onNodeWithText("추정 출력").assertIsDisplayed()
        compose.onNodeWithText("75").assertIsDisplayed()
    }

    /** 휴게소 도착: 예상 요금을 100원 단위로 보여 주고, 배터리를 직접 넣는 중이면 고치는 버튼이 있다. */
    @Test
    fun arrived_showsCostAndManualCorrector() {
        var delta = 0
        val stop = StopInfo(
            stationName = "B 휴게소", currentSoc = 37.0, targetSoc = 50.0, minutesToTarget = 9, timeLimitMin = 40,
            minutesToLimit = 40, departAt = "10:27", costKrw = 3862, manualSoc = true
        )
        compose.setContent {
            ChargisTheme(night = true) {
                ParkedScreen(
                    state = TripUiState(phase = com.chargis.app.domain.session.TripPhase.Arrived, stop = stop),
                    onStartScenario = {}, onStartCharging = {}, onStopCharging = {}, onDepart = {}, onRestart = {},
                    onCorrectSoc = { delta += it }
                )
            }
        }
        compose.onNodeWithText("예상 요금").assertIsDisplayed()
        compose.onNodeWithText("3,900").assertIsDisplayed()
        compose.onNodeWithText("−1").performClick()
        compose.onNodeWithText("+5").performClick()
        assertEquals(4, delta)
        // [맞음]은 값을 바꾸지 않고 확인만 한다(0).
        compose.onNodeWithText("맞음").performClick()
        assertEquals(4, delta)
    }

    /** 설정: 목적지 도착 잔량과 배터리 값(직접 입력)을 고른다. */
    @Test
    fun settings_destinationReserveAndManualSoc() {
        var settings = UserSettings(onboarded = true)
        compose.setContent {
            ChargisTheme(night = true) {
                ParkedScreen(
                    state = TripUiState(settings = settings),
                    onStartScenario = {}, onStartCharging = {}, onStopCharging = {}, onDepart = {}, onRestart = {},
                    onSettings = { transform -> settings = transform(settings) }
                )
            }
        }
        compose.onNodeWithText("설정").performClick()
        // 설정 화면은 두 칸으로 나눠 1280×800에서 스크롤 없이 모두 보인다.
        compose.onNodeWithText("30%").assertIsDisplayed().performClick()
        compose.onNodeWithText("직접 입력").assertIsDisplayed().performClick()
        assertEquals(30, settings.destinationSocPercent)
        assertEquals(true, settings.manualSoc)
        // [설정 완료]를 누르면 첫 화면으로 돌아가고 설정을 마친 것으로 기록한다.
        compose.onNodeWithText("설정 완료").performClick()
        assertEquals(true, settings.onboarded)
        compose.onNodeWithText("설정").assertIsDisplayed()
    }

    /** C: 실제 주행 추천 카드에 순정 내비 충전소와의 비교 한 줄이 붙는다. */
    @Test
    fun recommendCard_showsNaviCompareLine() {
        val navi = NaviCompare("가평휴게소 E-pit", 65.0, 13.6, 260.0, 6, same = false, chargisFasterMin = 7, naviTarget = 55.0, naviChargeMinutes = 20)
        compose.setContent {
            ChargisTheme(night = true) {
                DriveCardScreen(
                    card = DriveCard.Recommend(rec, navi = navi),
                    eta = "11:20", remainingKm = 160.0, meta = TicketMeta("LIVE-1430", "14:30"),
                    routeMarks = emptyList(), progress = 0.2f, onAccept = {}, onDecline = {}
                )
            }
        }
        compose.onNodeWithText("순정 내비: 가평휴게소 E-pit · 이곳이 7분 빨리 도착").assertIsDisplayed()
    }

    /** D: 영수증 요약에 일반 방식과의 비교가 한 줄로, 영수증에는 충전 횟수·제한·계산 기준이 찍힌다. */
    @Test
    fun receipt_showsTripCompare() {
        val receipt = TripReceipt(
            departAt = "09:00", startSoc = 60.0, arriveAt = "11:23", endSoc = 18.0, distanceKm = 210.0,
            charges = listOf(ChargeRecord("B 휴게소", 36.0, 50.0, 9, baselineMinutes = 30)),
            declinedNames = emptyList(),
            compare = TripCompare(fasterMin = 34, habitStops = 1, chargisStops = 1, habitLimitCuts = 1, chargisLimitCuts = 0, congestionAvoided = 0)
        )
        compose.setContent {
            ChargisTheme(night = true) {
                com.chargis.app.ui.parked.ReceiptScreen(
                    TripUiState(phase = com.chargis.app.domain.session.TripPhase.Finished, receipt = receipt, scenarioTitle = "S1"),
                    onRestart = {}, modifier = androidx.compose.ui.Modifier
                )
            }
        }
        compose.onNodeWithText("일반 방식보다 목적지에 34분 빨리 도착(같은 조건 계산)").assertIsDisplayed()
        compose.onNodeWithText("목적지 34분 빨리").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("시간 제한에 끊긴 충전 0번(일반 1번)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("출발 때 같은 조건으로 계산. 일반 방식: 배터리 30% 아래에서 첫 휴게소·쉼터에 들러 80%까지").performScrollTo().assertIsDisplayed()
    }

    // 글자 일부("왕복")가 들어간 노드가 하나라도 있는지
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExists(part: String) =
        onAllNodes(androidx.compose.ui.test.hasText(part, substring = true)).fetchSemanticsNodes().isNotEmpty()
}
