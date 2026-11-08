package com.chargis.app.data.sdk

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteOverviewShowInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.chargis.app.domain.engine.TurnAngles
import com.chargis.app.domain.model.ChargingStation
import com.chargis.app.domain.model.GeoPoint
import com.chargis.app.domain.model.RouteState
import com.chargis.app.domain.model.TurnHint
import com.chargis.app.port.LiveRoutePort
import com.chargis.app.port.NaviChargingStop
import com.chargis.app.port.NaviDestination
import com.chargis.app.port.NaviPort
import com.chargis.app.port.TripEstimate
import com.chargis.app.port.TripEstimator
import com.chargis.app.port.TurnGuidePort
import com.chargis.app.port.WaypointsFullException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Pleos 순정 내비(NaviHelper SDK) 연결.
 * - B7: 수락한 충전소를 내비 경유지로 넣고 뺀다. 경유지는 두 자리뿐이라, 다른 곳으로 차 있으면 [WaypointsFullException]을 돌려준다.
 * - B8: 충전소까지의 실제 도로 거리·도착 시간·도착 배터리를 묻는다(requestTrip).
 *
 * NaviHelper는 요청 함수를 부르면 결과가 리스너로 따로 온다. 요청마다 짝을 맞출 번호가 없어 [mutex]로 한 번에 하나씩만 묻는다.
 * 퍼미션: NAVI_ROUTE(get*Info·requestTrip), NAVI_ROUTE_SEARCH(경유지 추가·삭제·재탐색).
 */
class PleosNavi(context: Context) : TripEstimator, LiveRoutePort, TurnGuidePort {
    private val helper = NaviHelper(context.applicationContext)
    private val mutex = Mutex()
    @Volatile private var ready = false
    @Volatile private var trip: CompletableDeferred<TripInfo?>? = null
    @Volatile private var waypoints: CompletableDeferred<List<WaypointInfo>>? = null
    @Volatile private var location: CompletableDeferred<CurrentLocationInfo>? = null
    @Volatile private var destination: CompletableDeferred<DestinationInfo?>? = null

    private val _chargingStop = MutableStateFlow<NaviChargingStop?>(null)
    /** 순정 내비가 경로에 스스로 넣은 충전 경유지(B·C). 없으면 null */
    val chargingStop: StateFlow<NaviChargingStop?> = _chargingStop.asStateFlow()

    /** 바로 전 [addWaypoint] 때 경로에 있던 순정 내비 자체 충전 경유지 이름(Chargis 충전소와 다를 때만). 없으면 null */
    @Volatile var lastNaviOwnStop: String? = null
        private set

    private val _turn = MutableStateFlow<TurnHint?>(null)
    /** 순정 내비가 안내 중인 다음 갈림길(TBT). 길 안내 중이 아니거나 모르는 종류면 null */
    override val turn: StateFlow<TurnHint?> = _turn.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onTripInfo(tripInfo: TripInfo) { trip?.complete(tripInfo) }
        // 다음 갈림길 목록. 첫 항목이 가장 가까운 갈림길이다. distance는 미터로 온다.
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val first = tbtInfoList.firstOrNull()
            val hint = first?.let { TurnAngles.fromNavi(it.type.name, it.distance / 1000.0, it.description) }
            if (hint?.maneuver != _turn.value?.maneuver) Log.i(TAG, "tbt ${first?.type} ${first?.distance}m '${first?.description}' -> $hint")
            _turn.value = hint
        }
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val stop = chargingStopOf(drivingInfo)
            if (stop != _chargingStop.value) Log.i(TAG, "navi charging stop: $stop")
            _chargingStop.value = stop
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { destination?.complete(destinationInfo) }
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) { waypoints?.complete(waypointInfoList) }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) { location?.complete(currentLocationInfo) }
        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
            if (errorCode == NaviErrorCode.CAUSE_SEARCH_TRIP) trip?.complete(null)
        }
    }

    /** 내비 서비스에 연결한다. Pleos가 아니거나 연결하지 못하면 false. */
    suspend fun connect(): Boolean {
        if (ready) return true
        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    helper.initialize({ if (cont.isActive) cont.resume(true) }, { e -> Log.w(TAG, "init failed", e); if (cont.isActive) cont.resume(false) })
                }.onFailure { Log.w(TAG, "init threw", it); if (cont.isActive) cont.resume(false) }
            }
        } ?: false
        if (ok) helper.addListener(listener)
        ready = ok
        return ok
    }

    val isReady: Boolean get() = ready

    override fun requestTurn() {
        if (!ready) return
        runCatching { helper.getTBTInfo() }.onFailure { Log.w(TAG, "getTBTInfo failed", it) }
    }

    override suspend fun showRouteOverview(): Result<Unit> {
        if (!ready) return Result.failure(IllegalStateException("navi not connected"))
        return runCatching { helper.requestNavigationScreen(NavigationScreenRequest.OverviewRoute(RouteOverviewShowInfo())) }
            .onFailure { Log.w(TAG, "route overview failed", it) }
    }

    override suspend fun currentLocation(): GeoPoint? = ask<CurrentLocationInfo>({ location = it }) { helper.getCurrentLocationInfo() }
        ?.let { GeoPoint(it.latitude, it.longitude) }
        ?.also { Log.d(TAG, "location ${it.lat},${it.lon}") }

    /** 순정 내비에 설정된 목적지. 목적지가 없거나(답이 없거나 좌표·남은 거리가 0) 연결 전이면 null. */
    override suspend fun destination(): NaviDestination? = ask<DestinationInfo?>({ destination = it }) { helper.getDestinationInfo() }
        ?.takeIf { (it.latitude != 0.0 || it.longitude != 0.0) && it.remainDistance > 0 }
        ?.let { NaviDestination(it.name, GeoPoint(it.latitude, it.longitude), it.remainDistance / 1000.0, (it.remainTime / 60.0).roundToInt()) }

    override suspend fun estimate(station: ChargingStation): TripEstimate? {
        val info = ask<TripInfo?>({ trip = it }) {
            helper.requestTrip(TripRequest(latitude = station.location.lat, longitude = station.location.lon, poiId = poiId(station), poiName = station.name, poiSubId = "0"))
        } ?: return null
        Log.i(TAG, "trip ${station.id}: distance=${info.distance} eta=${info.eta} soc=${info.estimatedSocAtDest}")
        return toEstimate(info.distance, info.eta, info.estimatedSocAtDest)
    }

    /** 경유지 추가. 이미 우리가 넣은 곳이면 그대로 성공, 두 자리가 다른 곳으로 차 있으면 [WaypointsFullException]. */
    suspend fun addWaypoint(station: ChargingStation): Result<Unit> {
        if (!ready) return Result.failure(IllegalStateException("navi not connected"))
        lastNaviOwnStop = null
        val current = ask<List<WaypointInfo>>({ waypoints = it }) { helper.getWaypointInfo() } ?: emptyList()
        if (current.any { it.matches(station) }) return Result.success(Unit)
        // 순정 내비가 스스로 넣은 충전 경유지는 getWaypointInfo에 나오지 않고 뺄 수도 없다(에뮬레이터 실측). 이름만 기억해 안내에 쓴다.
        lastNaviOwnStop = _chargingStop.value?.name?.takeIf { it != station.name }
        if (current.size >= MAX_WAYPOINTS) return Result.failure(WaypointsFullException())
        val slot = if (current.isEmpty()) WaypointIndex.FIRST else WaypointIndex.SECOND
        return runCatching {
            helper.addWaypoint(
                RequestWaypointInfo(
                    latitude = station.location.lat, longitude = station.location.lon, poiName = station.name, waypointIndex = slot,
                    poiId = poiId(station), address = "", poiSubId = "0", poiProvider = ""
                )
            )
        }
    }

    /** 우리가 넣은 경유지만 찾아 뺀다. 내비에서 이미 지웠으면 그대로 성공. */
    suspend fun removeWaypoint(station: ChargingStation): Result<Unit> {
        if (!ready) return Result.failure(IllegalStateException("navi not connected"))
        val current = ask<List<WaypointInfo>>({ waypoints = it }) { helper.getWaypointInfo() } ?: return Result.success(Unit)
        val index = current.indexOfFirst { it.matches(station) }
        if (index < 0) return Result.success(Unit)
        return runCatching { helper.removeWaypoint(if (index == 0) WaypointIndex.FIRST else WaypointIndex.SECOND) }
    }

    fun requestReRoute(): Result<Unit> = if (ready) runCatching { helper.requestReRoute() } else Result.failure(IllegalStateException("navi not connected"))

    fun release() {
        if (!ready) return
        runCatching { helper.removeListener(listener); helper.release() }
        ready = false
    }

    // 요청 하나를 보내고 리스너로 올 답을 기다린다. 시간 안에 오지 않으면 null.
    private suspend fun <T> ask(slot: (CompletableDeferred<T>?) -> Unit, request: () -> Unit): T? {
        if (!ready) return null
        return mutex.withLock {
            val d = CompletableDeferred<T>()
            slot(d)
            try {
                runCatching(request).onFailure { Log.w(TAG, "request failed", it); return@withLock null }
                withTimeoutOrNull(ANSWER_TIMEOUT_MS) { d.await() }
            } finally {
                slot(null)
            }
        }
    }

    private fun WaypointInfo.matches(station: ChargingStation) =
        poiId == poiId(station) || (name == station.name && RoadDistanceRefiner.distanceKm(GeoPoint(latitude, longitude), station.location) < 0.3)

    companion object {
        private const val TAG = "ChargisNavi"
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val ANSWER_TIMEOUT_MS = 3_000L
        const val MAX_WAYPOINTS = 2
        private const val POI_PREFIX = "chargis-"

        /** onDrivingInfo에서 순정 내비가 넣은 충전 경유지를 꺼낸다. 경로가 없으면(거리 0) null. */
        fun chargingStopOf(info: DrivingInfo): NaviChargingStop? = info.waypoints
            .firstOrNull { it.isChargingStation && it.distance > 0 }
            ?.let { w ->
                NaviChargingStop(
                    name = w.name,
                    distanceKm = w.distance / 1000.0,
                    arrivalKwh = w.remainDestinationChargingValue.takeIf { it > 0 }?.let { it / 1000.0 },
                    maxKw = w.maxChargingPower.toDouble(),
                    chargers = w.chargerCount
                )
            }

        /** 내비에 넣는 POI id. 우리가 넣은 경유지를 다시 찾을 때 쓴다. */
        fun poiId(station: ChargingStation) = "$POI_PREFIX${station.id}"

        /** TripInfo 값(거리 m, 도착 시간 초, 도착 배터리 %)을 앱 단위로. 배터리를 모르면(음수) null. */
        fun toEstimate(distanceM: Int, etaSec: Int, socAtDest: Int): TripEstimate =
            TripEstimate(distanceKm = distanceM / 1000.0, etaMin = (etaSec / 60.0).roundToInt(), arrivalSocPercent = socAtDest.takeIf { it in 0..100 })
    }
}

/**
 * 하이브리드 내비: 경로·위치는 데모 시뮬레이터([demo])가 맡고, 수락한 경유지는 순정 내비([pleos])에도 넣는다.
 * 순정 내비에 연결하지 못했거나 일시적으로 실패하면 데모만으로 계속한다. 다만 내비 경유지 두 자리가 다른 곳으로 차 있으면
 * 데모 쪽도 되돌리고 실패를 돌려준다(운전자에게 내비에서 직접 정리하라고 안내해야 하므로).
 */
class HybridNavi(private val demo: NaviPort, private val pleos: PleosNavi) : NaviPort {
    override val routeState: Flow<RouteState?> = demo.routeState

    @Volatile override var lastAddSkippedNavi: Boolean = false
        private set
    @Volatile override var lastNaviOwnStop: String? = null
        private set

    override suspend fun addWaypoint(station: ChargingStation): Result<Unit> {
        lastAddSkippedNavi = false
        lastNaviOwnStop = null
        demo.addWaypoint(station).onFailure { return Result.failure(it) }
        if (!pleos.isReady) return Result.success(Unit)
        // 순정 내비에 목적지가 없으면 경유지를 넣지 않는다. 넣으면 내비가 그 충전소를 목적지처럼 안내하려고 앞으로 나와
        // Chargis가 뒤로 가 버린다(2026-10-28 Pleos 에뮬레이터). 그때는 Chargis 안에서만 안내한다.
        if (pleos.destination() == null) {
            lastAddSkippedNavi = true
            Log.i("ChargisNavi", "navi has no destination; waypoint kept in app only")
            return Result.success(Unit)
        }
        val real = pleos.addWaypoint(station)
        lastNaviOwnStop = pleos.lastNaviOwnStop
        val full = real.exceptionOrNull() as? WaypointsFullException
        if (full != null) {
            demo.removeWaypoint(station)
            return Result.failure(full)
        }
        real.onFailure { Log.w("ChargisNavi", "navi waypoint add failed; demo continues", it) }
        return Result.success(Unit)
    }

    override suspend fun removeWaypoint(station: ChargingStation): Result<Unit> {
        if (pleos.isReady) pleos.removeWaypoint(station).onFailure { Log.w("ChargisNavi", "navi waypoint remove failed", it) }
        return demo.removeWaypoint(station)
    }

    override suspend fun requestReRoute(): Result<Unit> {
        // 내비에 목적지가 없어 경유지를 넣지 않았으면 다시 탐색도 하지 않는다. 부르면 내비가 앞으로 나와 Chargis를 가린다.
        if (pleos.isReady && !lastAddSkippedNavi) pleos.requestReRoute()
        return demo.requestReRoute()
    }
}
