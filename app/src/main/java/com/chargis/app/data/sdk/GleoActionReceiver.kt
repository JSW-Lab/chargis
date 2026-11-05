package com.chargis.app.data.sdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chargis.app.ui.AcceptOutcome
import com.chargis.app.ui.DeclineOutcome
import com.chargis.app.ui.GleoReplies
import com.chargis.app.ui.GleoReply
import com.chargis.app.ui.GleoSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gleo AI 연동(Gleo Interface). Gleo가 assets/schema.json의 함수를 부르면 [GleoActionReceiver]가 받아 답한다.
 *
 * - 함수: GET_CHARGING_PLAN(다음 충전 계획), GET_CHARGING_STATUS(충전·주행 상태), ACCEPT_CHARGING_STOP(안내한 추천 수락),
 *   EXPLAIN_RECOMMENDATION(왜 그 충전소인지), DECLINE_STOP(안내한 추천 거절)
 * - Gleo는 "패키지명.함수명"을 Intent action으로, 파라미터를 "ACTION_PARAM" JSON으로 보낸다(에뮬레이터 v2.1.0 실측. 문서 예시와 다르다).
 * - 7초 안에 APP_ACTION_RESPONSE 브로드캐스트로 답해야 한다. 앱이 꺼져 있다가 불려도 늦지 않게, 컨트롤러가 미리 만들어 둔 답([GleoSnapshot])을
 *   기기에 저장해 두고 여기서는 읽기만 한다.
 */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val function = intent.action?.removePrefix("${context.packageName}.") ?: return
        Log.i(TAG, "received $function param=${intent.getStringExtra(EXTRA_PARAM)}")
        val snap = GleoBridge.load(context)
        when (function) {
            FN_PLAN -> respond(context, function, snap?.plan ?: GleoReplies.notRunning())
            FN_STATUS -> respond(context, function, snap?.status ?: GleoReplies.notRunning())
            FN_EXPLAIN -> respond(context, function, snap?.explain ?: GleoReplies.notRunning())
            FN_DECLINE -> {
                val name = snap?.acceptable
                // 거절도 화면의 [다음 충전소]와 같은 일이라 앱이 실행 중이어야 한다.
                if (name == null || !GleoBridge.isLive) {
                    respond(context, function, if (GleoBridge.isLive) GleoReplies.declineReply("", DeclineOutcome.NOTHING_TO_DECLINE) else GleoReplies.cannotAccept(false))
                    return
                }
                val pending = goAsync()
                val request = CompletableDeferred<DeclineOutcome>()
                GleoBridge.sendDecline(request)
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val outcome = withTimeoutOrNull(ACCEPT_WAIT_MS) { request.await() } ?: DeclineOutcome.NOTHING_TO_DECLINE
                        respond(context, function, GleoReplies.declineReply(name, outcome))
                    } finally {
                        pending.finish()
                    }
                }
            }
            FN_ACCEPT -> {
                val name = snap?.acceptable
                // 수락은 화면의 [경유지로 수락]과 같은 일이라 앱이 실행 중이어야 한다.
                if (name == null || !GleoBridge.isLive) {
                    respond(context, function, GleoReplies.cannotAccept(GleoBridge.isLive))
                    return
                }
                // 경유지 추가 결과를 기다렸다가 그 결과로 답한다(Gleo는 7초까지 기다린다).
                val pending = goAsync()
                val request = CompletableDeferred<AcceptOutcome>()
                GleoBridge.send(request)
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val outcome = withTimeoutOrNull(ACCEPT_WAIT_MS) { request.await() } ?: AcceptOutcome.FAILED
                        respond(context, function, GleoReplies.acceptReply(name, outcome))
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun respond(context: Context, function: String, reply: GleoReply) {
        val response = JSONObject()
            .put("functionName", function)
            .put("status", if (reply.success) "success" else "fail")
            .put("message", reply.message)
            .put("mode", "voice")
        if (reply.data.isNotEmpty()) response.put("data", JSONArray().put(JSONObject(reply.data)).toString())
        context.sendBroadcast(
            Intent(ACTION_RESPONSE)
                .putExtra("packageName", context.packageName)
                .putExtra("response", response.toString())
        )
        Log.i(TAG, "sent $response")
    }

    companion object {
        private const val TAG = "ChargisGleo"
        private const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        private const val EXTRA_PARAM = "ACTION_PARAM"
        const val FN_PLAN = "GET_CHARGING_PLAN"
        const val FN_STATUS = "GET_CHARGING_STATUS"
        const val FN_ACCEPT = "ACCEPT_CHARGING_STOP"
        const val FN_EXPLAIN = "EXPLAIN_RECOMMENDATION"
        const val FN_DECLINE = "DECLINE_STOP"
        private const val ACCEPT_WAIT_MS = 5_000L
    }
}

/**
 * 앱(컨트롤러)과 Gleo 수신기 사이의 다리.
 * - 컨트롤러 쪽: 상태가 바뀔 때마다 [save]로 미리 만든 답을 기기에 쓰고, [acceptRequests]로 Gleo의 수락 요청을 받아 결과를 채운다.
 * - 수신기 쪽: [load]로 답을 읽고, 앱이 실행 중이면([isLive]) [send]로 수락을 넘긴 뒤 결과를 기다린다.
 */
object GleoBridge {
    private const val PREFS = "gleo"
    private const val KEY = "snapshot"

    private val _acceptRequests = MutableSharedFlow<CompletableDeferred<AcceptOutcome>>(extraBufferCapacity = 1)
    val acceptRequests = _acceptRequests.asSharedFlow()

    /** 컨트롤러가 수락 요청을 듣고 있는지(앱이 실행 중인지). */
    val isLive: Boolean get() = _acceptRequests.subscriptionCount.value > 0

    fun send(request: CompletableDeferred<AcceptOutcome>): Boolean = _acceptRequests.tryEmit(request)

    private val _declineRequests = MutableSharedFlow<CompletableDeferred<DeclineOutcome>>(extraBufferCapacity = 1)
    val declineRequests = _declineRequests.asSharedFlow()

    fun sendDecline(request: CompletableDeferred<DeclineOutcome>): Boolean = _declineRequests.tryEmit(request)

    fun save(context: Context, snap: GleoSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(snap).toString()).apply()
    }

    fun load(context: Context): GleoSnapshot? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }

    private fun encode(s: GleoSnapshot) = JSONObject()
        .put("plan", encode(s.plan))
        .put("status", encode(s.status))
        .put("acceptable", s.acceptable ?: JSONObject.NULL)
        .put("explain", encode(s.explain))

    private fun encode(r: GleoReply) = JSONObject().put("success", r.success).put("message", r.message).put("data", JSONObject(r.data))

    private fun decode(o: JSONObject) = GleoSnapshot(
        plan = decodeReply(o.getJSONObject("plan")),
        status = decodeReply(o.getJSONObject("status")),
        acceptable = o.optString("acceptable").takeIf { !o.isNull("acceptable") && it.isNotEmpty() },
        // 예전 형식(설명 없음)으로 저장된 답도 읽는다.
        explain = o.optJSONObject("explain")?.let(::decodeReply) ?: GleoReplies.notRunning()
    )

    private fun decodeReply(o: JSONObject): GleoReply {
        val d = o.optJSONObject("data")
        val data = d?.keys()?.asSequence()?.associateWith { k -> d.opt(k).takeIf { it != JSONObject.NULL } }.orEmpty()
        return GleoReply(o.getBoolean("success"), o.getString("message"), data)
    }
}
