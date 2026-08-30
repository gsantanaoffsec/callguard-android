package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * As opcoes sao curadoria da interface e mudam quando a interface muda -- a lista
     * cresceu quando os chips viraram uma folha que rola. Fixar os valores literais aqui
     * so fazia o teste quebrar a cada ajuste de UI sem proteger nada.
     *
     * O que importa e o contrato: ordenada, sem repeticao, comecando onde deve e dentro
     * do que a persistencia aceita.
     */
    @Test
    fun `as opcoes de limite formam uma lista utilizavel`() {
        val opcoes = ProtectionSettings.MAX_CALL_OPTIONS
        assertEquals("deve comecar em 1", 1, opcoes.first())
        assertEquals("nao pode repetir", opcoes.size, opcoes.distinct().size)
        assertEquals("deve estar em ordem", opcoes.sorted(), opcoes)
        assertTrue("o padrao precisa estar entre as opcoes",
            ProtectionSettings.DEFAULT_MAX_ALLOWED_CALLS in opcoes)
    }

    @Test
    fun `as opcoes de janela cabem no que a persistencia aceita`() {
        val opcoes = ProtectionSettings.WINDOW_MINUTE_OPTIONS
        assertEquals("nao pode repetir", opcoes.size, opcoes.distinct().size)
        assertEquals("deve estar em ordem", opcoes.sorted(), opcoes)
        assertTrue("o padrao precisa estar entre as opcoes",
            ProtectionSettings.DEFAULT_WINDOW_MINUTES in opcoes)
        opcoes.forEach { minutos ->
            // Fora desta faixa, `sanitized` corrigiria em silencio e a opcao mostrada
            // deixaria de ser a opcao aplicada.
            assertTrue(
                "$minutos fora da faixa aceita",
                minutos in WindowFormat.MIN_MINUTES..WindowFormat.MAX_MINUTES,
            )
        }
    }

    @Test
    fun `uma janela em horas sobrevive a persistencia`() {
        // O valor personalizado que a folha oferece precisa voltar igual.
        val settings = ProtectionSettings.sanitized(
            protectionEnabled = true,
            maxAllowedCalls = 2,
            windowMinutes = 6 * 60,
            applyToContacts = false,
        )
        assertEquals(360, settings.windowMinutes)
        assertEquals("2 chamadas em 6 h", settings.globalPolicy().describe())
    }
}
