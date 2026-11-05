package com.chargis.app.domain.engine

import kotlin.math.ceil

/**
 * 차량 충전 한도(EV_CHARGE_PERCENT_LIMIT)를 몇 %로 넣을지 정한다.
 *
 * 충전은 Chargis가 목표에 닿을 때 직접 끈다(충전 스위치 끄기). 차량 한도는 Chargis가 끄지 못했을 때(앱 종료, 신호 지연,
 * 배터리 직접 입력의 추정 오차) 차가 대신 멈추는 안전장치라서, 목표와 딱 같은 값이 아니라 여유를 둔다.
 * - 목표를 5% 단위로 올리고 [MARGIN]을 더한다. 예) 목표 52% → 55% + 10 = 65%.
 * - 목표보다 낮게는 넣지 않는다. 낮으면 차가 목표 전에 멈춰, 다음 충전소·목적지까지 필요한 양을 채우지 못한다.
 * - 차가 받는 범위([MIN]~[MAX])로 묶는다. 목표가 50% 미만이어도 한도는 50%로 넣을 수 있다.
 */
object ChargeLimit {
    const val STEP = 5
    const val MARGIN = 10
    const val MIN = 50
    const val MAX = 100
    /** 충전을 마치고 떠날 때 되돌리는 값. 원래 값을 SDK로 읽을 수 없어 제한 없음(100%)으로 둔다. */
    const val RESTORE = 100

    /** 목표 [targetSoc]%일 때 넣을 차량 한도(%) */
    fun forTarget(targetSoc: Double): Int {
        val up = (ceil(targetSoc / STEP - 1e-9) * STEP).toInt()
        return (up + MARGIN).coerceIn(MIN, MAX)
    }

    /** 차가 받는 값 목록([allowed])에서 [wanted] 이상 중 가장 가까운 값. 목록이 없으면 [wanted], 모두 작으면 가장 큰 값. */
    fun pick(wanted: Int, allowed: List<Int>?): Int {
        if (allowed.isNullOrEmpty()) return wanted
        return allowed.filter { it >= wanted }.minOrNull() ?: allowed.max()
    }
}
