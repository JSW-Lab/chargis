package com.chargis.app.sim

import com.chargis.app.data.fake.FakeNavi
import com.chargis.app.domain.model.ChargingStation
import com.chargis.app.domain.session.TripPhase
import com.chargis.app.port.NaviPort
import com.chargis.app.ui.DriveCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D1: 순정 내비에 목적지가 없을 때 앱에서만 안내하고, 나중에 목적지가 생기면 경유지를 넣는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppOnlyWaypointTest {

    /** 처음엔 내비 목적지가 없어 앱에서만 넣고, [hasDestination]이 true가 되면 내비에도 넣는 흉내. */
    private class NoDestinationNavi(private val demo: NaviPort) : NaviPort by demo {
        var hasDestination = false
        var synced = 0
        override var lastAddSkippedNavi = false
        override suspend fun addWaypoint(station: ChargingStation): Result<Unit> {
            lastAddSkippedNavi = !hasDestination
            return demo.addWaypoint(station)
        }
        override suspend fun syncSkippedWaypoint(station: ChargingStation): Result<Boolean> {
            if (!lastAddSkippedNavi || !hasDestination) return Result.success(false)
            lastAddSkippedNavi = false
            synced++
            return Result.success(true)
        }
    }

    @Test
    fun appOnlyBadge_thenWaypointAddedWhenNaviGetsDestination() = runTest {
        var navi: NoDestinationNavi? = null
        val h = harness(navi = { sim -> NoDestinationNavi(FakeNavi(sim)).also { navi = it } })
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        val card = h.controller.state.value.card as DriveCard.Navigating
        assertTrue(card.appOnly)

        // 목적지가 생기면 다음 확인(10초) 때 경유지를 넣고 알린다.
        navi!!.hasDestination = true
        advanceTimeBy(11_000)
        runCurrent()
        assertEquals(1, navi!!.synced)
        assertTrue(h.tts.spoken.contains("waypoint_synced:B 휴게소"))
        val after = h.controller.state.value.card
        assertFalse(after is DriveCard.Navigating && after.appOnly)
    }

    /** 내비에 넣은 경유지는 여행을 다시 시작할 때 뺀다(충전을 마치고 떠난 곳 포함)(경유지 두 자리가 쌓여 차지 않게). */
    @Test
    fun naviWaypointRemovedOnDepartAndRestart() = runTest {
        val removed = mutableListOf<String>()
        val h = harness(navi = { sim ->
            object : NaviPort by FakeNavi(sim) {
                private val inner = FakeNavi(sim)
                override suspend fun removeWaypoint(station: ChargingStation): Result<Unit> {
                    removed += station.id
                    return inner.removeWaypoint(station)
                }
            }
        })
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        // 수락한 채 다시 시작하면 뺀다.
        h.controller.restart()
        runCurrent()
        assertEquals(listOf("rest_b"), removed)

        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        drive(h) { h.controller.state.value.phase == TripPhase.Arrived }
        h.controller.startCharging()
        drive(h) { h.controller.state.value.phase == TripPhase.Done }
        h.controller.depart()
        runCurrent()
        // 출발할 때는 지우지 않는다(내비가 경로 선택 화면으로 앞에 나오므로). 다음에 다시 시작할 때 지운다.
        assertEquals(listOf("rest_b"), removed)
        h.controller.restart()
        runCurrent()
        assertEquals(listOf("rest_b", "rest_b"), removed)
    }
}
