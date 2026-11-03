package com.chargis.app.voice

import com.chargis.app.domain.model.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/* F5 음성 명령 해석 테스트. 지금 받을 수 있는 명령 중에서만 고르는지 확인한다. */
class VoiceCommandParserTest {
    private val decide = setOf(VoiceCommand.ACCEPT, VoiceCommand.NEXT_STOP)
    private val arrived = setOf(VoiceCommand.START_CHARGING, VoiceCommand.DEPART)

    @Test
    fun parsesNaturalPhrases() {
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("응 수락할게", decide))
        assertEquals(VoiceCommand.NEXT_STOP, VoiceCommandParser.parse("다음 휴게소로 가", decide))
        assertEquals(VoiceCommand.START_CHARGING, VoiceCommandParser.parse("충전 시작해 줘", arrived))
        // Pleos 온디바이스 STT 세션이 나뉘어 뒤쪽만 들린 경우(에뮬레이터 실측)
        assertEquals(VoiceCommand.START_CHARGING, VoiceCommandParser.parse("시작해줘", arrived))
        assertEquals(VoiceCommand.ACCEPT, VoiceCommandParser.parse("수락예요", decide))
        assertNull(VoiceCommandParser.parse("시작해줘", decide))
        assertEquals(VoiceCommand.DEPART, VoiceCommandParser.parse("출발하자", arrived))
        assertEquals(VoiceCommand.PRECONDITION, VoiceCommandParser.parse("배터리 예열 켜 줘", setOf(VoiceCommand.PRECONDITION)))
        assertNull(VoiceCommandParser.parse("예열", decide))
    }

    @Test
    fun ignoresCommandsNotAllowedNow() {
        // 결정 대기 중에는 "출발"을 알아듣지 않는다.
        assertNull(VoiceCommandParser.parse("출발", decide))
        assertNull(VoiceCommandParser.parse("", decide))
        assertNull(VoiceCommandParser.parse("음악 틀어 줘", decide))
    }
}
