package com.chargis.app.ui

import com.chargis.app.domain.engine.TurnAngles
import com.chargis.app.domain.engine.BatteryThermal
import com.chargis.app.domain.engine.ChargeMath
import com.chargis.app.domain.model.ChargingStation
import com.chargis.app.domain.model.Recommendation
import com.chargis.app.domain.model.RecommendationConfig
import com.chargis.app.domain.model.RecommendationResult
import com.chargis.app.domain.model.VehicleProfile
import com.chargis.app.domain.model.VehicleState
import com.chargis.app.domain.session.TripPhase
import com.chargis.app.sim.Scenario
import com.chargis.app.sim.SimState
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 화면 상태 조립기(B5에서 TripController로부터 분리).
 * [TripMemory]와 시뮬레이터 상태를 읽어 화면에 그릴 값([TripUiState])으로 옮겨 담기만 한다. 아무것도 바꾸지 않는다.
 * 계산(추천, 충전 추적)과 그리기 준비를 나눠 두면, 화면에 무엇을 보여 줄지 바꿀 때 이 파일만 고치면 된다.
 */
class TripPresenter(
    private val canControlPreconditioning: Boolean = true,  // 앱에서 예열을 켤 수 있는 차량인지(VehiclePort)
    private val canShowMap: Boolean = false                  // 순정 내비 경로 화면을 열 수 있는지(TurnGuidePort)
) {

    /** 지금 상황을 화면 상태 하나로 묶는다. [prev]에서 자막·설정처럼 다른 곳이 채우는 값은 그대로 이어받는다. */
    fun present(
        prev: TripUiState,
        m: TripMemory,
        sim: SimState,
        scenario: Scenario,
        declinedIds: Set<String>,
        config: RecommendationConfig,
        energy: EnergyInfo?,
        manualSoc: Boolean = false
    ): TripUiState {
        val v = m.lastVehicle
        val route = m.lastRoute
        val clock = Clock(scenario.startHour, scenario.startMinute)
        return prev.copy(
            phase = m.phase,
            isDriving = v?.isDriving ?: false,
            socPercent = v?.socPercent ?: 0.0,
            dataSource = v?.source ?: prev.dataSource,
            remainingKm = route?.remainingDistanceKm ?: 0.0,
            scenarioTitle = scenario.title,
            ticketNo = "%s-%02d%02d".format(scenario.id, scenario.startHour, scenario.startMinute),
            departAt = clock.text(0.0),
            clock = clock.text(sim.simTimeSec),
            eta = route?.remainingTimeMin?.let { min -> clock.text(sim.simTimeSec + min * 60) } ?: prev.eta,
            routeMarks = buildMarks(m, scenario, sim.traveledKm, declinedIds),
            progress = (sim.traveledKm / scenario.totalDistanceKm).toFloat().coerceIn(0f, 1f),
            receipt = buildReceipt(m, scenario, declinedIds, clock),
            card = buildCard(m, sim, clock, scenario, config),
            stop = buildStop(m, sim, scenario.profile, config, clock)?.copy(manualSoc = manualSoc, carLimitPercent = m.carLimit),
            candidates = candidatesOf(m),
            energy = energy,
            profileName = scenario.profile.displayName,
            dev = DevState(
                clock = clock.text(sim.simTimeSec),
                traveledKm = sim.traveledKm,
                speedMultiplier = sim.speedMultiplier,
                paused = sim.paused
            )
        )
    }

    /**
     * 정차 화면 비교 표("앞으로의 충전소")에 보여 줄 후보.
     * 정차 중에는 지금 서 있는 충전소는 빼고, 남은 후보가 없으면(도착 배터리로는 다음 충전소까지 못 간다고 나온 경우, S3 E 휴게소)
     * 이 충전소에서 채우고 떠날 배터리로 본 후보를 쓴다.
     */
    private fun candidatesOf(m: TripMemory): List<Recommendation> {
        fun list(r: RecommendationResult?) = (r as? RecommendationResult.Recommended)?.let { listOf(it.primary) + it.alternatives }.orEmpty()
        if (m.phase !in STOP_PHASES) return list(m.lastResult)
        val here = m.accepted?.station?.id
        return list(m.lastResult).filter { it.station.id != here }.ifEmpty { list(m.stopResult) }
    }

    /** 주행 화면 아래쪽에 띄울 카드를 고른다: 추천 / 혼잡 비교 / 충전소로 이동 중 / 경고 / 평소 상태 */
    private fun buildCard(m: TripMemory, sim: SimState, clock: Clock, scenario: Scenario, config: RecommendationConfig): DriveCard {
        val o = m.offer
        val acc = m.accepted
        // 계획대로 가면 목적지에 도착하는 시각
        val etaOf = { r: Recommendation -> clock.text(sim.simTimeSec + r.etaMinutes * 60) }
        return when {
            m.phase == TripPhase.AwaitingDecision && o != null -> {
                val alt = o.alternatives.firstOrNull()
                if (o.congested && alt != null) {
                    DriveCard.Congested(o.primary, alt, etaOf(o.primary), etaOf(alt))
                } else {
                    // 다음 후보보다 몇 분 빨리 도착하는지(1분 미만 차이는 말하지 않는다)
                    val faster = o.alternatives.minOfOrNull { it.etaMinutes }
                        ?.let { (it - o.primary.etaMinutes).roundToInt() }
                        ?.takeIf { it >= 1 }
                    DriveCard.Recommend(o.primary, etaOf(o.primary), faster, m.naviCompare)
                }
            }
            m.phase == TripPhase.NavigatingToCharger && acc != null ->
                DriveCard.Navigating(
                    stationName = acc.station.name,
                    distanceKm = acc.station.distanceAlongRouteKm,
                    targetSoc = acc.targetSocPercent,
                    arrivalSoc = acc.arrivalSocPercent,
                    chargeMinutes = acc.chargeMinutes,
                    timeLimitMin = acc.timeLimitMin,
                    timeLimitKnown = acc.station.rule.timeLimitKnown,
                    coldExtraMinutes = acc.coldExtraMinutes,
                    preconditioning = m.lastVehicle?.preconditioning,
                    // 컨트롤러가 예열을 권한 뒤(도착 30분 이내)부터, 아직 꺼져 있으면 버튼을 띄운다.
                    suggestPreconditioning = m.preconditionAdvised && m.lastVehicle?.preconditioning != true,
                    canStartPreconditioning = canControlPreconditioning,
                    // 실제 주행은 순정 내비의 다음 갈림길(TBT), 데모는 수락한 충전소로 만든 안내를 쓴다.
                    // 데모는 진입 3km 안에서만 띠를 띄운다. 더 멀면 띠의 거리가 아래 "N km 남음"과 같은 값이라 겹치기만 한다.
                    turn = if (scenario.live) m.liveTurn
                        else TurnAngles.forStation(acc.station, acc.station.distanceAlongRouteKm, config.offRouteKm)
                            .takeIf { it.distanceKm <= DEMO_TURN_SHOW_KM },
                    canShowMap = canShowMap,
                    appOnly = m.appOnlyWaypoint
                )
            else -> when (val r = m.lastResult) {
                is RecommendationResult.NoSafeStop ->
                    DriveCard.NoSafeStop(r.nearest?.name, r.nearest?.distanceAlongRouteKm)
                is RecommendationResult.Recommended -> {
                    // 혼잡 비교가 걸린 결과라도, 평소 카드에는 가장 빨리 도착하는 계획을 보여 준다.
                    val best = (listOf(r.primary) + r.alternatives).minBy { it.costMinutes }
                    DriveCard.Status(
                        chargePlanned = true,
                        plannedName = best.station.name,
                        plannedKm = best.station.distanceAlongRouteKm,
                        plannedTarget = best.targetSocPercent,
                        plannedMinutes = best.chargeMinutes,
                        nextStopName = best.nextStop?.station?.name,
                        nextStopTarget = best.nextStop?.targetSocPercent
                    )
                }
                else -> DriveCard.Status(chargePlanned = false)
            }
        }
    }

    /** 경로 막대 위에 충전소 표시를 만든다. 각 충전소를 충전함/목표/거절/지나침/앞에 있음 중 하나로 칠한다. */
    private fun buildMarks(m: TripMemory, sc: Scenario, traveledKm: Double, declinedIds: Set<String>): List<RouteMark> {
        val chargedIds = m.charges.map { it.first }.toSet()
        val targetId = when (m.phase) {
            TripPhase.AwaitingDecision -> m.offer?.primary?.station?.id
            TripPhase.NavigatingToCharger, TripPhase.Arrived, TripPhase.Charging -> m.accepted?.station?.id
            else -> null
        }
        // 실제 주행: 시나리오 충전소는 공공데이터가 없을 때 쓰는 번들 예비라, 띠에는 지금 실제로 고른 후보·수락·충전한 곳만 그린다.
        val shown = if (sc.live) liveMarkStations(m, sc, traveledKm, chargedIds) else sc.stations
        return shown.map { s ->
            val state = when {
                s.id in chargedIds -> MarkState.CHARGED
                s.id == targetId -> MarkState.TARGET
                s.id in declinedIds -> MarkState.DECLINED
                s.distanceAlongRouteKm < traveledKm -> MarkState.PASSED
                else -> MarkState.AHEAD
            }
            RouteMark(s.id, s.name, (s.distanceAlongRouteKm / sc.totalDistanceKm).toFloat(), state)
        }
    }

    // 실제 주행 띠에 그릴 충전소(출발점 기준 거리). 후보는 지금 위치 기준 거리라 달린 거리를 더한다.
    private fun liveMarkStations(m: TripMemory, sc: Scenario, traveledKm: Double, chargedIds: Set<String>): List<ChargingStation> {
        val candidates = (m.lastResult as? RecommendationResult.Recommended)
            ?.let { r -> (listOf(r.primary) + r.alternatives).map { it.station } }.orEmpty()
            .map { it.copy(distanceAlongRouteKm = it.distanceAlongRouteKm + traveledKm) }
        val kept = sc.stations.filter { it.id in chargedIds || it.id == m.accepted?.station?.id }   // 수락·충전한 곳은 시나리오에 올라가 있다
        return (kept + candidates).distinctBy { it.id }.sortedBy { it.distanceAlongRouteKm }
    }

    /** 통행권 모양 영수증에 들어갈 여행 기록(출발·도착 시각, 충전 내역, 거절한 충전소). */
    private fun buildReceipt(m: TripMemory, sc: Scenario, declinedIds: Set<String>, clock: Clock): TripReceipt? {
        if (m.phase == TripPhase.Idle) return null
        val names = sc.stations.associate { it.id to it.name }
        return TripReceipt(
            departAt = clock.text(0.0),
            startSoc = sc.startSoc,
            arriveAt = m.arrivedAtSec?.let(clock::text),
            endSoc = if (m.phase == TripPhase.Finished) m.lastVehicle?.socPercent else null,
            distanceKm = sc.totalDistanceKm,
            charges = m.charges.map { it.second },
            declinedNames = declinedIds.mapNotNull { names[it] },
            // Chargis 안내대로 한 번도 충전하지 않았으면 Chargis 계획과의 비교가 실제 여행과 맞지 않아 적지 않는다.
            compare = m.habitBase?.takeIf { m.charges.isNotEmpty() }?.let { b ->
                TripCompare(
                    fasterMin = (b.habit.etaMinutes - b.chargisEtaMinutes).roundToInt(),
                    habitStops = b.habit.stops,
                    chargisStops = if (m.phase == TripPhase.Finished) m.charges.size else b.chargisStops,
                    habitLimitCuts = b.habit.limitCuts,
                    chargisLimitCuts = m.charges.count { it.second.endedByLimit },
                    congestionAvoided = m.congestionAvoided
                )
            }
        )
    }

    /** 충전소 정차 화면(도착·충전 중·완료)에 보여 줄 숫자들. 충전 중에는 실측 보정값을 쓴다. */
    private fun buildStop(
        m: TripMemory,
        sim: SimState,
        profile: VehicleProfile,
        config: RecommendationConfig,
        clock: Clock
    ): StopInfo? {
        val rec = m.accepted ?: return null
        val v = m.lastVehicle
        if (v == null || m.phase !in STOP_PHASES) return null
        val session = m.charge
        val snap = session?.snapshot
        val target = session?.targetSoc ?: rec.targetSocPercent
        // 배터리가 차가우면 충전을 덜 받는다. 예상 시간과 출력 곡선을 지금 배터리 온도로 그린다.
        val thermal = BatteryThermal.chargePowerFactor(v.batteryTempC ?: v.outsideTempC)
        val cold = profile.withChargePowerFactor(thermal)
        val common = StopCommon(rec.station, target, v, cold, config, profile, thermal < COLD_NOTE_FACTOR)
        if (m.phase == TripPhase.Charging && snap != null) {
            return common.fill(
                StopInfo(
                    stationName = rec.station.name,
                    currentSoc = v.socPercent,
                    targetSoc = target,
                    minutesToTarget = ceil(snap.minutesToTarget - 1e-9).toInt(),
                    timeLimitMin = rec.timeLimitMin,
                    minutesToLimit = snap.minutesToLimit?.let { max(0, ceil(it - 1e-9).toInt()) },
                    departAt = clock.text(snap.departAtSec),
                    predictedSocAtLimit = snap.predictedSocAtLimit,
                    willHitLimit = snap.willHitLimit,
                    chargePowerKw = v.chargePowerKw
                ),
                rec
            )
        }
        // 도착(충전 전)·완료: 모델값으로 예상한다.
        val toTarget = ChargeMath.chargeMinutes(v.socPercent, target, rec.station.maxKw, cold, config)
        val atLimit = rec.timeLimitMin?.let { ChargeMath.socAfterMinutes(v.socPercent, it.toDouble(), rec.station.maxKw, cold, config) }
        return common.fill(
            StopInfo(
                stationName = rec.station.name,
                currentSoc = v.socPercent,
                targetSoc = target,
                minutesToTarget = ceil(toTarget).toInt(),
                timeLimitMin = rec.timeLimitMin,
                minutesToLimit = rec.timeLimitMin,
                departAt = clock.text(sim.simTimeSec + toTarget * 60),
                predictedSocAtLimit = atLimit,
                willHitLimit = rec.timeLimitMin != null && toTarget > rec.timeLimitMin,
                endedByLimit = m.phase == TripPhase.Done && m.endedByLimit
            ),
            rec
        )
    }

    // 정차 화면 세 단계가 함께 쓰는 값: 출력 곡선, 목표 조정 가능 여부, 다음 충전소, 요금.
    // [profile]은 배터리 온도를 반영한 사양(곡선용), [base]는 원래 사양(요금용 배터리 용량).
    private class StopCommon(
        val station: ChargingStation,
        val target: Double,
        val v: VehicleState,
        val profile: VehicleProfile,
        val config: RecommendationConfig,
        val base: VehicleProfile,
        val cold: Boolean
    ) {
        fun fill(info: StopInfo, rec: Recommendation) = info.copy(
            powerCurve = (0..100 step CURVE_STEP).map { soc ->
                CurveSample(soc.toFloat(), ChargeMath.powerKw(soc.toDouble(), station.maxKw, profile, config).toFloat())
            },
            canRaiseTarget = target + STEP <= MAX_TARGET,
            canLowerTarget = target - STEP > v.socPercent + MIN_GAP,
            targetInTaper = target > config.taperStartSocPercent,
            nextStopName = rec.nextStop?.station?.name,
            nextStopTarget = rec.nextStop?.targetSocPercent,
            timeLimitKnown = rec.station.rule.timeLimitKnown,
            costKrw = ChargeMath.chargeCostKrw(v.socPercent, target, base, station.pricePerKwh, config),
            coldBattery = cold
        )
    }

    /** 시뮬레이터 경과 초를 "14:05" 같은 시각 글자로 바꾼다. 시나리오 출발 시각에서부터 센다. */
    class Clock(private val startHour: Int, private val startMinute: Int = 0) {
        fun text(simSec: Double): String {
            val total = startHour * 3600 + startMinute * 60 + simSec.toLong()
            val h = (total / 3600) % 24
            val m = (total % 3600) / 60
            return "%02d:%02d".format(h, m)
        }

        /** 0시부터 센 분(대기 예측용, B4) */
        fun minuteOfDay(simSec: Double): Int = ((startHour * 3600 + startMinute * 60 + simSec.toLong()) / 60 % (24 * 60)).toInt()
    }

    companion object {
        // 정차 화면을 보여 주는 단계들
        val STOP_PHASES = setOf(TripPhase.Arrived, TripPhase.Charging, TripPhase.Done)
        const val STEP = 5.0          // 목표 조정 단위(%)
        const val MAX_TARGET = 100.0
        const val MIN_GAP = 1.0       // 목표는 지금 %보다 이만큼은 높아야 한다
        private const val CURVE_STEP = 2
        const val DEMO_TURN_SHOW_KM = 3.0  // 데모 턴 안내 띠를 띄우는 거리(진입까지)
        private const val COLD_NOTE_FACTOR = 0.9   // 충전 수용이 평소의 90% 아래면 "배터리가 차가워 느림"을 알린다
    }
}
