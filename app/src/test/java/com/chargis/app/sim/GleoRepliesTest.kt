package com.chargis.app.sim

import com.chargis.app.domain.session.TripPhase
import com.chargis.app.ui.AcceptOutcome
import com.chargis.app.ui.GleoReplies
import kotlinx.coroutines.async
import com.chargis.app.ui.TripUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gleo AI 연동: 여행 단계마다 Gleo에 돌려줄 답이 화면과 같은 내용인지 S1을 돌려 확인한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class GleoRepliesTest {

    @Test
    fun idle_noTripFails() {
        val idle = TripUiState()
        assertFalse(GleoReplies.plan(idle).success)
        assertFalse(GleoReplies.status(idle).success)
        assertNull(GleoReplies.acceptableName(idle))
    }

    @Test
    fun s1_planStatusAndAcceptFollowTheTrip() = runTest {
        val h = harness()
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }

        // 추천을 안내한 뒤: 다음 충전소·목표 %를 답하고, "거기로 가자"를 받을 수 있다.
        val offered = h.controller.state.value
        val plan = GleoReplies.plan(offered)
        assertTrue(plan.success)
        assertTrue(plan.message, plan.message.contains("B 휴게소") && plan.message.contains("50%"))
        assertEquals("B 휴게소", plan.data["station"])
        assertEquals("B 휴게소", GleoReplies.acceptableName(offered))

        // Gleo의 "거기로 가자"는 경유지 추가가 끝난 뒤의 결과를 받는다.
        val outcome = async { h.controller.acceptForAssistant() }
        runCurrent()
        assertEquals(AcceptOutcome.ADDED, outcome.await())
        assertEquals(TripPhase.NavigatingToCharger, h.controller.state.value.phase)
        assertNull(GleoReplies.acceptableName(h.controller.state.value))
        assertEquals(AcceptOutcome.NOTHING_TO_ACCEPT, h.controller.acceptForAssistant())

        drive(h) { h.controller.state.value.phase == TripPhase.Arrived }
        // 도착했지만 충전 전: 동승자가 "언제 출발해?"라고 물어도 충전소·출발 예정 시각을 답한다.
        val arrived = h.controller.state.value
        val arrivedStatus = GleoReplies.status(arrived)
        assertTrue(arrivedStatus.message, arrivedStatus.message.startsWith("Arrived at B 휴게소"))
        assertEquals(arrived.stop!!.departAt, arrivedStatus.data["departAt"])
        h.controller.startCharging()
        runCurrent()
        h.sim.advance(60.0)
        runCurrent()

        // 충전 중: 지금 %, 목표 %, 남은 분, 출발 예정 시각을 답한다.
        val charging = h.controller.state.value
        val status = GleoReplies.status(charging)
        val stop = charging.stop!!
        assertTrue(status.message, status.message.startsWith("Charging at B 휴게소"))
        assertEquals(stop.minutesToTarget, status.data["minutesToTarget"])
        assertEquals(stop.departAt, status.data["departAt"])
        assertEquals(50, status.data["targetSoc"])
        // S1은 시간 제한 안에 목표에 닿으므로 출발까지 남은 분 = 목표까지 남은 분(동승자 쉬는 시간)
        assertEquals(stop.minutesToTarget, status.data["minutesUntilDepart"])
    }
}
