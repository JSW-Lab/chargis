package com.chargis.app.ui

import com.chargis.app.domain.model.ReasonCode
import com.chargis.app.domain.model.WaitSource
import com.chargis.app.domain.session.TripPhase
import kotlin.math.roundToInt

/**
 * Gleo AI가 Chargis 함수를 부를 때 돌려줄 답(assets/schema.json, Gleo Interface).
 * 화면 상태([TripUiState])만 보고 만든다. Gleo는 [message]와 [data]를 바탕으로 운전자에게 할 말을 다시 써서 말한다.
 * Gleo 가이드에 따라 message는 영어 문장으로 쓰되, 충전소 이름은 화면과 같은 한글 그대로 둔다.
 *
 * 앱이 꺼져 있다가 Gleo가 부르면 7초 안에 답해야 해서, 컨트롤러가 상태를 바꿀 때마다 미리 만들어 기기에 저장해 둔다([GleoSnapshot]).
 */
data class GleoReply(val success: Boolean, val message: String, val data: Map<String, Any?> = emptyMap())

/** 운전자 대신 수락을 요청했을 때의 결과 */
enum class AcceptOutcome { ADDED, APP_ONLY, WAYPOINTS_FULL, NOTHING_TO_ACCEPT, FAILED }

/** 함수마다 미리 만들어 둔 답과, 지금 "거기로 가자"(수락)를 받을 수 있는지. */
data class GleoSnapshot(
    val plan: GleoReply,
    val status: GleoReply,
    /** 수락을 기다리는 추천이 있으면 그 충전소 이름. 없으면 null. 거절(DECLINE_STOP)도 이 추천을 거절한다 */
    val acceptable: String?,
    /** EXPLAIN_RECOMMENDATION: 왜 그 충전소·그 목표 %인지(E) */
    val explain: GleoReply = GleoReplies.notRunning()
)

/** 운전자 대신 거절을 요청했을 때의 결과(E) */
enum class DeclineOutcome { DECLINED, NOTHING_TO_DECLINE }

object GleoReplies {

    fun snapshot(s: TripUiState) = GleoSnapshot(plan(s), status(s), acceptableName(s), explain(s))

    /**
     * EXPLAIN_RECOMMENDATION(E): "왜 거기야?". 엔진이 붙인 이유(가장 빨리 도착, 대기, 시간 제한, 우회)와 다음 후보와의 도착 시각 차이,
     * 실제 주행이면 순정 내비 충전소와의 비교로 답한다. 정차 중이면 목표 %의 이유를 답한다.
     */
    fun explain(s: TripUiState): GleoReply {
        noTrip(s)?.let { return it }
        s.stop?.takeIf { s.phase in TripPresenter.STOP_PHASES }?.let { st ->
            val next = st.nextStopName?.let { " It also gets you to the next stop, $it, with a safe margin." }
                ?: " That covers the rest of the trip to the destination plus a safety margin."
            val limit = if (st.willHitLimit && st.predictedSocAtLimit != null)
                " The charger time limit will stop it at about ${pct(st.predictedSocAtLimit)}, so Chargis will plan the next stop from there." else ""
            val car = st.carLimitPercent?.let { " The car's charge limit is set to $it% as a backup; Chargis stops charging at the target." }.orEmpty()
            return GleoReply(
                true,
                "The target is ${pct(st.targetSoc)} because charging beyond what you need only adds time.$next$limit$car",
                mapOf("station" to st.stationName, "targetSoc" to st.targetSoc.roundToInt())
            )
        }
        return when (val c = s.card) {
            is DriveCard.Recommend -> {
                val r = c.rec
                val reasons = buildList {
                    if (c.fasterThanNextMin != null) add("it gets you to the destination about ${c.fasterThanNextMin} minutes earlier than the next option")
                    else if (ReasonCode.FASTEST_ARRIVAL in r.reasonCodes) add("it gets you to the destination earliest")
                    if (r.waitSource != WaitSource.UNKNOWN && ReasonCode.LOW_WAIT in r.reasonCodes) add(if (r.waitMinutes == 0) "no wait is expected" else "the expected wait is short (about ${r.waitMinutes} minutes)")
                    if (ReasonCode.MAX_POWER in r.reasonCodes) add("its chargers are the fastest nearby (${r.station.maxKw.roundToInt()} kW)")
                    if (ReasonCode.LATEST_SAFE_STOP in r.reasonCodes) add("it is the last stop you can reach safely")
                    if (ReasonCode.NO_DETOUR in r.reasonCodes) add("there is no detour")
                }.ifEmpty { listOf("it is the best balance of driving, waiting and charging time") }
                val risk = if (ReasonCode.TIME_LIMIT_RISK in r.reasonCodes) " Note: its time limit may stop charging before the target." else ""
                val navi = c.navi?.let { n ->
                    val g = n.chargisFasterMin
                    when {
                        n.same -> " The car navigation picked the same stop."
                        !n.naviReachable -> " The car navigation picked ${n.naviName}, but you would arrive there below Chargis's 10% safety reserve."
                        g != null && g >= 1 -> " The car navigation picked ${n.naviName}; ${r.station.name} arrives about $g minutes earlier by Chargis's estimate."
                        g != null && g <= -1 -> " The car navigation picked ${n.naviName}, which may arrive about ${-g} minutes earlier."
                        else -> " The car navigation picked ${n.naviName}; the arrival time is about the same."
                    }
                }.orEmpty()
                GleoReply(
                    true,
                    "Chargis recommends ${r.station.name} because ${reasons.joinToString(", ")}. Charge up to ${pct(r.targetSocPercent)}: " +
                        "that is what the rest of the trip needs plus a safety margin.$risk$navi",
                    mapOf("station" to r.station.name, "targetSoc" to r.targetSocPercent.roundToInt(), "fasterThanNextMin" to c.fasterThanNextMin)
                )
            }
            is DriveCard.Congested -> GleoReply(
                true,
                "${c.primary.station.name} would normally be best, but about ${c.primary.waitMinutes} minutes of waiting is expected. " +
                    "With ${c.alternative.station.name} you reach the destination at ${c.alternativeEta} instead of ${c.primaryEta}.",
                mapOf("crowded" to c.primary.station.name, "alternative" to c.alternative.station.name)
            )
            is DriveCard.Navigating -> GleoReply(
                true,
                "You chose ${c.stationName}. Charging to ${pct(c.targetSoc)} covers the rest of the trip plus a safety margin; it takes about ${c.chargeMinutes} minutes.",
                stationData(c.stationName, c.distanceKm, c.targetSoc, c.chargeMinutes)
            )
            is DriveCard.Status, is DriveCard.NoSafeStop -> plan(s)
        }
    }

    /** DECLINE_STOP(E)의 실제 결과에 맞춘 답. */
    fun declineReply(name: String, outcome: DeclineOutcome) = when (outcome) {
        DeclineOutcome.DECLINED -> GleoReply(true, "Skipped $name. Chargis will suggest the next charging stop and will not mention $name again.", mapOf("station" to name))
        DeclineOutcome.NOTHING_TO_DECLINE -> GleoReply(false, "There is no charging stop to skip right now.")
    }

    /** GET_CHARGING_PLAN: 다음 충전을 어디서, 몇 %까지, 얼마나 하는지. */
    fun plan(s: TripUiState): GleoReply {
        noTrip(s)?.let { return it }
        s.stop?.takeIf { s.phase in TripPresenter.STOP_PHASES }?.let { st ->
            val next = st.nextStopName?.let { n -> " After this, charge once more at $n up to ${pct(st.nextStopTarget)}." }.orEmpty()
            return GleoReply(
                true,
                "You are at ${st.stationName}. Charge up to ${pct(st.targetSoc)}; it takes about ${st.minutesToTarget} minutes and you can leave around ${st.departAt}.$next",
                mapOf("station" to st.stationName, "targetSoc" to st.targetSoc.roundToInt(), "minutesToTarget" to st.minutesToTarget, "departAt" to st.departAt)
            )
        }
        return when (val c = s.card) {
            is DriveCard.Recommend -> c.rec.let { r ->
                GleoReply(
                    true,
                    "Next charging stop: ${r.station.name}, ${km(r.station.distanceAlongRouteKm)} ahead. Charge up to ${pct(r.targetSocPercent)}, " +
                        "about ${r.chargeMinutes} minutes. Arrival at the destination around ${c.etaClock}. Say 'go there' to add it as a waypoint.",
                    stationData(r.station.name, r.station.distanceAlongRouteKm, r.targetSocPercent, r.chargeMinutes) + ("eta" to c.etaClock)
                )
            }
            is DriveCard.Congested -> GleoReply(
                true,
                "${c.primary.station.name} is crowded (about ${c.primary.waitMinutes} minutes wait, arrival ${c.primaryEta}). " +
                    "${c.alternative.station.name} gets you to the destination at ${c.alternativeEta}. Say 'go there' to choose ${c.alternative.station.name}.",
                mapOf("crowded" to c.primary.station.name, "alternative" to c.alternative.station.name, "primaryEta" to c.primaryEta, "alternativeEta" to c.alternativeEta)
            )
            is DriveCard.Navigating -> GleoReply(
                true,
                "Heading to ${c.stationName}, ${km(c.distanceKm)} ahead. Charge up to ${pct(c.targetSoc)}, about ${c.chargeMinutes} minutes.",
                stationData(c.stationName, c.distanceKm, c.targetSoc, c.chargeMinutes)
            )
            is DriveCard.Status ->
                if (c.chargePlanned && c.plannedName != null) GleoReply(
                    true,
                    "A charging stop is needed. The best one now is ${c.plannedName}, ${km(c.plannedKm ?: 0.0)} ahead, up to ${pct(c.plannedTarget)}. " +
                        "Chargis will announce it when you get closer.",
                    stationData(c.plannedName, c.plannedKm ?: 0.0, c.plannedTarget ?: 0.0, c.plannedMinutes ?: 0)
                )
                else GleoReply(
                    true,
                    "No charging is needed to reach the destination. Battery is ${pct(s.socPercent)} with ${km(s.remainingKm)} to go.",
                    mapOf("soc" to s.socPercent.roundToInt(), "remainingKm" to s.remainingKm.roundToInt())
                )
            is DriveCard.NoSafeStop -> GleoReply(
                true,
                "Warning: no charging stop can be reached safely with the current battery." +
                    (c.nearestName?.let { " The nearest is $it, ${km(c.nearestKm ?: 0.0)} ahead." } ?: ""),
                mapOf("nearest" to c.nearestName)
            )
        }
    }

    /** GET_CHARGING_STATUS: 충전 중이면 목표·제한까지 남은 시간과 출발 예정 시각, 아니면 배터리와 남은 거리. */
    fun status(s: TripUiState): GleoReply {
        noTrip(s)?.let { return it }
        val st = s.stop
        if (s.phase == TripPhase.Charging && st != null) {
            val limit = st.minutesToLimit?.let { m ->
                if (st.willHitLimit) " The charger stops in $m minutes, at about ${pct(st.predictedSocAtLimit)}." else " Time limit: $m minutes left."
            }.orEmpty()
            // 동승자 질문("언제 출발해?", "얼마나 쉬어도 돼?")에도 답하도록 출발까지 남은 분을 따로 준다.
            val untilDepart = st.minutesUntilDepart
            return GleoReply(
                true,
                "Charging at ${st.stationName}: ${pct(st.currentSoc)} now, target ${pct(st.targetSoc)}, about ${st.minutesToTarget} minutes left. " +
                    "You can leave around ${st.departAt}, so passengers have about $untilDepart minutes for a break.$limit",
                mapOf("soc" to st.currentSoc.roundToInt(), "targetSoc" to st.targetSoc.roundToInt(), "minutesToTarget" to st.minutesToTarget,
                    "minutesToLimit" to st.minutesToLimit, "departAt" to st.departAt, "minutesUntilDepart" to untilDepart)
            )
        }
        // 충전소에 도착했지만 아직 충전 전: 지금 시작하면 언제 떠나는지 알려 준다.
        if (s.phase == TripPhase.Arrived && st != null) {
            val untilDepart = st.minutesUntilDepart
            return GleoReply(
                true,
                "Arrived at ${st.stationName}. Charging to ${pct(st.targetSoc)} takes about $untilDepart minutes once it starts, " +
                    "so you can leave around ${st.departAt}.",
                mapOf("station" to st.stationName, "targetSoc" to st.targetSoc.roundToInt(), "minutesUntilDepart" to untilDepart, "departAt" to st.departAt)
            )
        }
        if (s.phase == TripPhase.Done && st != null) return GleoReply(
            true, "Charging is done at ${pct(st.currentSoc)}. You can depart now.", mapOf("soc" to st.currentSoc.roundToInt())
        )
        return GleoReply(
            true,
            "Battery is ${pct(s.socPercent)}, ${km(s.remainingKm)} to the destination, arriving around ${s.eta}.",
            mapOf("soc" to s.socPercent.roundToInt(), "remainingKm" to s.remainingKm.roundToInt(), "eta" to s.eta)
        )
    }

    /** "거기로 가자": 안내한 추천이 있을 때만 받는다. 혼잡 비교 중이면 더 빨리 도착하는 대안을 고른다(음성 명령 "수락"과 같다). */
    fun acceptableName(s: TripUiState): String? = if (s.phase != TripPhase.AwaitingDecision) null else when (val c = s.card) {
        is DriveCard.Recommend -> c.rec.station.name
        is DriveCard.Congested -> c.alternative.station.name
        else -> null
    }

    /** "거기로 가자"의 실제 결과([TripController.acceptForAssistant])에 맞춘 답. */
    fun acceptReply(name: String, outcome: AcceptOutcome) = when (outcome) {
        AcceptOutcome.ADDED -> GleoReply(true, "Added $name as a navigation waypoint. Chargis will guide the charging there.", mapOf("station" to name))
        AcceptOutcome.APP_ONLY -> GleoReply(
            true, "Chose $name as the next charging stop. The navigation has no destination, so no waypoint was added; Chargis will guide you.", mapOf("station" to name)
        )
        AcceptOutcome.WAYPOINTS_FULL -> GleoReply(false, "Could not add $name: both navigation waypoint slots are already used. Remove one waypoint and try again.")
        AcceptOutcome.NOTHING_TO_ACCEPT -> cannotAccept(true)
        AcceptOutcome.FAILED -> GleoReply(false, "Could not add $name as a waypoint. Please add it in the navigation.")
    }
    fun cannotAccept(running: Boolean) = GleoReply(
        false,
        if (running) "There is no charging stop to accept right now. Ask for the charging plan first."
        else "Chargis is not open. Open Chargis to continue the trip."
    )
    fun notRunning() = GleoReply(false, "Chargis has no trip information yet. Open Chargis and start a trip.")

    private fun noTrip(s: TripUiState): GleoReply? = when (s.phase) {
        TripPhase.Idle -> GleoReply(false, "No trip is in progress in Chargis. Start a trip in Chargis first.")
        TripPhase.Finished -> GleoReply(true, "You have arrived at the destination. The trip is finished.")
        else -> null
    }

    private fun stationData(name: String, km: Double, target: Double, minutes: Int) =
        mapOf("station" to name, "distanceKm" to km.roundToInt(), "targetSoc" to target.roundToInt(), "chargeMinutes" to minutes)

    private fun pct(v: Double?) ="${(v ?: 0.0).roundToInt()}%"
    private fun km(v: Double) = "${v.roundToInt()} km"
}
