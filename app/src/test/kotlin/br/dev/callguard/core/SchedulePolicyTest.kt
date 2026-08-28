package br.dev.callguard.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Testes do periodo por horario, com atencao especial a faixa que atravessa a meia-noite.
 */
class SchedulePolicyTest {

    /** 22:00 -> 07:00, todos os dias. */
    private val noite = SchedulePolicy(
        enabled = true,
        startMinuteOfDay = 22 * 60,
        endMinuteOfDay = 7 * 60,
        activeDays = DayOfWeek.entries.toSet(),
        maxAllowedCalls = 1,
        windowMillis = TimeUnit.MINUTES.toMillis(30),
    )

    /** 2026-08-26 e uma quarta-feira. */
    private fun quarta(hora: Int, minuto: Int = 0) = LocalDateTime.of(2026, 8, 26, hora, minuto)
    private fun quinta(hora: Int, minuto: Int = 0) = LocalDateTime.of(2026, 8, 27, hora, minuto)

    @Test
    fun `desabilitado nunca esta ativo`() {
        assertFalse(noite.copy(enabled = false).isActiveAt(quarta(23)))
    }

    @Test
    fun `antes do periodo nao esta ativo`() {
        assertFalse(noite.isActiveAt(quarta(21, 59)))
    }

    @Test
    fun `exatamente no inicio ja esta ativo`() {
        assertTrue(noite.isActiveAt(quarta(22, 0)))
    }

    @Test
    fun `durante a madrugada esta ativo`() {
        assertTrue(noite.isActiveAt(quarta(23, 30)))
        assertTrue(noite.isActiveAt(quinta(2, 0)))
        assertTrue(noite.isActiveAt(quinta(6, 59)))
    }

    @Test
    fun `exatamente no fim ja saiu do periodo`() {
        assertFalse(noite.isActiveAt(quinta(7, 0)))
    }

    @Test
    fun `depois do periodo nao esta ativo`() {
        assertFalse(noite.isActiveAt(quinta(12, 0)))
    }

    @Test
    fun `faixa que nao atravessa a meia-noite funciona normalmente`() {
        val almoco = noite.copy(startMinuteOfDay = 12 * 60, endMinuteOfDay = 14 * 60)
        assertFalse(almoco.isActiveAt(quarta(11, 59)))
        assertTrue(almoco.isActiveAt(quarta(12, 0)))
        assertTrue(almoco.isActiveAt(quarta(13, 30)))
        assertFalse(almoco.isActiveAt(quarta(14, 0)))
    }

    @Test
    fun `inicio igual ao fim e periodo vazio, nao 24 horas`() {
        val vazio = noite.copy(startMinuteOfDay = 22 * 60, endMinuteOfDay = 22 * 60)
        assertFalse(vazio.isActiveAt(quarta(22, 0)))
        assertFalse(vazio.isActiveAt(quarta(3, 0)))
    }

    /**
     * O dia da semana considerado e o do INICIO do periodo.
     *
     * Escolhendo apenas quarta-feira, a madrugada de quinta (que comecou quarta as 22:00)
     * DEVE estar protegida, e a madrugada de quarta (que comecou terca) NAO deve.
     */
    @Test
    fun `dia selecionado vale para a madrugada que comecou naquele dia`() {
        val soQuarta = noite.copy(activeDays = setOf(DayOfWeek.WEDNESDAY))

        assertTrue("22:00 de quarta", soQuarta.isActiveAt(quarta(22, 30)))
        assertTrue("02:00 de quinta comecou na quarta", soQuarta.isActiveAt(quinta(2, 0)))
        assertFalse("02:00 de quarta comecou na terca", soQuarta.isActiveAt(quarta(2, 0)))
        assertFalse("22:00 de quinta nao foi selecionado", soQuarta.isActiveAt(quinta(22, 30)))
    }

    @Test
    fun `fim de semana pode ficar de fora`() {
        val semFimDeSemana = noite.copy(
            activeDays = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            ),
        )
        // 2026-08-28 e sexta; 2026-08-29 e sabado.
        assertFalse(semFimDeSemana.isActiveAt(LocalDateTime.of(2026, 8, 28, 23, 0)))
        assertFalse(semFimDeSemana.isActiveAt(LocalDateTime.of(2026, 8, 29, 23, 0)))
        // Quinta 22:00 ainda esta selecionada.
        assertTrue(semFimDeSemana.isActiveAt(LocalDateTime.of(2026, 8, 27, 23, 0)))
    }

    @Test
    fun `politica derivada carrega a origem correta`() {
        val p = noite.toPolicy()
        assertTrue(p.source == PolicySource.SCHEDULE)
        assertTrue(p.maxAllowedCalls == 1)
    }
}
