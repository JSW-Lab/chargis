package com.chargis.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.chargis.app.port.UxLimits

/*
 * 주행 중 화면 제한(CarUxRestrictions)을 화면 곳곳에 나눠 주는 통로.
 * ChargisRoot가 지금 제한을 넣고, 주행 카드와 자막이 꺼내 써서 글자 수·항목 수 상한을 지킨다.
 */

/** 지금 걸린 주행 중 화면 제한. 기본은 제한 없음(Pleos가 아닌 기기·화면 테스트). */
val LocalUxLimits = staticCompositionLocalOf { UxLimits.NONE }

/**
 * [max]자를 넘으면 앞부분만 남기고 "…"를 붙여 [max]자에 맞춘다. [max]가 없으면 그대로 둔다.
 * LIMIT_STRING_LENGTH 제한에서 쓴다. 음성 안내(TTS) 문장은 줄이지 않고 화면 글자만 줄인다.
 */
fun limitText(text: String, max: Int?): String = when {
    max == null || max <= 0 || text.length <= max -> text
    max == 1 -> "…"
    else -> text.take(max - 1).trimEnd() + "…"
}

/** 지금 제한의 글자 수 상한에 맞춘 글자 */
@Composable
fun uxText(text: String): String = limitText(text, LocalUxLimits.current.maxStringLength)

/** 보조 줄(종류·우회, 이유 도장, 전비 메모, 혼잡 비교의 배터리 차이)을 보여 줘도 되는지. LIMIT_CONTENT 상한이 작으면 false */
@Composable
fun uxShowSecondary(): Boolean = !LocalUxLimits.current.trimSecondary
