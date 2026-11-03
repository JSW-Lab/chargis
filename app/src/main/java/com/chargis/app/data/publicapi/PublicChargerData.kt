package com.chargis.app.data.publicapi

import com.chargis.app.domain.model.ChargerRule
import com.chargis.app.domain.model.ChargingStation
import com.chargis.app.domain.model.ConnectorType
import com.chargis.app.domain.model.RouteState
import com.chargis.app.domain.model.StationKind
import com.chargis.app.port.ChargerDataPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime

/*
 * B5: 공공 충전소 데이터(한국환경공단 "전기자동차 충전소 정보" API, data.go.kr) 어댑터.
 *
 * 흐름: 시도별 충전기 목록(getChargerInfo)과 상태(getChargerStatus)를 받아
 *   → 충전소(statId)별로 묶고 → 누구나 쓸 수 있는 급속 충전소만 남기고
 *   → 지금 경로 위로 투영해 "경로상 몇 km 앞, 편도 몇 km 우회"로 바꾼다.
 * 목록은 자주 바뀌지 않아 오래(기본 12시간), 상태는 짧게(기본 2분) 캐시한다.
 *
 * 후보로 남기는 충전소: 고속도로·국도 휴게소, 졸음쉼터, 상업시설(마트·주유소 등), 공공시설, 관광지 등.
 * 빼는 충전소: 완속, 아파트·사업장·학교처럼 외부인이 쓰기 어려운 시설, "입주민 전용" 같은 이용자 제한이 걸린 곳.
 *
 * limitYn/limitDetail은 충전 시간 제한이 아니라 "이용자 제한 여부/사유"다(data.go.kr 서비스 설명의
 * "이용자 제한 여부" 항목). 그래서 이용자 제한 판단에 쓰고, 시간 제한은 "1회 40분 이내"처럼 분 단위 제한이
 * 분명히 적힌 문구가 있을 때만 꺼낸다. 없으면 시간 제한 "정보 없음"으로 둔다.
 *
 * 주의: 응답 필드 이름과 시설 구분 코드는 공개 문서와 공개 구현 사례 기준으로 맞췄고 실제 키로 호출해
 * 확인하지 못했다(docs/DECISIONS.md). 그래서 파서는 숫자가 글자로 오거나 항목이 빠져도 넘어가도록 너그럽게 만들었다.
 */

/** HTTP 호출만 맡는 얇은 층. 테스트에서는 가짜로 바꿔 끼운다. */
fun interface EvChargerHttp {
    suspend fun get(operation: String, params: Map<String, String>): String
}

/**
 * 충전소 API 호출 구현.
 * - [baseUrl]: 기본은 data.go.kr. 키를 앱에 넣지 않으려면 키를 붙여 주는 중계 서버(프록시) 주소를 넣는다.
 * - [serviceKey]: data.go.kr 키. 비어 있으면 붙이지 않는다(프록시가 붙인다고 본다).
 * - [appToken]: 중계 서버(server/charger-proxy.mjs)에 APP_TOKEN을 정했으면 같은 값을 X-Chargis-Token 헤더로 보낸다.
 * 앱에 넣은 키는 APK에서 꺼낼 수 있으므로, 배포용(release) 빌드는 키를 넣지 않고 프록시를 쓴다(app/build.gradle.kts).
 * 오류 메시지에는 주소를 넣지 않는다(키가 로그에 남지 않게).
 */
class UrlEvChargerHttp(
    private val serviceKey: String?,
    private val baseUrl: String = DEFAULT_BASE,
    private val timeoutMs: Int = 20_000,   // 9,999행 한 쪽이 약 8MB·5초(2026-11-03 실측)라 차량 통신에서 넉넉히 둔다
    private val appToken: String? = null
) : EvChargerHttp {
    override suspend fun get(operation: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val conn = URL(urlFor(operation, params)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            appToken?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty(TOKEN_HEADER, it) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} ($operation)")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 호출 주소. 테스트에서 키가 붙는지 확인할 수 있게 따로 뺐다. */
    fun urlFor(operation: String, params: Map<String, String>): String {
        val key = serviceKey?.takeIf { it.isNotBlank() }?.let { mapOf("serviceKey" to decodeServiceKey(it)) }.orEmpty()
        val query = (params + key + mapOf("dataType" to "JSON"))
            .entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8.name())}" }
        return "${baseUrl.trimEnd('/')}/$operation?$query"
    }

    companion object {
        /**
         * data.go.kr은 같은 키를 Encoding(%2B 등)과 Decoding 두 형식으로 보여 준다. 주소를 만들 때 한 번 인코딩하므로,
         * Encoding 값을 넣으면 두 번 인코딩되어 "등록되지 않은 서비스키"가 된다(2026-11-03 실제로 겪음). 어느 쪽을 넣어도 되게 푼다.
         */
        fun decodeServiceKey(key: String): String {
            val k = key.trim()
            return if ('%' in k) runCatching { java.net.URLDecoder.decode(k, Charsets.UTF_8.name()) }.getOrDefault(k) else k
        }

        const val DEFAULT_BASE = "https://apis.data.go.kr/B552584/EvCharger"
        const val TOKEN_HEADER = "X-Chargis-Token"
    }
}

/**
 * 공공데이터 기반 [ChargerDataPort].
 * [zcodes]를 주면 그 시도만, 비워 두면(null) 경로가 지나는 시도를 [SidoCodes]로 그때그때 고른다.
 * 목록·상태는 시도마다 따로 캐시해서, 경로가 새 시도로 넘어가면 그 시도만 새로 받는다.
 * [nowMillis]는 캐시 만료 판단용 시계(테스트에서 바꿔 끼운다).
 */
class PublicChargerData(
    private val http: EvChargerHttp,
    private val zcodes: List<String>? = null,
    private val corridorKm: Double = 3.0,
    private val rules: ChargerRuleTable = ChargerRuleTable.EMPTY,
    private val infoTtlMs: Long = 12 * 60 * 60 * 1000L,
    private val statusTtlMs: Long = 2 * 60 * 1000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nowLocal: () -> LocalDateTime = LocalDateTime::now
) : ChargerDataPort {
    private val mutex = Mutex()
    private val info = mutableMapOf<String, Cached<List<ChargerItem>>>()
    private val status = mutableMapOf<String, Cached<List<ChargerStatus>>>()
    private var lastZones: List<String> = emptyList()
    private var lastConnectors: Set<ConnectorType>? = null
    private var lastStations: List<PublicStation> = emptyList()

    private class Cached<T>(val value: T, val at: Long)

    override suspend fun stationsAlongRoute(route: RouteState, connectors: Set<ConnectorType>?): List<ChargingStation> {
        val stations = load(zcodes ?: SidoCodes.along(route), connectors)
        return stations.mapNotNull { st ->
            // 반대 방향 휴게소("(서울방향)" 등)는 본선에서 들어갈 수 없으므로 뺀다.
            if (st.kind.isRoadside && RestStopDirection.isOpposite(st.name, st.location, route)) return@mapNotNull null
            val (along, detour) = RouteProjector.project(route, st.location, corridorKm) ?: return@mapNotNull null
            ChargingStation(
                id = st.id,
                name = st.name,
                location = st.location,
                kind = st.kind,
                distanceAlongRouteKm = along,
                // 휴게소·쉼터는 본선에 붙어 있어 직선 경로와의 거리가 투영 오차일 뿐이므로 우회 0으로 본다.
                detourKm = if (st.kind == StationKind.HIGHWAY_REST_STOP || st.kind == StationKind.SHELTER) 0.0 else detour,
                maxKw = st.maxKw,
                totalChargers = st.totalChargers,
                availableChargers = st.availableChargers,
                expectedWaitMin = st.expectedWaitMin,
                // 시간 제한: 안내 문구에서 읽은 값 → 규칙표 → 둘 다 없으면 "정보 없음"
                rule = (st.timeLimitMin ?: rules.timeLimitFor(st.operatorId, st.kind)).let { limit ->
                    ChargerRule(timeLimitMin = limit, amountLimitKrw = null, note = st.note, timeLimitKnown = limit != null)
                },
                openHours = st.openHours,
                parkingFree = st.parkingFree,
                connectors = st.connectors,
                // 요금: 공공데이터에 없어 규칙표에서만 찾는다. 없으면 "정보 없음"
                pricePerKwh = rules.priceFor(st.operatorId, st.kind),
                brokenChargers = st.brokenChargers,
                hoursSinceLastCharge = st.hoursSinceLastCharge
            )
        }.sortedBy { it.distanceAlongRouteKm }
    }

    /**
     * [zones] 시도의 충전소. 시도마다 캐시가 살아 있으면 그대로, 만료됐으면 그 시도만 다시 받는다.
     * 상태만 실패하면 목록 API의 상태로 버틴다. 아무것도 새로 받지 않았고 시도도 같으면 지난 결과를 그대로 쓴다.
     */
    private suspend fun load(zones: List<String>, connectors: Set<ConnectorType>?): List<PublicStation> = mutex.withLock {
        val now = nowMillis()
        var changed = zones != lastZones || connectors != lastConnectors
        val items = zones.flatMap { z ->
            info[z]?.takeIf { now - it.at < infoTtlMs }?.value
                ?: StationKinds.FETCH_KINDS.flatMap { k -> fetch(z, "getChargerInfo", EvChargerParser::parseInfo, mapOf("kind" to k)) }
                    .also { info[z] = Cached(it, now); changed = true }
        }
        val statuses = zones.flatMap { z ->
            status[z]?.takeIf { now - it.at < statusTtlMs }?.value
                ?: runCatching { fetch(z, "getChargerStatus", EvChargerParser::parseStatus, mapOf("period" to "10")) }
                    .getOrDefault(emptyList()).also { status[z] = Cached(it, now); changed = true }
        }
        if (changed) {
            lastZones = zones
            lastConnectors = connectors
            lastStations = StationAggregator.aggregate(items, statuses, nowLocal(), connectors)
        }
        lastStations
    }

    /**
     * 한 시도(목록은 시설 구분 하나)를 끝까지 받는다. 한 번에 [PAGE_ROWS]개까지만 오므로 모자라게 온 쪽까지 쪽을 넘긴다.
     * 실제로 강원(51)은 충전기 18,055대라 한 쪽만 받으면 절반이 빠진다(2026-11-03 실키 확인).
     */
    private suspend fun <T> fetch(zone: String, op: String, parse: (String) -> List<T>, extra: Map<String, String> = emptyMap()): List<T> {
        val all = mutableListOf<T>()
        for (page in 1..MAX_PAGES) {
            val body = http.get(op, extra + mapOf("pageNo" to "$page", "numOfRows" to "$PAGE_ROWS", "zcode" to zone))
            EvChargerParser.checkResult(body)
            val rows = parse(body)
            all += rows
            if (rows.size < PAGE_ROWS) break
        }
        return all
    }

    private companion object {
        const val PAGE_ROWS = 9999   // data.go.kr이 한 번에 주는 최대 행 수
        const val MAX_PAGES = 3      // 시설 구분 하나는 경기도 상업시설(약 7,600대)도 한 쪽에 들어온다. 넉넉히 3쪽까지
    }
}

/**
 * 1순위 공급원이 실패하거나 빈 목록을 주면 예비 공급원으로 넘어간다.
 * 예) 공공데이터 → (네트워크 오류) → 번들 샘플 데이터
 */
class FallbackChargerData(
    private val primary: ChargerDataPort,
    private val fallback: ChargerDataPort,
    private val onFallback: (Throwable?) -> Unit = {}
) : ChargerDataPort {
    override suspend fun stationsAlongRoute(route: RouteState, connectors: Set<ConnectorType>?): List<ChargingStation> {
        val result = runCatching { primary.stationsAlongRoute(route, connectors) }
        val list = result.getOrNull()
        if (list != null && list.isNotEmpty()) return list
        onFallback(result.exceptionOrNull())
        return fallback.stationsAlongRoute(route, connectors)
    }
}
