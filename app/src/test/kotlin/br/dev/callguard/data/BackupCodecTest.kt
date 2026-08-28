package br.dev.callguard.data

import br.dev.callguard.core.BackupError
import br.dev.callguard.core.BackupNumber
import br.dev.callguard.core.BackupPayload
import br.dev.callguard.core.BackupRule
import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.core.SchedulePolicy
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * O arquivo de backup e a unica entrada do app que vem de fora do proprio app.
 *
 * Robolectric porque `org.json` e uma classe do Android, nao da JVM. O que esta sendo
 * testado continua sendo logica pura: ida e volta sem perda, e recusa nomeada para cada
 * forma de arquivo ruim.
 */
@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {

    private val exemplo = BackupPayload(
        exportedAtMillis = 1_700_000_000_000L,
        appVersionName = "2.1.0",
        settings = ProtectionSettings(
            protectionEnabled = true,
            maxAllowedCalls = 2,
            windowMillis = TimeUnit.MINUTES.toMillis(30),
            applyToContacts = true,
            notifyOnBlock = false,
            biometricLockEnabled = true,
        ),
        schedule = SchedulePolicy(
            enabled = true,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 7 * 60,
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            maxAllowedCalls = 1,
            windowMillis = TimeUnit.MINUTES.toMillis(45),
        ),
        allowlist = listOf(BackupNumber("+5511999998888", "Mãe")),
        blocklist = listOf(BackupNumber("+5511777776666", "Telemarketing")),
        customRules = listOf(
            BackupRule("+5511555554444", "Cobrança", 1, TimeUnit.HOURS.toMillis(6), true),
        ),
    )

    @Test
    fun `ida e volta preserva tudo que foi exportado`() {
        val lido = BackupCodec.decode(BackupCodec.encode(exemplo)).getOrThrow()

        assertEquals(exemplo.settings, lido.settings)
        assertEquals(exemplo.schedule, lido.schedule)
        assertEquals(exemplo.allowlist, lido.allowlist)
        assertEquals(exemplo.blocklist, lido.blocklist)
        assertEquals(exemplo.customRules, lido.customRules)
        assertEquals(exemplo.appVersionName, lido.appVersionName)
        assertEquals(exemplo.exportedAtMillis, lido.exportedAtMillis)
    }

    @Test
    fun `o arquivo gerado e legivel por uma pessoa`() {
        val texto = BackupCodec.encode(exemplo)
        // Indentado e com os nomes de campo por extenso: o dono do arquivo precisa
        // conseguir conferir o que esta levando embora sem ferramenta nenhuma.
        assertTrue(texto.contains("\n  "))
        assertTrue(texto.contains("\"allowlist\""))
        assertTrue(texto.contains("+5511999998888"))
    }

    @Test
    fun `texto que nao e json e recusado sem excecao`() {
        val erro = BackupCodec.decode("isto nao e json").exceptionOrNull()
        assertEquals(BackupError.NOT_JSON, (erro as BackupException).error)
    }

    @Test
    fun `json de outro aplicativo e recusado`() {
        val erro = BackupCodec.decode("""{"app":"outro.coisa","formatVersion":1}""")
            .exceptionOrNull()
        assertEquals(BackupError.WRONG_APP, (erro as BackupException).error)
    }

    @Test
    fun `arquivo de versao futura e recusado em vez de adivinhado`() {
        val texto = BackupCodec.encode(exemplo)
            .replace("\"formatVersion\": 1", "\"formatVersion\": 99")
        val erro = BackupCodec.decode(texto).exceptionOrNull()
        assertEquals(BackupError.FUTURE_VERSION, (erro as BackupException).error)
    }

    @Test
    fun `arquivo sem bloco de ajustes e considerado corrompido`() {
        val erro = BackupCodec.decode("""{"app":"br.dev.callguard.backup","formatVersion":1}""")
            .exceptionOrNull()
        assertEquals(BackupError.CORRUPTED, (erro as BackupException).error)
    }

    @Test
    fun `entrada sem numero e descartada sem derrubar o arquivo inteiro`() {
        val texto = BackupCodec.encode(
            exemplo.copy(
                allowlist = listOf(
                    BackupNumber("", "vazio"),
                    BackupNumber("+5511999998888", "Mãe"),
                ),
            ),
        )
        val lido = BackupCodec.decode(texto).getOrThrow()
        assertEquals(1, lido.allowlist.size)
        assertEquals("+5511999998888", lido.allowlist.first().normalizedNumber)
    }

    @Test
    fun `valores fora de faixa sao corrigidos em vez de lancar excecao`() {
        val texto = BackupCodec.encode(exemplo)
            .replace("\"maxAllowedCalls\": 2", "\"maxAllowedCalls\": 9999")
        val lido = BackupCodec.decode(texto).getOrThrow()
        // 50 e o teto aplicado por ProtectionSettings.sanitized.
        assertEquals(50, lido.settings.maxAllowedCalls)
    }

    @Test
    fun `regra com janela absurda e trazida para dentro do limite`() {
        val texto = BackupCodec.encode(exemplo).replace("21600000", "999999999999")
        val lido = BackupCodec.decode(texto).getOrThrow()
        assertEquals(TimeUnit.HOURS.toMillis(24), lido.customRules.first().windowMillis)
    }

    @Test
    fun `lista de dias vazia volta como semana inteira`() {
        // Um periodo sem nenhum dia nunca valeria; e mais provavel ser arquivo estragado
        // do que intencao do usuario.
        val texto = BackupCodec.encode(exemplo.copy(schedule = exemplo.schedule.copy(activeDays = emptySet())))
        val lido = BackupCodec.decode(texto).getOrThrow()
        assertEquals(7, lido.schedule.activeDays.size)
    }

    @Test
    fun `resumo descreve o conteudo antes de o usuario confirmar`() {
        val resumo = exemplo.summary()
        assertTrue(resumo.contains("1 permitido(s)"))
        assertTrue(resumo.contains("1 bloqueado(s)"))
        assertTrue(resumo.contains("modo noturno ligado"))
    }
}
