package com.chargis.app.sim

import com.chargis.app.data.fake.FakeNavi
import com.chargis.app.domain.engine.ChargeLimit
import com.chargis.app.domain.model.ChargingStation
import com.chargis.app.domain.model.GeoPoint
import com.chargis.app.domain.model.RouteState
import com.chargis.app.domain.session.TripPhase
import com.chargis.app.port.CarSignalsPort
import com.chargis.app.port.ChargeControlPort
import com.chargis.app.port.LiveRoutePort
import com.chargis.app.port.NaviChargingStop
import com.chargis.app.port.NaviDestination
import com.chargis.app.port.NaviPort
import com.chargis.app.ui.DeclineOutcome
import com.chargis.app.ui.DriveCard
import com.chargis.app.ui.GleoReplies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 차별점 1~4번(A~E) 통합 테스트.
 * A 차량 충전 한도·충전 끄기, B 순정 내비 충전 경유지 바꾸기, C 순정 내비 충전소와 비교, D 영수증의 일반 방식 비교, E Gleo 설명·거절.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DifferentiatorsTest {

    /** 차량 충전 제어 흉내: 부른 순서대로 적어 둔다. [fail]이면 모두 실패한다. */
    private class FakeControl(override val canControl: Boolean = true, var fail: Boolean = false) : ChargeControlPort {
        val calls = mutableListOf<String>()
        override suspend fun setChargeLimit(percent: Int): Result<Int> {
            calls += "limit:$percent"
            return if (fail) Result.failure(IllegalStateException("no")) else Result.success(ChargeLimit.pick(percent, (50..100 step 5).toList()))
        }
        override suspend fun stopCharging(): Result<Unit> {
            calls += "stop"
            return if (fail) Result.failure(IllegalStateException("no")) else Result.success(Unit)
        }
    }

    // S1을 시작해 B 휴게소를 수락하고 도착까지 간다.
    private fun TestScope.arriveAtB(h: Harness) {
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        drive(h) { h.controller.state.value.phase == TripPhase.Arrived }
    }

    // ---- A ----

    @Test
    fun limitRule_targetPlusTenPercentWithinCarRange() {
        assertEquals(50, ChargeLimit.forTarget(30.0))   // 30 + 10 = 40 → 차가 받는 최소 50
        assertEquals(50, ChargeLimit.forTarget(40.0))
        assertEquals(65, ChargeLimit.forTarget(52.0))   // 55 + 10
        assertEquals(65, ChargeLimit.forTarget(55.0))
        assertEquals(90, ChargeLimit.forTarget(80.0))
        assertEquals(100, ChargeLimit.forTarget(95.0))  // 105 → 100
        assertEquals(65, ChargeLimit.pick(63, (50..100 step 5).toList()))
        assertEquals(100, ChargeLimit.pick(120, (50..100 step 5).toList()))
        assertEquals(63, ChargeLimit.pick(63, null))
        // 한도는 언제나 목표 이상이다(목표 전에 차가 멈추지 않는다).
        for (t in 1..100) assertTrue("target $t", ChargeLimit.forTarget(t.toDouble()) >= t)
    }

    @Test
    fun charging_setsCarLimitThenStopsCarAtTarget_andRestoresOnDepart() = runTest {
        val control = FakeControl()
        val h = harness(chargeControl = control)
        arriveAtB(h)
        val target = h.controller.state.value.stop!!.targetSoc
        h.controller.startCharging()
        runCurrent()
        val limit = ChargeLimit.forTarget(target)
        assertEquals(listOf("limit:$limit"), control.calls)
        assertTrue(h.tts.spoken.contains("car_limit:$limit"))
        assertEquals(limit, h.controller.state.value.stop!!.carLimitPercent)

        drive(h) { h.controller.state.value.phase == TripPhase.Done }
        runCurrent()
        assertEquals(listOf("limit:$limit", "stop"), control.calls)
        assertTrue(h.tts.spoken.contains("car_stopped"))
        assertEquals(1, h.controller.state.value.receipt!!.charges.size)

        h.controller.depart()
        runCurrent()
        assertEquals("limit:${ChargeLimit.RESTORE}", control.calls.last())
        assertFalse(h.tts.spoken.contains("car_control_failed"))
    }

    @Test
    fun targetChange_resetsCarLimit_andManualStopTurnsCarOffWithoutTargetMessage() = runTest {
        val control = FakeControl()
        val h = harness(chargeControl = control)
        arriveAtB(h)
        h.controller.startCharging()
        runCurrent()
        val target = h.controller.state.value.stop!!.targetSoc
        h.controller.adjustTarget(5)
        runCurrent()
        assertEquals("limit:${ChargeLimit.forTarget(target + 5)}", control.calls.last())
        h.controller.stopCharging()
        runCurrent()
        assertEquals("stop", control.calls.last())
        assertFalse(h.tts.spoken.contains("car_stopped"))
        assertEquals(1, h.controller.state.value.receipt!!.charges.size)
    }

    @Test
    fun carControlFailure_announcedOnce_andTripContinues() = runTest {
        val control = FakeControl(fail = true)
        val h = harness(chargeControl = control)
        arriveAtB(h)
        h.controller.startCharging()
        runCurrent()
        drive(h) { h.controller.state.value.phase == TripPhase.Done }
        runCurrent()
        assertEquals(1, h.tts.spoken.count { it == "car_control_failed" })
        assertNull(h.controller.state.value.stop!!.carLimitPercent)
    }

    @Test
    fun noCarControl_noCalls() = runTest {
        val control = FakeControl(canControl = false)
        val h = harness(chargeControl = control)
        arriveAtB(h)
        h.controller.startCharging()
        drive(h) { h.controller.state.value.phase == TripPhase.Done }
        runCurrent()
        assertTrue(control.calls.isEmpty())
    }

    // ---- B ----

    @Test
    fun naviOwnChargingStop_driverToldToSkipIt() = runTest {
        val h = harness(navi = { sim ->
            val demo = FakeNavi(sim)
            object : NaviPort {
                override val routeState: Flow<RouteState?> = demo.routeState
                override suspend fun addWaypoint(station: ChargingStation) = demo.addWaypoint(station)
                override suspend fun removeWaypoint(station: ChargingStation) = demo.removeWaypoint(station)
                override suspend fun requestReRoute() = demo.requestReRoute()
                override val lastNaviOwnStop: String? = "가평휴게소 E-pit"
            }
        })
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        assertEquals(TripPhase.NavigatingToCharger, h.controller.state.value.phase)
        assertTrue(h.tts.spoken.toString(), h.tts.spoken.any { it.startsWith("with_navi_stop:가평휴게소 E-pit:") })
        assertFalse(h.tts.spoken.any { it.startsWith("waypoint:") })
    }

    @Test
    fun naviStopAppearingAfterAccept_announcedOnce() = runTest {
        val stop = MutableStateFlow<NaviChargingStop?>(null)
        val h = harness(naviStop = stop)
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        h.controller.accept("rest_b")
        runCurrent()
        // 재탐색 뒤 내비가 자기 충전소를 알려 온다(에뮬레이터: onDrivingInfo는 경로가 바뀔 때만 온다).
        stop.value = NaviChargingStop("가평휴게소 E-pit", 65.0, 10.5, 260.0, 6)
        runCurrent()
        stop.value = null
        runCurrent()
        stop.value = NaviChargingStop("가평휴게소 E-pit", 64.0, 10.4, 260.0, 6)
        runCurrent()
        assertEquals(1, h.tts.spoken.count { it == "navi_stop_skip:가평휴게소 E-pit" })
        // 내비가 Chargis와 같은 충전소를 넣었으면 말하지 않는다.
        stop.value = NaviChargingStop("B 휴게소", 46.0, 20.0, 100.0, 4)
        runCurrent()
        assertFalse(h.tts.spoken.contains("navi_stop_skip:B 휴게소"))
    }

    // ---- C ----

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

    // 실제 주행: 내비 남은 거리를 줄이며 추천 안내까지 간다.
    private fun TestScope.liveUntilOffer(naviStop: NaviChargingStop?): Harness {
        val navi = FakeLiveNavi()
        val car = FakeCar(45)
        val h = harness(car = car, liveRoute = navi, naviStop = MutableStateFlow(naviStop))
        h.controller.startScenario("LIVE")
        runCurrent()
        var km = 210.0
        while (km > 100 && h.controller.state.value.phase != TripPhase.AwaitingDecision) {
            km -= 2.0
            navi.remainingKm = km
            car.socPercent.value = (45 - (210 - km) * 0.2).toInt()
            h.sim.advance(3.0)
            advanceTimeBy(3_000)
            runCurrent()
        }
        assertEquals(TripPhase.AwaitingDecision, h.controller.state.value.phase)
        return h
    }

    @Test
    fun liveTrip_comparesWithNaviChargingStop() = runTest {
        // 순정 내비가 Chargis 추천보다 15km 뒤의 충전소를 골랐다고 본다.
        val probe = liveUntilOffer(null)
        val recKm = (probe.controller.state.value.card as DriveCard.Recommend).rec.station.distanceAlongRouteKm
        val h = liveUntilOffer(NaviChargingStop("내비 휴게소 E-pit", recKm + 15.0, 12.0, 200.0, 4))
        val card = h.controller.state.value.card as DriveCard.Recommend
        val n = assertNotNullAndGet(card.navi)
        assertEquals("내비 휴게소 E-pit", n.naviName)
        assertFalse(n.same)
        assertNotNull(n.chargisFasterMin)
        assertNotNull(n.naviTarget)
        val gain = n.chargisFasterMin!!
        val spoken = h.tts.spoken.any { it.startsWith("navi_compare:내비 휴게소 E-pit:") }
        // 빨리 도착할 때만 음성으로 덧붙인다.
        assertEquals(gain >= 1, spoken)
        println("C live compare: chargis=${card.rec.station.name} navi=${n.naviName} gain=${gain}min naviTarget=${n.naviTarget} naviCharge=${n.naviChargeMinutes}min")
        // 수락하면 비교는 지운다.
        h.controller.accept(card.rec.station.id)
        runCurrent()
        assertTrue(h.controller.state.value.card !is DriveCard.Recommend)
    }

    @Test
    fun liveTrip_naviStopTooFar_markedUnreachable() = runTest {
        val h = liveUntilOffer(NaviChargingStop("먼 휴게소", 190.0, 2.0, 200.0, 4))
        val n = assertNotNullAndGet((h.controller.state.value.card as DriveCard.Recommend).navi)
        assertFalse(n.naviReachable)
        assertFalse(h.tts.spoken.any { it.startsWith("navi_compare:") })
    }

    @Test
    fun liveTrip_sameStopAsNavi() = runTest {
        val probe = liveUntilOffer(null)
        val rec = (probe.controller.state.value.card as DriveCard.Recommend).rec
        val h = liveUntilOffer(NaviChargingStop(rec.station.name + " 전기차충전소", rec.station.distanceAlongRouteKm + 0.3, null, rec.station.maxKw, 4))
        val n = assertNotNullAndGet((h.controller.state.value.card as DriveCard.Recommend).navi)
        assertTrue(n.same)
        assertFalse(h.tts.spoken.any { it.startsWith("navi_compare:") })
    }

    @Test
    fun demoScenario_neverComparesWithNavi() = runTest {
        val h = harness(naviStop = MutableStateFlow(NaviChargingStop("내비 휴게소", 80.0, 10.0, 200.0, 4)))
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        assertNull((h.controller.state.value.card as DriveCard.Recommend).navi)
    }

    // ---- D ----

    @Test
    fun receiptCompare_allScenarios() = runTest {
        for (id in listOf("S1", "S2", "S3", "S4", "S5", "S6")) {
            val h = harness()
            h.controller.startScenario(id)
            runToFinish(h)
            val r = h.controller.state.value.receipt!!
            val c = r.compare
            println("D $id: compare=$c charges=${r.charges.map { "${it.stationName} ${it.fromSoc.toInt()}→${it.toSoc.toInt()} ${it.minutes}m${if (it.endedByLimit) " limit" else ""}" }}")
            assertNotNull("$id compare", c)
            assertEquals(r.charges.size, c!!.chargisStops)
            assertEquals(r.charges.count { it.endedByLimit }, c.chargisLimitCuts)
        }
    }

    @Test
    fun receiptCompare_hiddenWhenDriverNeverCharged() = runTest {
        val h = harness()
        h.controller.startScenario("S1")
        repeat(8 * 3600) {
            if (h.controller.state.value.phase == TripPhase.Finished) return@repeat
            if (h.controller.state.value.phase == TripPhase.AwaitingDecision) h.controller.decline()
            h.sim.advance(1.0)
            runCurrent()
        }
        val r = h.controller.state.value.receipt
        if (h.controller.state.value.phase == TripPhase.Finished) {
            assertTrue(r!!.charges.isEmpty())
            assertNull(r.compare)
        }
    }

    // 추천은 1순위(혼잡이면 빠른 대안)를 수락하고, 도착하면 충전하고, 다 채우면 떠나 목적지까지 간다.
    private fun TestScope.runToFinish(h: Harness) {
        repeat(8 * 3600) {
            val s = h.controller.state.value
            when (s.phase) {
                TripPhase.Finished -> return
                TripPhase.AwaitingDecision -> when (val card = s.card) {
                    is DriveCard.Recommend -> h.controller.accept(card.rec.station.id)
                    is DriveCard.Congested -> h.controller.accept(card.alternative.station.id)
                    else -> Unit
                }
                TripPhase.Arrived -> h.controller.startCharging()
                TripPhase.Done -> h.controller.depart()
                else -> Unit
            }
            h.sim.advance(1.0)
            runCurrent()
        }
        error("목적지에 도착하지 못함: ${h.controller.state.value.phase}")
    }

    // ---- E ----

    @Test
    fun gleoExplain_recommendCard_andStop() = runTest {
        val h = harness()
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        val s = h.controller.state.value
        val reply = GleoReplies.explain(s)
        val rec = (s.card as DriveCard.Recommend).rec
        assertTrue(reply.success)
        assertTrue(reply.message, reply.message.startsWith("Chargis recommends ${rec.station.name} because "))
        assertTrue(reply.message, reply.message.contains("${rec.targetSocPercent.toInt()}%"))
        println("E explain(recommend): ${reply.message}")
        assertEquals(reply, GleoReplies.snapshot(s).explain)

        h.controller.accept(rec.station.id)
        runCurrent()
        drive(h) { h.controller.state.value.phase == TripPhase.Arrived }
        val stop = GleoReplies.explain(h.controller.state.value)
        assertTrue(stop.message, stop.message.startsWith("The target is "))
        println("E explain(stop): ${stop.message}")
    }

    @Test
    fun gleoDecline_skipsOffer() = runTest {
        val h = harness()
        assertEquals(DeclineOutcome.NOTHING_TO_DECLINE, h.controller.declineForAssistant())
        h.controller.startScenario("S1")
        drive(h) { h.controller.state.value.phase == TripPhase.AwaitingDecision }
        val name = (h.controller.state.value.card as DriveCard.Recommend).rec.station.name
        assertEquals(DeclineOutcome.DECLINED, h.controller.declineForAssistant())
        assertEquals(TripPhase.Driving, h.controller.state.value.phase)
        assertTrue(GleoReplies.declineReply(name, DeclineOutcome.DECLINED).message.contains(name))
    }

    @Test
    fun gleoExplain_noTrip() {
        val reply = GleoReplies.explain(com.chargis.app.ui.TripUiState())
        assertFalse(reply.success)
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v)
        return v!!
    }
}
