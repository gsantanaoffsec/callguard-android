package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProtectionSettingsTest {

    @Test
    fun `padroes correspondem ao especificado`() {
        val settings = ProtectionSettings()
        assertEquals(true, settings.protectionEnabled)
        assertEquals(3, settings.maxAllowedCalls)
        assertEquals(15, settings.windowMinutes)
        assertEquals(false, settings.applyToContacts)
    }

    @Test
    fun `valores corrompidos na persistencia sao ajustados em vez de quebrar o app`() {
        val settings = ProtectionSettings.sanitized(
            protectionEnabled = true,
            maxAllowedCalls = 0,
            windowMinutes = -5,
            applyToContacts = false,
        )
        assertEquals(1, settings.maxAllowedCalls)
        assertEquals(TimeUnit.MINUTES.toMillis(1), settings.windowMillis)
    }

    @Test
    fun `opcoes oferecidas na interface cobrem o pedido`() {
        assertEquals(listOf(1, 2, 3, 4, 5), ProtectionSettings.MAX_CALL_OPTIONS)
        assertEquals(listOf(5, 10, 15, 30, 60), ProtectionSettings.WINDOW_MINUTE_OPTIONS)
    }
}
