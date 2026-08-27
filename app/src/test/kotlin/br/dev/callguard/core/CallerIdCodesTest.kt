package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerIdCodesTest {

    @Test
    fun `monta o prefixo de ocultacao antes do numero`() {
        assertEquals("#31#11999998888", CallerIdCodes.buildHiddenCallerIdNumber("11999998888"))
    }

    @Test
    fun `formatacao visual e descartada`() {
        assertEquals(
            "#31#11999998888",
            CallerIdCodes.buildHiddenCallerIdNumber("(11) 99999-8888"),
        )
        assertEquals(
            "#31#11999998888",
            CallerIdCodes.buildHiddenCallerIdNumber("  11 9 9999 8888  "),
        )
    }

    @Test
    fun `o mais inicial e preservado para numeros internacionais`() {
        assertEquals(
            "#31#+5511999998888",
            CallerIdCodes.buildHiddenCallerIdNumber("+55 11 99999-8888"),
        )
    }

    @Test
    fun `mais no meio do numero nao e preservado`() {
        assertEquals("1199998888", CallerIdCodes.sanitizeDialNumber("11 9999+8888"))
    }

    @Test
    fun `texto sem digitos nao produz numero`() {
        assertNull(CallerIdCodes.buildHiddenCallerIdNumber(""))
        assertNull(CallerIdCodes.buildHiddenCallerIdNumber("   "))
        assertNull(CallerIdCodes.buildHiddenCallerIdNumber("abc"))
        assertNull(CallerIdCodes.buildHiddenCallerIdNumber("+"))
    }

    @Test
    fun `prefixos seguem o padrao 3GPP`() {
        assertEquals("#31#", CallerIdCodes.HIDE_CALLER_ID_PREFIX)
        assertEquals("*31#", CallerIdCodes.SHOW_CALLER_ID_PREFIX)
    }
}
