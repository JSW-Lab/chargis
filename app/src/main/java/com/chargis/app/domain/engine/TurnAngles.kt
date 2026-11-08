package com.chargis.app.domain.engine

import com.chargis.app.domain.model.ChargingStation
import com.chargis.app.domain.model.DataSource
import com.chargis.app.domain.model.Maneuver
import com.chargis.app.domain.model.TurnHint

/**
 * 턴 안내 화살표의 방향을 정한다. 순수 Kotlin(안드로이드·Pleos 의존 없음).
 *
 * - [fromNavi]: 순정 내비 TurnType 이름("TURN_KEEP_R" 등)을 [Maneuver]와 대표 각도로 바꾼다.
 *   좌/우 두 가지가 아니라 "살짝 오른쪽(35°)", "IC로 빠지기(60°)", "우회전(90°)", "급우회전(135°)"처럼 나눈다.
 *   회전교차로는 내비가 알려 주는 시계 방향(2시, 10시 등)을 그대로 각도로 쓴다.
 *   모르는 종류는 null(틀린 화살표를 보여 주느니 띠를 숨긴다).
 * - [forStation]: 데모 경로에서는 순정 내비가 가상 경로를 안내하지 않으므로, 수락한 충전소의 종류와 우회 거리로 다음 움직임을 만든다.
 */
object TurnAngles {
    const val KEEP = 35
    const val EXIT = 60
    const val TURN = 90
    const val SHARP = 135

    /** [turnType]: Pleos TurnType의 이름. 해당이 없으면 null */
    fun fromNavi(turnType: String, distanceKm: Double, text: String?): TurnHint? {
        val (m, a) = maneuverOf(turnType) ?: return null
        return TurnHint(m, a, distanceKm.coerceAtLeast(0.0), text?.trim()?.takeIf { it.isNotEmpty() }, DataSource.SDK)
    }

    fun maneuverOf(t: String): Pair<Maneuver, Int>? {
        // 회전교차로: TURN_ENTER_ROUNDABOUT_02 → 2시 방향 → 60°. 9~12시는 왼쪽(음수).
        if (t.startsWith("TURN_ENTER_ROUNDABOUT_")) {
            val clock = t.removePrefix("TURN_ENTER_ROUNDABOUT_").toIntOrNull() ?: return null
            val deg = clock * 30
            return Maneuver.ROUNDABOUT to (if (deg > 180) deg - 360 else deg)
        }
        return when (t) {
            "TURN_STRAIGHT", "TURN_TUNNEL", "TURN_ENTER_OVER_C", "TURN_ENTER_UNDER_C", "TURN_AVOID_OVER_C", "TURN_AVOID_UNDER_C",
            "TURN_EXIT_ROUNDABOUT" -> Maneuver.STRAIGHT to 0
            "TURN_KEEP_R", "TURN_ENTER_OVER_R", "TURN_ENTER_UNDER_R", "TURN_AVOID_OVER_R", "TURN_AVOID_UNDER_R" -> Maneuver.KEEP_RIGHT to KEEP
            "TURN_KEEP_L", "TURN_ENTER_OVER_L", "TURN_ENTER_UNDER_L", "TURN_AVOID_OVER_L", "TURN_AVOID_UNDER_L" -> Maneuver.KEEP_LEFT to -KEEP
            "TURN_DEVIDE_MERGE" -> Maneuver.KEEP_RIGHT to KEEP
            // 국내 고속도로 휴게소·졸음쉼터는 오른쪽으로 들어간다.
            "TURN_ENTER_SERVICEAREA", "TURN_RESTAREA" -> Maneuver.SERVICE_AREA to KEEP
            "TURN_EXIT_IC_R", "TURN_ENTER_IC_R" -> Maneuver.EXIT_RIGHT to EXIT
            "TURN_EXIT_IC_L", "TURN_ENTER_IC_L" -> Maneuver.EXIT_LEFT to -EXIT
            "TURN_EXIT_IC_C", "TURN_ENTER_IC_C" -> Maneuver.STRAIGHT to 0
            "TURN_RIGHT" -> Maneuver.RIGHT to TURN
            "TURN_LEFT" -> Maneuver.LEFT to -TURN
            "TURN_SHARP_R" -> Maneuver.SHARP_RIGHT to SHARP
            "TURN_SHARP_L" -> Maneuver.SHARP_LEFT to -SHARP
            "TURN_UTURN" -> Maneuver.U_TURN to 180
            "TURN_WAY" -> Maneuver.WAYPOINT to 0
            "TURN_ARRIVE" -> Maneuver.ARRIVE to 0
            else -> null
        }
    }

    /**
     * 데모: 수락한 충전소로 가는 다음 움직임. [distanceKm]는 경로에서 충전소로 빠지는 지점까지 남은 거리.
     * 휴게소·쉼터는 "휴게소 진입", 경로를 벗어나는 곳(IC 근처·마트 등)은 "IC로 나가기" 뒤 1km 안에서 "우회전".
     */
    fun forStation(station: ChargingStation, distanceKm: Double, offRouteKm: Double): TurnHint {
        val d = distanceKm.coerceAtLeast(0.0)
        val offRoute = station.detourKm > offRouteKm
        val (m, a) = when {
            !offRoute && station.kind.isRoadside -> Maneuver.SERVICE_AREA to KEEP
            !offRoute -> Maneuver.KEEP_RIGHT to KEEP
            d > 0.0 -> Maneuver.EXIT_RIGHT to EXIT
            else -> Maneuver.RIGHT to TURN
        }
        // 이미 빠져나왔으면 충전소까지 남은 우회 거리(편도)를 보여 준다.
        val shown = if (offRoute && d <= 0.0) station.detourKm else d
        return TurnHint(m, a, shown, station.name, DataSource.SIMULATED)
    }
}
