package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BrazilPhoneRulesTest {

    @Test
    fun `celular brasileiro antigo recebe o nono digito`() {
        assertEquals("+5511999998888", BrazilPhoneRules.canonicalize("+551199998888"))
        assertEquals("+5521987654321", BrazilPhoneRules.canonicalize("+552187654321"))
    }

    @Test
    fun `celular ja com nove digitos nao muda`() {
        assertEquals("+5511999998888", BrazilPhoneRules.canonicalize("+5511999998888"))
    }

    @Test
    fun `fixo brasileiro nao e alterado`() {
        // Fixos comecam em 2..5 e continuam com 8 digitos.
        assertEquals("+551133334444", BrazilPhoneRules.canonicalize("+551133334444"))
        assertEquals("+551145556666", BrazilPhoneRules.canonicalize("+551145556666"))
    }

    @Test
    fun `numeros de outros paises nao sao tocados`() {
        assertEquals("+14155552671", BrazilPhoneRules.canonicalize("+14155552671"))
        assertEquals("+351912345678", BrazilPhoneRules.canonicalize("+351912345678"))
    }

    @Test
    fun `entradas fora do formato voltam inalteradas`() {
        assertEquals("+55", BrazilPhoneRules.canonicalize("+55"))
        assertEquals("11999998888", BrazilPhoneRules.canonicalize("11999998888"))
    }
}
