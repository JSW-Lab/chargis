package com.chargis.app.data.sdk

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import com.chargis.app.domain.engine.ChargeLimit
import com.chargis.app.port.CarSignalsPort
import com.chargis.app.port.ChargeControlPort
import com.chargis.app.port.UxLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * B5·B6: Pleos 차량 신호.
 * - Vehicle SDK: 배터리 %(변경 알림), 기어(변경 알림), 충전 스위치(알림이 없어 [POLL_MS]마다 읽는다).
 * - 안드로이드 차량 서비스: CarUxRestrictions. 차가 움직이면 Distraction Optimization 필요 여부와 ActiveRestrictions 비트를
 *   [uxLimits]로 옮긴다. 화면은 그 값으로 기능별 제한을 적용한다(DO를 선언한 앱도 직접 적용해야 한다).
 *
 * 자동차용 안드로이드(FEATURE_AUTOMOTIVE)가 아니면 아무것도 연결하지 않는다(일반 기기·테스트에서는 android.car 클래스가 없다).
 * - Vehicle SDK 충전 제어([ChargeControlPort]): 충전 한도(setEvChargePercentLimit), 충전 스위치 끄기(setEvChargeSwitch(false)).
 *   2026-11-06 Pleos 에뮬레이터: 한도는 50~100%·5% 단위만 받고(READ_WRITE), 충전 스위치 끄기는 Virtual Console에 바로 보인다.
 * 퍼미션: CAR_ENERGY(배터리·충전), CAR_DRIVE(기어), CONTROL_CAR_ENERGY(충전 한도·스위치).
 */
class PleosCarSignals(context: Context, private val scope: CoroutineScope) : CarSignalsPort, ChargeControlPort {
    private val app = context.applicationContext
    private val _available = MutableStateFlow(false)
    private val _soc = MutableStateFlow<Int?>(null)
    private val _parked = MutableStateFlow<Boolean?>(null)
    private val _charge = MutableStateFlow<Boolean?>(null)
    private val _ux = MutableStateFlow(UxLimits.NONE)
    override val available: StateFlow<Boolean> = _available.asStateFlow()
    override val socPercent: StateFlow<Int?> = _soc.asStateFlow()
    override val parked: StateFlow<Boolean?> = _parked.asStateFlow()
    override val chargeSwitchOn: StateFlow<Boolean?> = _charge.asStateFlow()
    override val uxLimits: StateFlow<UxLimits> = _ux.asStateFlow()

    private var vehicle: Vehicle? = null
    @Volatile private var limitValues: List<Int>? = null   // 차가 받는 충전 한도 값. 확인 전이거나 쓸 수 없으면 null
    private var car: Car? = null

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(soc: Int?) { if (soc != null && soc in 0..100) _soc.value = soc }
        override fun onFailed(e: Exception) { Log.w(TAG, "soc listener failed", e) }
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) { _parked.value = gear?.let(::parkedOf) }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear listener failed", e) }
    }

    /** 자동차용 안드로이드면 SDK와 차량 서비스에 연결한다. 실패해도 앱은 데모 값으로 계속한다. */
    fun start() {
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        connectUx()
        runCatching {
            val v = Vehicle(app).also { it.initialize() }
            vehicle = v
            v.getEvBattery().getEvBatteryStateOfCharge({ soc -> socListener.onEvBatteryStateOfChargeUpdated(soc); _available.value = true }, { Log.w(TAG, "soc read failed", it) })
            v.getEvBattery().registerEvBatteryStateOfCharge(socListener)
            v.getGear().getCurrentGearSelectionState({ g -> gearListener.onSelected(g) }, { Log.w(TAG, "gear read failed", it) })
            v.getGear().registerGearSelection(gearListener)
            // 충전 한도를 쓸 수 있는지와 받는 값을 확인해 둔다(쓰기 권한이 없으면 충전 제어를 하지 않는다).
            v.getEvBattery().checkEvChargePercentLimitCapabilities({ cfg, values ->
                val writable = cfg.accessType.name.contains("WRITE")
                limitValues = if (writable) values.mapNotNull { it?.toInt() }.sorted() else null
                Log.i(TAG, "charge limit: access=${cfg.accessType} values=$values")
            }, { Log.w(TAG, "charge limit capability failed", it) })
            scope.launch {
                while (isActive) {
                    v.getEvBattery().getEvChargeSwitchState({ _charge.value = it }, { })
                    delay(POLL_MS)
                }
            }
        }.onFailure { Log.w(TAG, "vehicle sdk unavailable", it) }
    }

    private fun connectUx() {
        runCatching {
            car = Car.createCar(app, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (!ready) return@createCar
                val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return@createCar
                ux.currentCarUxRestrictions?.let(::onUx)
                // 제한이 바뀔 때마다 onUxRestrictionsChanged()로 새 값이 온다.
                ux.registerListener(::onUx)
            }
        }.onFailure { Log.w(TAG, "car ux restrictions unavailable", it) }
    }

    private fun onUx(r: CarUxRestrictions) {
        val limits = limitsOf(r.isRequiresDistractionOptimization, r.activeRestrictions, r.maxRestrictedStringLength, r.maxCumulativeContentItems)
        if (limits != _ux.value) Log.i(TAG, "ux restrictions: ${limits.summary()}")
        _ux.value = limits
    }

    override val canControl: Boolean get() = vehicle != null && limitValues != null

    override suspend fun setChargeLimit(percent: Int): Result<Int> {
        val v = vehicle ?: return Result.failure(IllegalStateException("vehicle sdk unavailable"))
        val value = ChargeLimit.pick(percent, limitValues)
        return call { ok, fail -> v.getEvBattery().setEvChargePercentLimit(value.toFloat(), { ok(it?.toInt() ?: value) }, fail) }
            .onSuccess { Log.i(TAG, "charge limit set $it% (asked $percent%)") }
            .onFailure { Log.w(TAG, "charge limit set failed", it) }
    }

    override suspend fun stopCharging(): Result<Unit> {
        val v = vehicle ?: return Result.failure(IllegalStateException("vehicle sdk unavailable"))
        return call<Unit> { ok, fail -> v.getEvBattery().setEvChargeSwitch(false, { ok(Unit) }, fail) }
            .onSuccess { Log.i(TAG, "charge switch off") }
            .onFailure { Log.w(TAG, "charge switch off failed", it) }
    }

    // 콜백 하나를 기다린다. [CALL_TIMEOUT_MS] 안에 답이 없으면 실패.
    private suspend fun <T> call(request: ((T) -> Unit, (Exception) -> Unit) -> Unit): Result<T> =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine<Result<T>> { cont ->
                runCatching {
                    request({ if (cont.isActive) cont.resume(Result.success(it)) }, { if (cont.isActive) cont.resume(Result.failure(it)) })
                }.onFailure { if (cont.isActive) cont.resume(Result.failure(it)) }
            }
        } ?: Result.failure(IllegalStateException("vehicle sdk timeout"))

    fun release() {
        vehicle?.let { v ->
            runCatching {
                v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
                v.getGear().unregisterGearSelection(gearListener)
                v.release()
            }
        }
        runCatching { car?.disconnect() }
        vehicle = null
        car = null
    }

    companion object {
        private const val TAG = "ChargisCar"
        private const val POLL_MS = 2_000L
        private const val CALL_TIMEOUT_MS = 3_000L

        /**
         * CarUxRestrictions 값을 기능별 [UxLimits]로 옮긴다. 상한값은 해당 비트가 켜졌을 때만 쓴다.
         * 비트 상수는 컴파일할 때 숫자로 들어가므로 android.car 없이도(단위 테스트) 부를 수 있다.
         */
        fun limitsOf(requiresDo: Boolean, bits: Int, maxStringLength: Int, maxContentItems: Int): UxLimits {
            fun has(flag: Int) = bits and flag == flag
            return UxLimits(
                restricted = requiresDo,
                noKeyboard = has(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD),
                noSetup = has(CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP),
                noFiltering = has(CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING),
                noVoiceTranscription = has(CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION),
                maxStringLength = maxStringLength.takeIf { has(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH) && it > 0 },
                maxContentItems = maxContentItems.takeIf { has(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT) && it > 0 },
                activeBits = bits
            )
        }

        /** 기어를 정차 여부로. P면 정차, 그 밖에 알려진 기어면 주행 쪽, 모르면 null. */
        fun parkedOf(gear: VehicleGear): Boolean? = when (gear) {
            VehicleGear.GEAR_PARK -> true
            VehicleGear.GEAR_UNKNOWN -> null
            else -> false
        }
    }
}

/** Pleos가 아닌 기기·테스트용: 충전을 제어할 수 없다. */
object NoChargeControl : ChargeControlPort {
    override val canControl = false
    override suspend fun setChargeLimit(percent: Int): Result<Int> = Result.failure(UnsupportedOperationException("no charge control"))
    override suspend fun stopCharging(): Result<Unit> = Result.failure(UnsupportedOperationException("no charge control"))
}

/** Pleos가 아닌 기기·테스트용: 아무 신호도 없다. */
object NoCarSignals : CarSignalsPort {
    override val available: StateFlow<Boolean> = MutableStateFlow(false)
    override val socPercent: StateFlow<Int?> = MutableStateFlow(null)
    override val parked: StateFlow<Boolean?> = MutableStateFlow(null)
    override val chargeSwitchOn: StateFlow<Boolean?> = MutableStateFlow(null)
    override val uxLimits: StateFlow<UxLimits> = MutableStateFlow(UxLimits.NONE)
}
