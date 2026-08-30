package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O texto da janela aparece no botão, na frase da tela inicial, no log e no diagnóstico.
 *
 * Se cada um formatar por conta própria, o mesmo número vira "90 min" num lugar e "1 h"
 * em outro — e a pessoa passa a desconfiar de qual está certo.
 */
class WindowFormatTest {

    @Test
    fun `minutos abaixo de uma hora ficam em minutos`() {
        assertEquals("5 min", WindowFormat.short(5))
        assertEquals("45 min", WindowFormat.short(45))
    }

    @Test
    fun `horas redondas nao mostram minutos`() {
        assertEquals("1 h", WindowFormat.short(60))
        assertEquals("6 h", WindowFormat.short(360))
        assertEquals("24 h", WindowFormat.short(1440))
    }

    @Test
    fun `hora quebrada mostra o resto em vez de virar decimal`() {
        // "1,5 h" obrigaria a pessoa a converter de cabeca -- justamente o trabalho que
        // o rotulo deveria poupar.
        assertEquals("1 h 30", WindowFormat.short(90))
        assertEquals("2 h 15", WindowFormat.short(135))
    }

    @Test
    fun `forma por extenso concorda em numero`() {
        assertEquals("1 minuto", WindowFormat.long(1))
        assertEquals("30 minutos", WindowFormat.long(30))
        assertEquals("1 hora", WindowFormat.long(60))
        assertEquals("3 horas", WindowFormat.long(180))
        assertEquals("1 hora e 30 minutos", WindowFormat.long(90))
    }

    @Test
    fun `valores fora da faixa sao trazidos para dentro em vez de estourar`() {
        assertEquals("1 min", WindowFormat.short(0))
        assertEquals("1 min", WindowFormat.short(-10))
        assertEquals("24 h", WindowFormat.short(99_999))
    }

    @Test
    fun `horas inteiras nunca devolvem zero`() {
        // O seletor personalizado comeca em 1 h; devolver 0 deixaria o botao "0 h".
        assertEquals(1, WindowFormat.wholeHours(30))
        assertEquals(1, WindowFormat.wholeHours(60))
        assertEquals(3, WindowFormat.wholeHours(200))
    }

    @Test
    fun `reconhece um valor que nao esta entre as opcoes prontas`() {
        val prontas = listOf(5, 10, 15, 30, 60)
        assertFalse(WindowFormat.isCustom(30, prontas))
        assertTrue(WindowFormat.isCustom(120, prontas))
    }

    @Test
    fun `a descricao da regra usa o mesmo formatador`() {
        val regra = CallPolicy(2, 90 * 60_000L, PolicySource.GLOBAL)
        assertEquals("2 chamadas em 1 h 30", regra.describe())
    }
}
