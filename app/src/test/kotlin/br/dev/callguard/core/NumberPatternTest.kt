package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O bloqueio por faixa é a única forma de barrar quem liga de muitos números.
 *
 * Também é o recurso com mais potencial de estrago: um padrão curto demais derruba um DDD
 * inteiro em silêncio. Os dois lados estão cobertos aqui.
 */
class NumberPatternTest {

    private fun padrao(raw: String, kind: NumberPattern.MatchKind = NumberPattern.MatchKind.STARTS_WITH) =
        NumberPattern.from(raw, "teste", kind)!!

    @Test
    fun `prefixo pega o numero em E164 sem o codigo do pais`() {
        // O normalizador entrega "+5511999998888"; a pessoa digita o DDD sem o +55.
        // Se so a forma completa fosse testada, nenhum padrao nacional funcionaria.
        assertTrue(padrao("11").matches("+5511999998888"))
        assertTrue(padrao("1199999").matches("+5511999998888"))
    }

    @Test
    fun `prefixo pega numero nao geografico que nao vira E164`() {
        // 0303 e codigo nao geografico: o normalizador nao consegue produzir E.164 e
        // devolve so os digitos. E exatamente o caso do telemarketing.
        assertTrue(padrao("0303").matches("03031234567"))
        assertTrue(padrao("1052").matches("1052"))
    }

    @Test
    fun `nao casa com numero de outro DDD`() {
        assertFalse(padrao("11").matches("+5521999998888"))
    }

    @Test
    fun `nao remove o 55 de um numero que nao declara o Brasil`() {
        // Sem o "+55" na frente, "55" e so um digito qualquer. Remove-lo transformaria
        // 5512345678 em 12345678 e criaria um casamento falso com o DDD 12.
        assertFalse(padrao("12").matches("5512345678"))
        assertTrue(padrao("55").matches("5512345678"))
    }

    @Test
    fun `contem acha os digitos em qualquer posicao`() {
        val p = padrao("4004", NumberPattern.MatchKind.CONTAINS)
        assertTrue(p.matches("+551140041234"))
        assertFalse(p.matches("+5511999998888"))
    }

    @Test
    fun `numero ausente nunca casa`() {
        // Chamada com apresentacao restrita chega sem numero. Casar aqui bloquearia
        // toda chamada privada por causa de um padrao qualquer.
        assertFalse(padrao("11").matches(null))
        assertFalse(padrao("11").matches(""))
        assertFalse(padrao("11").matches("   "))
    }

    @Test
    fun `padrao desativado nao casa`() {
        assertFalse(padrao("11").copy(enabled = false).matches("+5511999998888"))
    }

    @Test
    fun `formatacao digitada e ignorada`() {
        val p = NumberPattern.from("(11) 4004-", "Claro")!!
        assertEquals("11400 4".filter { it.isDigit() }, p.digits)
        assertTrue(p.matches("+551140041234"))
    }

    @Test
    fun `entrada curta demais e recusada em vez de virar regra perigosa`() {
        assertNull(NumberPattern.from("1", "x"))
        assertNull(NumberPattern.from("", "x"))
        assertNull(NumberPattern.from("abc", "x"))
    }

    @Test
    fun `a amplitude denuncia um padrao que pegaria um DDD inteiro`() {
        assertEquals(NumberPattern.Breadth.VERY_BROAD, padrao("11").breadth())
        assertEquals(NumberPattern.Breadth.BROAD, padrao("110").breadth())
        assertEquals(NumberPattern.Breadth.NARROW, padrao("1140").breadth())
    }

    @Test
    fun `sem rotulo o proprio padrao vira o nome`() {
        assertEquals("0303", NumberPattern.from("0303", "   ")!!.label)
    }

    @Test
    fun `a busca devolve o primeiro padrao que pega`() {
        val lista = listOf(padrao("21"), padrao("11"), padrao("1140"))
        assertEquals("11", lista.firstMatching("+5511400412345")?.digits)
        assertNull(lista.firstMatching("+5531999998888"))
    }
}
