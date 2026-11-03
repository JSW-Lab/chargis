package com.chargis.app.ui

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
    /** 수락을 기다리는 추천이 있으면 그 충전소 이름. 없으면 null */
    val acceptable: String?
)

object GleoReplies {

    fun snapshot(s: TripUiState) = GleoSnapshot(plan(s), status(s), acceptableName(s))

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
            return GleoReply(
                true,
                "Charging at ${st.stationName}: ${pct(st.currentSoc)} now, target ${pct(st.targetSoc)}, about ${st.minutesToTarget} minutes left. " +
                    "You can leave around ${st.departAt}.$limit",
                mapOf("soc" to st.currentSoc.roundToInt(), "targetSoc" to st.targetSoc.roundToInt(), "minutesToTarget" to st.minutesToTarget,
                    "minutesToLimit" to st.minutesToLimit, "departAt" to st.departAt)
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

    private fun pct(v: Double?) = "${(v ?: 0.0).roundToInt()}%"
    private fun km(v: Double) = "${v.roundToInt()} km"
}
