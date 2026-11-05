package com.chargis.app.data.sdk

import com.chargis.app.port.UxLimits
import com.chargis.app.ui.limitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CarUxRestrictions의 ActiveRestrictions 비트를 기능별 제한으로 옮기는 규칙과 글자 수 상한 자르기. */
class UxLimitsTest {
    // CarUxRestrictions 상수(AAOS 공개 값)
    private val limitString = 0x04
    private val noKeyboard = 0x08
    private val limitContent = 0x20
    private val fully = 0x1ff

    /** 정차: 제한 없음. 상한값이 와도 해당 비트가 꺼져 있으면 쓰지 않는다. */
    @Test
    fun baselineHasNoLimits() {
        val l = PleosCarSignals.limitsOf(requiresDo = false, bits = 0, maxStringLength = 120, maxContentItems = 21)
        assertFalse(l.restricted)
        assertFalse(l.noKeyboard)
        assertNull(l.maxStringLength)
        assertNull(l.maxContentItems)
        assertFalse(l.trimSecondary)
    }

    /** 전부 제한(FULLY_RESTRICTED)은 모든 비트가 켜진 값이라 기능별 항목이 모두 켜지고 상한값을 쓴다. */
    @Test
    fun fullyRestrictedTurnsOnEveryFeature() {
        val l = PleosCarSignals.limitsOf(requiresDo = true, bits = fully, maxStringLength = 120, maxContentItems = 21)
        assertTrue(l.restricted)
        assertTrue(l.noKeyboard && l.noSetup && l.noFiltering && l.noVoiceTranscription)
        assertEquals(120, l.maxStringLength)
        assertEquals(21, l.maxContentItems)
        // AAOS 기본 상한 21개는 주행 카드 항목 수보다 커서 보조 줄을 빼지 않는다.
        assertFalse(l.trimSecondary)
    }

    /** 일부 비트만 켜지면 그 기능만 제한한다. 항목 수 상한이 작으면 보조 줄을 뺀다. */
    @Test
    fun partialBitsLimitOnlyThoseFeatures() {
        val l = PleosCarSignals.limitsOf(requiresDo = true, bits = noKeyboard or limitString or limitContent, maxStringLength = 30, maxContentItems = 8)
        assertTrue(l.noKeyboard)
        assertFalse(l.noSetup)
        assertEquals(30, l.maxStringLength)
        assertTrue(l.trimSecondary)
        assertEquals("do=true bits=0x2c str=30 items=8", l.summary())
    }

    @Test
    fun limitTextCutsToMaxWithEllipsis() {
        assertEquals("B 휴게소(속초방향)", limitText("B 휴게소(속초방향)", null))
        assertEquals("B 휴게소(속초방향)", limitText("B 휴게소(속초방향)", 11))
        assertEquals("B 휴게소…", limitText("B 휴게소(속초방향)", 6))
        assertEquals(6, limitText("B 휴게소(속초방향)", 6).length)
        assertEquals("…", limitText("가나다", 1))
    }

    @Test
    fun noneIsDefault() {
        assertEquals(UxLimits(), UxLimits.NONE)
    }
}
