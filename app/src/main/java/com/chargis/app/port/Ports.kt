package com.chargis.app.port

import com.chargis.app.domain.model.ChargingStation
import com.chargis.app.domain.model.ConnectorType
import com.chargis.app.domain.model.GeoPoint
import com.chargis.app.domain.model.Priority
import com.chargis.app.domain.model.RouteState
import com.chargis.app.domain.model.TurnHint
import com.chargis.app.domain.model.UserSettings
import com.chargis.app.domain.model.VehicleState
import com.chargis.app.domain.model.VoiceCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * "포트(Port)"는 앱이 바깥 세상(차량, 내비, 충전소 서버, 스피커)과 연결되는 콘센트 모양을 정해 둔 것이다.
 * interface는 "이런 기능이 있어야 한다"는 약속만 적고, 실제 동작은 따로 만든 구현체가 채운다.
 *
 * 덕분에 같은 앱 코드에 플러그만 바꿔 꽂을 수 있다.
 * - 데모/테스트: 가짜 데이터를 내보내는 Fake 구현체 (data/fake/Fakes.kt)
 * - 실제 차량: 현대차그룹 SDK를 쓰는 구현체 (SDK 제공 시 추가 예정)
 *
 * Flow = 시간이 흐르며 새 값이 계속 흘러나오는 데이터 줄기. 예) 배터리 %가 바뀔 때마다 새 값 도착
 * suspend fun = 결과가 나올 때까지 기다릴 수 있는 함수(네트워크 요청처럼 시간이 걸리는 작업)
 */

/** 내비게이션 연결. 현재 경로를 읽고, 충전소를 경유지로 추가할 수 있다. */
interface NaviPort {
    val routeState: Flow<RouteState?>
    suspend fun addWaypoint(station: ChargingStation): Result<Unit>   // F4: 경유지 추가 + 재탐색
    suspend fun removeWaypoint(station: ChargingStation): Result<Unit> // 수락한 경유지를 다른 곳으로 바꿀 때 이전 것을 지운다
    suspend fun requestReRoute(): Result<Unit>                        // 경로 다시 찾기

    /** 바로 전 [addWaypoint]가 순정 내비에 목적지가 없어 내비 경유지를 넣지 않고 앱 안에서만 성공했는지. */
    val lastAddSkippedNavi: Boolean get() = false

    /**
     * D1: 앱 안에서만 넣었던 경유지를 순정 내비에 목적지가 생겼으면 이제 넣는다. 넣었으면 true.
     * 목적지가 아직 없거나 내비가 없으면 false(다음에 다시 부른다). 경유지 두 자리가 차 있으면 실패를 돌려준다.
     */
    suspend fun syncSkippedWaypoint(station: ChargingStation): Result<Boolean> = Result.success(false)

    /**
     * 바로 전 [addWaypoint] 때 순정 내비가 스스로 넣은 충전 경유지가 경로에 있었으면 그 충전소 이름(B). 아니면 null.
     * 2026-11-06 Pleos 에뮬레이터: 이 경유지는 getWaypointInfo에 나오지 않고, removeWaypoint·재탐색 뒤에도 내비가 다시 넣어
     * 공개 API로는 뺄 수 없다. 그래서 Chargis 충전소를 넣고 운전자에게 내비 충전소는 들르지 않아도 된다고 알린다.
     */
    val lastNaviOwnStop: String? get() = null
}

/**
 * 순정 내비가 경로에 스스로 넣은 충전 경유지(NaviHelper onDrivingInfo의 isChargingStation 경유지).
 * 2026-11-06 Pleos 에뮬레이터: 남은 에너지(EV_BATTERY_LEVEL)가 모자라면 내비가 충전소를 경유지로 넣는다. 목표 %·충전 시간(chargingPlan)은 주지 않는다.
 * [arrivalKwh]: 그 충전소에 도착할 때 남는 에너지(kWh, 내비 값 Wh ÷ 1000). 모르면 null.
 */
data class NaviChargingStop(
    val name: String,
    val distanceKm: Double,
    val arrivalKwh: Double?,
    val maxKw: Double,
    val chargers: Int
)

/**
 * 순정 내비의 턴 안내(NaviHelper TBT)와 지도 화면 열기. Pleos 차량에서만 있다.
 * [turn]: 순정 내비가 안내 중인 다음 갈림길. 길 안내 중이 아니거나 모르면 null.
 */
interface TurnGuidePort {
    val turn: StateFlow<TurnHint?>
    /** 다음 갈림길을 다시 묻는다. 답은 [turn]으로 온다. */
    fun requestTurn()
    /** 순정 내비의 경로 전체 화면을 연다(지도는 순정 내비가 그린다). */
    suspend fun showRouteOverview(): Result<Unit>
}

/** 차량 연결. 배터리 %와 주행/정차 상태를 읽는다. */
interface VehiclePort {
    val vehicleState: Flow<VehicleState>        // SoC를 못 받으면 앱이 운전자 입력으로 추정한다(ManualSocEstimator)
    suspend fun isSupported(): Boolean          // SoC 수신 가능 여부

    /** 앱에서 배터리 예열을 켤 수 있는지. false면 [배터리 예열 켜기] 버튼과 "예열" 음성 명령 없이 차량 화면에서 켜 달라고만 안내한다. */
    val canControlPreconditioning: Boolean get() = false

    /** 배터리 예열(컨디셔닝)을 켜거나 끈다. 차량이 지원하지 않거나 SDK에 기능이 없으면 실패를 돌려준다. */
    suspend fun requestPreconditioning(on: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("preconditioning not supported"))
}

/**
 * 실제 차량이 알려 주는 신호(Pleos Vehicle SDK + 안드로이드 차량 주행 제한). 하이브리드 데모에서 쓴다:
 * 경로·시계는 데모 시뮬레이터가 맡고, 이 신호들이 발권 시작 배터리·일시정지·충전 시작/종료·화면 제한을 정한다.
 * 값을 모르면 null이다. Pleos가 아닌 기기에서는 [available]이 false이고 모든 값이 null로 남는다.
 */
interface CarSignalsPort {
    val available: StateFlow<Boolean>
    /** 배터리 잔량(%, 정수) */
    val socPercent: StateFlow<Int?>
    /** 기어가 P면 true, D·R·N이면 false */
    val parked: StateFlow<Boolean?>
    /** 충전 스위치(충전 중)가 켜졌으면 true */
    val chargeSwitchOn: StateFlow<Boolean?>
    /** 주행 중 화면 제한(CarUxRestrictions + ActiveRestrictions)을 기능별로 옮긴 값 */
    val uxLimits: StateFlow<UxLimits>
}

/**
 * 차량 충전 제어(Pleos Vehicle SDK EvBattery, 퍼미션 CONTROL_CAR_ENERGY).
 * 충전 한도는 안전장치로 넣고([com.chargis.app.domain.engine.ChargeLimit]), 목표에 닿으면 [stopCharging]으로 직접 끈다.
 */
interface ChargeControlPort {
    /** 차량에서 충전 한도와 충전 스위치를 바꿀 수 있는지. false면 Chargis는 지금처럼 안내만 한다. */
    val canControl: Boolean
    /** 차량 충전 한도를 넣는다. 차가 받는 값 중 [percent] 이상에서 가장 가까운 값을 넣고, 실제로 넣은 값을 돌려준다. */
    suspend fun setChargeLimit(percent: Int): Result<Int>
    /** 차량 충전을 멈춘다(충전 스위치 끄기). */
    suspend fun stopCharging(): Result<Unit>
}

/**
 * 주행 중 화면 제한을 기능별로 옮긴 값. android.car에 기대지 않아 테스트에서도 쓸 수 있다.
 * 값은 CarUxRestrictions의 isRequiresDistractionOptimization()과 getActiveRestrictions() 비트마스크에서 온다.
 * UX_RESTRICTIONS_FULLY_RESTRICTED는 모든 비트가 켜진 값이라 아래 항목이 모두 켜진다.
 */
data class UxLimits(
    /** Distraction Optimization 필요(차가 움직임). 정차 화면(발권·상세·스크롤)을 가린다. */
    val restricted: Boolean = false,
    /** NO_KEYBOARD: 글자 입력 금지. 배터리 직접 입력은 정차 화면에만 있어 [restricted]와 함께 가려진다. */
    val noKeyboard: Boolean = false,
    /** NO_SETUP: 설정 금지. 설정 화면은 정차 화면에만 있어 함께 가려진다. */
    val noSetup: Boolean = false,
    /** NO_FILTERING: 목록 거르기 금지. "휴게소·쉼터만" 필터는 설정 화면에만 있어 함께 가려진다. */
    val noFiltering: Boolean = false,
    /** NO_VOICE_TRANSCRIPTION: 받아쓴 말 표시 금지. 앱은 인식한 말을 화면에 쓰지 않는다(자막은 앱이 읽어 주는 안내다). */
    val noVoiceTranscription: Boolean = false,
    /** LIMIT_STRING_LENGTH일 때 화면 글자 한 줄의 상한(getMaxRestrictedStringLength). 없으면 null */
    val maxStringLength: Int? = null,
    /** LIMIT_CONTENT일 때 한 화면 항목 수 상한(getMaxCumulativeContentItems). 없으면 null */
    val maxContentItems: Int? = null,
    /** 원래 비트마스크. 기록과 개발용 화면에만 쓴다. */
    val activeBits: Int = 0
) {
    /** 항목 수 상한이 주행 카드의 최대 항목 수보다 작으면 보조 줄(종류·우회, 이유 도장, 전비 메모 등)을 뺀다. */
    val trimSecondary: Boolean get() = maxContentItems != null && maxContentItems < DRIVE_CARD_MAX_ITEMS

    /** 기록·개발 화면용 한 줄. 예) "do=true bits=0x1ff str=120 items=21" */
    fun summary(): String =
        "do=$restricted bits=0x${Integer.toHexString(activeBits)} str=${maxStringLength ?: "-"} items=${maxContentItems ?: "-"}"

    companion object {
        val NONE = UxLimits()
        /** 추천 카드 한 장에 들어가는 항목 수의 최대치(거리·이름·보조 줄·도장 2·숫자 칸 6·경고 2·버튼 2 등) */
        const val DRIVE_CARD_MAX_ITEMS = 16
    }
}

/** 순정 내비의 경로 예측(requestTrip). 현재 위치에서 충전소까지의 실제 도로 거리·시간·도착 배터리. */
interface TripEstimator {
    /** 차량의 실제 현재 위치. 모르면 null */
    suspend fun currentLocation(): GeoPoint?
    suspend fun estimate(station: ChargingStation): TripEstimate?
}

data class TripEstimate(val distanceKm: Double, val etaMin: Int, val arrivalSocPercent: Int?)

/** 순정 내비에 설정된 목적지와 거기까지 남은 거리·시간. */
data class NaviDestination(val name: String, val location: GeoPoint, val remainingKm: Double, val remainingMin: Int)

/** 실제 주행(순정 내비 목적지로 여행 시작)에 쓰는 순정 내비 값. Pleos가 아니거나 연결 전이면 모두 null이다. */
interface LiveRoutePort {
    /** 순정 내비 목적지와 남은 거리·시간. 목적지가 없으면 null */
    suspend fun destination(): NaviDestination?
    /** 차량의 실제 현재 위치. 모르면 null */
    suspend fun currentLocation(): GeoPoint?
}

/** 순정 내비의 경유지 두 자리가 이미 다른 곳으로 차 있어 추가하지 못했다. */
class WaypointsFullException : IllegalStateException("navi waypoints full")

/** 충전소 정보 연결. 경로 위 충전소 목록을 준다(빈 대수·대기 같은 실시간 상황도 목록에 담겨 온다). */
interface ChargerDataPort {
    /**
     * 경로 정보를 받아 "경로상 충전소" 후보 반환 (distanceAlongRoute 포함).
     * [connectors]를 주면 그 충전구를 쓸 수 있는 충전기만 세어 빈 대수·출력을 정한다(null = 거르지 않음).
     */
    suspend fun stationsAlongRoute(route: RouteState, connectors: Set<ConnectorType>? = null): List<ChargingStation>
}

/** 음성 출력(TTS = Text To Speech, 글자를 소리로 읽어 줌). */
interface TtsPort {
    suspend fun speak(text: String, priority: Priority = Priority.NORMAL)
}

/** 음성 인식(STT = Speech To Text, 말을 글자로 바꿈). [allowed]에 있는 명령만 알아듣는다. */
interface SttPort {
    fun listenForCommand(allowed: Set<VoiceCommand>): Flow<VoiceCommand>
}

/** 운전자 설정 저장소(F5). 값이 바뀌면 [settings]로 새 값이 흘러나온다. */
interface SettingsStore {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

/**
 * 진행 중인 여행 저장소(B5). 앱이 꺼졌다 켜져도 같은 표로 이어 가게 한다.
 * 내용은 TripController가 만든 JSON 글자 하나다. null을 저장하면 지운다.
 */
interface TripStore {
    suspend fun load(): String?
    suspend fun save(json: String?)
}
