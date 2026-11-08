package com.chargis.app.sim

import com.chargis.app.domain.engine.TurnAngles
import com.chargis.app.domain.model.DataSource
import com.chargis.app.domain.model.GeoPoint
import com.chargis.app.domain.model.Maneuver
import com.chargis.app.domain.model.TurnHint
import com.chargis.app.domain.session.TripPhase
import com.chargis.app.port.CarSignalsPort
import com.chargis.app.port.LiveRoutePort
import com.chargis.app.port.NaviDestination
import com.chargis.app.port.TurnGuidePort
import com.chargis.app.ui.DriveCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 제안 A: 경유지로 가는 카드의 턴 안내 띠와 [지도에서 보기]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnGuideTest {

    /** 순정 내비 턴 안내 흉내. requestTurn 횟수와 경로 화면 요청을 센다. */
    private class FakeGuide(var overview: Result<Unit> = Result.success(Unit)) : TurnGuidePort {
        override val turn = MutableStateFlow<TurnHint?>(null)
        var requests = 0
        var overviews = 0
        override fun requestTurn() { requests++ }
        override suspend fun showRouteOverview(): Result<Unit> { overviews++; return overview }
    }

    private class FakeLiveNavi(var remainingKm: Double = 210.0) : LiveRoutePort {
        val start = GeoPoint(37.55, 127.05)
        val dest = GeoPoint(38.2, 128.59)
        override suspend fun destination() = NaviDestination("속초시청", dest, remainingKm, (remainingKm / 100.0 * 60).toInt())
        override suspend fun currentLocation(): GeoPoint {
            val t = 1 - remainingKm / 210.0
            return GeoPoint(start.lat + (dest.lat - start.lat) * t, start.lon + (dest.lon - start.lon) * t)
        }
    }

    private class FakeCar(soc: Int) : CarSignalsPort {
        override val available = MutableStateFlow(true)
        override val socPercent = MutableStateFlow<Int?>(soc)
        override val parked = MutableStateFlow<Boolean?>(false)
        override val chargeSwitchOn = MutableStateFlow<Boolean?>(false)
        override val uxLimits = MutableStateFlow(com.chargis.app.port.UxLimits.NONE)
    }

    private fun TestScope.wait(h: Harness, ms: Long) {
        h.sim.advance(ms / 1000.0)
        advanceTimeBy(ms)
        runCurrent()
    }

    /** 데모 S1: B 휴게소를 수락하면 진입 3km 안에서 "휴게소 진입" 띠가 생기고, 달리면 거리가 줄어든다. 순정 내비가 없으면 지도 버튼은 없다. */
    @Test
    fun demo_navigatingCardShowsServiceAreaTurn() = runTest {
        val h = harness()
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        // 멀리 있을 때는 띠가 아래 "N km 남음"과 같은 값이라 띄우지 않는다.
        assertNull((h.controller.state.value.card as DriveCard.Navigating).turn)
        drive(h) { (h.controller.state.value.card as? DriveCard.Navigating)?.turn != null }
        val first = h.controller.state.value.card as DriveCard.Navigating
        val t1 = first.turn!!
        assertTrue(t1.distanceKm <= 3.0)
        assertEquals(Maneuver.SERVICE_AREA, t1.maneuver)
        assertEquals(DataSource.SIMULATED, t1.source)
        assertEquals(first.distanceKm, t1.distanceKm, 1e-9)
        assertFalse(first.canShowMap)

        h.sim.advance(30.0)
        runCurrent()
        val t2 = (h.controller.state.value.card as DriveCard.Navigating).turn!!
        assertTrue("${t2.distanceKm} < ${t1.distanceKm}", t2.distanceKm < t1.distanceKm)
    }

    /** 데모에서는 순정 내비 턴 값이 와도 쓰지 않는다(내비가 가상 경로를 안내하지 않으므로). 지도 버튼은 순정 내비가 있으면 보인다. */
    @Test
    fun demo_ignoresNaviTurns_butOffersMap() = runTest {
        val guide = FakeGuide()
        val h = harness(turnGuide = guide)
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        drive(h) { (h.controller.state.value.card as? DriveCard.Navigating)?.turn != null }
        guide.turn.value = TurnAngles.fromNavi("TURN_LEFT", 0.3, "엉뚱한 길")
        runCurrent()
        val card = h.controller.state.value.card as DriveCard.Navigating
        assertEquals(DataSource.SIMULATED, card.turn!!.source)
        assertTrue(card.canShowMap)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, guide.requests)   // 데모에서는 내비에 묻지 않는다
    }

    /** [지도에서 보기]: 순정 내비 경로 화면을 열고, 실패하면 음성으로 알린다. */
    @Test
    fun showRouteOverview_opensNaviOrAnnouncesFailure() = runTest {
        val guide = FakeGuide()
        val h = harness(turnGuide = guide)
        h.controller.showRouteOverview()
        runCurrent()
        assertEquals(1, guide.overviews)
        assertFalse(h.tts.spoken.contains("map_unavailable"))

        guide.overview = Result.failure(IllegalStateException("no screen"))
        h.controller.showRouteOverview()
        runCurrent()
        assertTrue(h.tts.spoken.contains("map_unavailable"))
    }

    /** 실제 주행: 경유지로 가는 동안 순정 내비의 다음 갈림길을 3초마다 묻고, 받은 값을 띠에 그대로 쓴다. */
    @Test
    fun live_usesNaviTurnWhileNavigating() = runTest {
        val navi = FakeLiveNavi()
        val car = FakeCar(45)
        val guide = FakeGuide()
        val h = harness(car = car, liveRoute = navi, turnGuide = guide)
        h.controller.startScenario("LIVE")
        runCurrent()
        var km = 210.0
        while (km > 120 && h.controller.state.value.phase != TripPhase.AwaitingDecision) {
            km -= 2.0
            navi.remainingKm = km
            car.socPercent.value = (45 - (210 - km) * 0.2).toInt()
            wait(h, 3_000)
        }
        assertEquals(0, guide.requests)   // 아직 경유지로 가는 중이 아니면 묻지 않는다
        val rec = (h.controller.state.value.card as DriveCard.Recommend).rec
        h.controller.accept(rec.station.id)
        runCurrent()
        assertNull((h.controller.state.value.card as DriveCard.Navigating).turn)

        wait(h, 3_000)
        assertTrue(guide.requests >= 1)
        guide.turn.value = TurnAngles.fromNavi("TURN_ENTER_SERVICEAREA", 1.2, "B 휴게소")
        runCurrent()
        val t = (h.controller.state.value.card as DriveCard.Navigating).turn!!
        assertEquals(Maneuver.SERVICE_AREA, t.maneuver)
        assertEquals(DataSource.SDK, t.source)
        assertEquals("B 휴게소", t.text)
    }
}
