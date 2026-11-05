package com.chargis.app.data.sdk

import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.DrivingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 순정 내비 onDrivingInfo에서 내비가 스스로 넣은 충전 경유지를 꺼내는 규칙(B). 값은 2026-11-06 Pleos 에뮬레이터 실측 모양. */
class NaviChargingStopTest {
    private fun state(name: String, distance: Int, remain: Int, charging: Boolean, kw: Int = 0, count: Int = 0) =
        DrivingState(name, 2820, distance, remain, charging, false, kw, 10, count, null)

    @Test
    fun picksChargingWaypoint() {
        val info = DrivingInfo(
            state("속초", 198000, 7740, false),
            listOf(state("가평휴게소(춘천방향) E-pit 전기차충전소", 65000, 10555, true, 260, 6))
        )
        val stop = PleosNavi.chargingStopOf(info)!!
        assertEquals("가평휴게소(춘천방향) E-pit 전기차충전소", stop.name)
        assertEquals(65.0, stop.distanceKm, 1e-9)
        assertEquals(10.555, stop.arrivalKwh!!, 1e-9)
        assertEquals(260.0, stop.maxKw, 1e-9)
        assertEquals(6, stop.chargers)
    }

    @Test
    fun ignoresCancelledRouteAndPlainWaypoints() {
        // 경로를 취소하면 거리 0으로 온다(에뮬레이터 실측).
        assertNull(PleosNavi.chargingStopOf(DrivingInfo(state("x", 0, 0, false), listOf(state("가평", 0, 0, false)))))
        assertNull(PleosNavi.chargingStopOf(DrivingInfo(state("x", 1000, 0, false), listOf(state("친구 집", 5000, 0, false)))))
    }
}
