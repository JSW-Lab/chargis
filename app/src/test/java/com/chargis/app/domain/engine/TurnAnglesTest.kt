package com.chargis.app.domain.engine

import com.chargis.app.domain.model.DataSource
import com.chargis.app.domain.model.Maneuver
import com.chargis.app.domain.model.StationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 턴 안내 화살표 각도: 순정 내비 TurnType → 움직임·대표 각도, 데모 충전소 → 다음 움직임. */
class TurnAnglesTest {

    @Test
    fun naviTurnTypes_mapToManeuverAndAngle() {
        assertEquals(Maneuver.STRAIGHT to 0, TurnAngles.maneuverOf("TURN_STRAIGHT"))
        assertEquals(Maneuver.KEEP_RIGHT to 35, TurnAngles.maneuverOf("TURN_KEEP_R"))
        assertEquals(Maneuver.KEEP_LEFT to -35, TurnAngles.maneuverOf("TURN_KEEP_L"))
        assertEquals(Maneuver.SERVICE_AREA to 35, TurnAngles.maneuverOf("TURN_ENTER_SERVICEAREA"))
        assertEquals(Maneuver.SERVICE_AREA to 35, TurnAngles.maneuverOf("TURN_RESTAREA"))
        assertEquals(Maneuver.EXIT_RIGHT to 60, TurnAngles.maneuverOf("TURN_EXIT_IC_R"))
        assertEquals(Maneuver.RIGHT to 90, TurnAngles.maneuverOf("TURN_RIGHT"))
        assertEquals(Maneuver.LEFT to -90, TurnAngles.maneuverOf("TURN_LEFT"))
        assertEquals(Maneuver.SHARP_RIGHT to 135, TurnAngles.maneuverOf("TURN_SHARP_R"))
        assertEquals(Maneuver.U_TURN to 180, TurnAngles.maneuverOf("TURN_UTURN"))
        assertEquals(Maneuver.ARRIVE to 0, TurnAngles.maneuverOf("TURN_ARRIVE"))
    }

    /** 회전교차로는 내비가 알려 주는 시계 방향을 그대로 각도로 쓴다(2시 = 60°, 10시 = -60°, 12시 = 0°). */
    @Test
    fun roundabout_usesClockDirection() {
        assertEquals(Maneuver.ROUNDABOUT to 60, TurnAngles.maneuverOf("TURN_ENTER_ROUNDABOUT_02"))
        assertEquals(Maneuver.ROUNDABOUT to 90, TurnAngles.maneuverOf("TURN_ENTER_ROUNDABOUT_03"))
        assertEquals(Maneuver.ROUNDABOUT to -60, TurnAngles.maneuverOf("TURN_ENTER_ROUNDABOUT_10"))
        assertEquals(Maneuver.ROUNDABOUT to 0, TurnAngles.maneuverOf("TURN_ENTER_ROUNDABOUT_12"))
    }

    /** 모르는 종류는 띠를 숨기도록 null(틀린 화살표보다 없는 게 낫다). 거리는 m → km로 바꾸고 빈 설명은 null. */
    @Test
    fun unknownTurn_isNull_andNaviValuesAreCleaned() {
        assertNull(TurnAngles.maneuverOf("TURN_UNKNOWN"))
        assertNull(TurnAngles.fromNavi("TURN_UNKNOWN", 1.0, "x"))
        val t = TurnAngles.fromNavi("TURN_KEEP_R", 0.49, "  ")!!
        assertEquals(0.49, t.distanceKm, 1e-9)
        assertNull(t.text)
        assertEquals(DataSource.SDK, t.source)
    }

    /** 데모: 휴게소는 "휴게소 진입", 경로 밖 충전소는 IC 진출 → 빠져나온 뒤 우회전(남은 거리는 편도 우회 거리). */
    @Test
    fun demoStation_buildsNextMove() {
        val rest = station("rest_b", 23.0).copy(name = "B 휴게소")
        val a = TurnAngles.forStation(rest, 23.0, offRouteKm = 0.5)
        assertEquals(Maneuver.SERVICE_AREA, a.maneuver)
        assertEquals(35, a.angleDeg)
        assertEquals(23.0, a.distanceKm, 1e-9)
        assertEquals(DataSource.SIMULATED, a.source)

        val mart = station("mart", 5.0, detourKm = 2.5, kind = StationKind.COMMERCIAL).copy(name = "I 마트")
        assertEquals(Maneuver.EXIT_RIGHT, TurnAngles.forStation(mart, 5.0, 0.5).maneuver)
        val out = TurnAngles.forStation(mart, 0.0, 0.5)
        assertEquals(Maneuver.RIGHT, out.maneuver)
        assertEquals(2.5, out.distanceKm, 1e-9)
    }
}
