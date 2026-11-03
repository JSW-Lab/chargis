package com.chargis.app.sim

import com.chargis.app.domain.model.GeoPoint
import com.chargis.app.domain.session.TripPhase
import com.chargis.app.port.CarSignalsPort
import com.chargis.app.port.LiveRoutePort
import com.chargis.app.port.NaviDestination
import com.chargis.app.ui.DriveCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실제 주행: 순정 내비 목적지로 발권하고, 내비가 알려 주는 남은 거리·위치와 차량 배터리를 따라간다.
 * 내비 대신 남은 거리를 직접 줄여 가며 S1과 같은 길(서울→속초)을 달려 본다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTripTest {

    /** 순정 내비 흉내: 목적지(속초)와 남은 거리. 위치는 남은 거리에 맞춰 직선 위를 옮긴다. */
    private class FakeLiveNavi(var remainingKm: Double? = 210.0) : LiveRoutePort {
        val start = GeoPoint(37.55, 127.05)
        val dest = GeoPoint(38.2, 128.59)
        override suspend fun destination() = remainingKm?.let { NaviDestination("속초시청", dest, it, (it / 100.0 * 60).toInt()) }
        override suspend fun currentLocation(): GeoPoint {
            val t = 1 - (remainingKm ?: 0.0) / 210.0
            return GeoPoint(start.lat + (dest.lat - start.lat) * t, start.lon + (dest.lon - start.lon) * t)
        }
    }

    private class FakeCar(soc: Int) : CarSignalsPort {
        override val available = MutableStateFlow(true)
        override val socPercent = MutableStateFlow<Int?>(soc)
        override val parked = MutableStateFlow<Boolean?>(false)
        override val chargeSwitchOn = MutableStateFlow<Boolean?>(false)
        override val uxRestricted = MutableStateFlow(false)
    }

    // 실제 시간 [ms]만큼 흐르게 한다: 시뮬레이터 시계(실제 주행은 1배속)와 내비 값을 받는 작업을 함께 돌린다.
    private fun TestScope.wait(h: Harness, ms: Long) {
        h.sim.advance(ms / 1000.0)
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun liveTicketAppearsOnlyWithNaviDestination() = runTest {
        val navi = FakeLiveNavi(remainingKm = null)
        val h = harness(car = FakeCar(50), liveRoute = navi)
        assertFalse(h.controller.state.value.scenarios.any { it.id == "LIVE" })

        navi.remainingKm = 210.0
        advanceTimeBy(11_000)
        runCurrent()
        val live = h.controller.state.value.scenarios.first()
        assertEquals("LIVE", live.id)
        assertEquals("실제 주행 · 속초시청", live.title)
        assertEquals(210.0, live.distanceKm, 1e-9)
        assertEquals(50.0, live.startSoc, 1e-9)
        assertEquals("14:30", live.departAt)
    }

    @Test
    fun noDestination_asksToSetOne() = runTest {
        val h = harness(liveRoute = FakeLiveNavi(remainingKm = null))
        h.controller.startScenario("LIVE")
        runCurrent()
        assertEquals(TripPhase.Idle, h.controller.state.value.phase)
        assertTrue(h.tts.spoken.contains("live_no_destination"))
    }

    @Test
    fun liveTrip_followsNaviDistanceAndCarBattery_thenRecommendsAndArrives() = runTest {
        val navi = FakeLiveNavi()
        val car = FakeCar(45)
        val h = harness(car = car, liveRoute = navi)
        h.controller.startScenario("LIVE")
        runCurrent()

        val started = h.controller.state.value
        assertEquals(TripPhase.Driving, started.phase)
        assertEquals("실제 주행 · 속초시청", started.scenarioTitle)
        assertEquals("14:30", started.departAt)
        // 번들 충전소 중 직선 경로 근처의 A~D 휴게소를 경로 위로 내려 쓴다.
        assertTrue(h.sim.scenario.stations.map { it.id }.containsAll(listOf("rest_a", "rest_b", "rest_c", "rest_d")))

        // 시뮬레이터는 스스로 달리지 않는다: 시간이 흘러도 내비 남은 거리가 그대로면 제자리다.
        wait(h, 60_000)
        assertEquals(0.0, h.sim.state.value.traveledKm, 1e-9)

        // 내비 남은 거리를 줄이고 차량 배터리를 낮추면 그만큼 달린 것으로 따라간다.
        var phaseSeen = h.controller.state.value.phase
        var km = 210.0
        while (km > 120 && phaseSeen != TripPhase.AwaitingDecision) {
            km -= 2.0
            navi.remainingKm = km
            car.socPercent.value = (45 - (210 - km) * 0.2).toInt()
            wait(h, 3_000)
            phaseSeen = h.controller.state.value.phase
        }
        assertEquals(TripPhase.AwaitingDecision, phaseSeen)
        assertEquals(210.0 - km, h.sim.state.value.traveledKm, 0.01)
        assertEquals(car.socPercent.value!!.toDouble(), h.controller.state.value.socPercent, 1e-9)
        val rec = (h.controller.state.value.card as DriveCard.Recommend).rec
        // 구간 띠에는 번들 예비 충전소 전부가 아니라 지금 후보로 고른 곳만 그린다.
        val marks = h.controller.state.value.routeMarks.map { it.id }
        assertTrue(marks.toString(), rec.station.id in marks && marks.size <= 1 + h.controller.state.value.candidates.size.coerceAtLeast(2))

        h.controller.accept(rec.station.id)
        runCurrent()
        assertEquals(TripPhase.NavigatingToCharger, h.controller.state.value.phase)

        // 충전소 근처까지 와서 기어를 P로 바꾸면 도착한 것으로 본다.
        val wp = h.sim.kmToWaypoint()!!
        navi.remainingKm = km - (wp - 1.0)
        wait(h, 3_000)
        car.parked.value = true
        runCurrent()
        wait(h, 1_000)
        assertEquals(TripPhase.Arrived, h.controller.state.value.phase)
    }

    @Test
    fun liveTrip_arrivesWhenNaviDistanceRunsOut() = runTest {
        val navi = FakeLiveNavi(remainingKm = 30.0)
        val h = harness(car = FakeCar(80), liveRoute = navi)
        h.controller.startScenario("LIVE")
        runCurrent()
        wait(h, 3_000)
        navi.remainingKm = 0.05
        wait(h, 3_000)
        wait(h, 1_000)
        assertEquals(TripPhase.Finished, h.controller.state.value.phase)
    }
}
