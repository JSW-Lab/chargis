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
}
