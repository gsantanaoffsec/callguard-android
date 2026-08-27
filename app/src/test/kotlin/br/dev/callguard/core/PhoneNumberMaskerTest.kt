package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberMaskerTest {

    @Test
    fun `mantem prefixo e sufixo e esconde o meio`() {
        val masked = PhoneNumberMasker.mask("+5511999998888")
        assertTrue(masked.startsWith("+5511"))
        assertTrue(masked.endsWith("88"))
        assertEquals("+5511999998888".length, masked.length)
        assertTrue("Deveria esconder digitos do meio", masked.contains('•'))
    }

    @Test
    fun `numero muito curto e totalmente mascarado`() {
        assertEquals("••••", PhoneNumberMasker.mask("1234"))
    }

    @Test
    fun `nao vaza digitos do meio`() {
        val masked = PhoneNumberMasker.mask("+5511987654321")
        assertTrue("Digitos do meio nao podem aparecer", !masked.contains("9876543"))
    }
}
