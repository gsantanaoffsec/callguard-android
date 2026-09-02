package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A sugestão é o que transforma "bloqueie a Claro" em algo executável.
 *
 * Também é o lugar onde um erro é caro: uma sugestão ruim tem aparência de recomendação, e
 * a pessoa aceita sem conferir.
 */
class PatternSuggesterTest {

    @Test
    fun `acha o prefixo repetido entre numeros diferentes`() {
        val sugestoes = PatternSuggester.suggest(
            listOf("+5511400412345", "+5511400467890", "+5521999998888"),
        )
        assertTrue(sugestoes.isNotEmpty())
        assertTrue("deveria propor o prefixo comum", sugestoes.first().digits.startsWith("1140"))
        assertEquals(2, sugestoes.first().distinctNumbers)
    }

    @Test
    fun `um numero sozinho nao vira padrao`() {
        // Uma ligacao unica e coincidencia, nao faixa.
        assertTrue(PatternSuggester.suggest(listOf("+5511400412345")).isEmpty())
    }

    @Test
    fun `nunca sugere prefixo que pegaria um numero permitido`() {
        // O app discordando de uma decisao explicita do usuario -- e com cara de
        // recomendacao -- e pior do que nao sugerir nada.
        val sugestoes = PatternSuggester.suggest(
            numbers = listOf("+5511400412345", "+5511400467890"),
            protectedNumbers = setOf("+5511400467890"),
        )
        assertTrue(sugestoes.none { it.digits.startsWith("1140") })
    }

    @Test
    fun `nao repete o que ja esta bloqueado`() {
        val sugestoes = PatternSuggester.suggest(
            numbers = listOf("+5511400412345", "+5511400467890"),
            existingPatterns = listOf(NumberPattern("1140", "ja bloqueado")),
        )
        assertTrue(sugestoes.isEmpty())
    }

    @Test
    fun `prefere o prefixo mais longo quando a cobertura e a mesma`() {
        // 1140 e 114004 pegam os mesmos dois numeros; o mais longo e igualmente eficaz e
        // menos abrangente.
        val sugestoes = PatternSuggester.suggest(
            listOf("+5511400412345", "+5511400467890"),
        )
        assertEquals(1, sugestoes.size)
        assertTrue(
            "deveria escolher o mais especifico, veio ${sugestoes.first().digits}",
            sugestoes.first().digits.length >= 6,
        )
    }

    @Test
    fun `nunca sugere prefixo curto o bastante para pegar um DDD inteiro`() {
        val sugestoes = PatternSuggester.suggest(
            listOf("+5511111112222", "+5511222223333", "+5511333334444"),
        )
        sugestoes.forEach {
            assertTrue("prefixo curto demais: ${it.digits}", it.digits.length >= 4)
        }
    }

    @Test
    fun `funciona com numero nao geografico que nao vira E164`() {
        val sugestoes = PatternSuggester.suggest(listOf("03031234567", "03039876543"))
        assertTrue(sugestoes.first().digits.startsWith("0303"))
    }

    @Test
    fun `as amostras acompanham a sugestao para a tela poder mostrar`() {
        val sugestoes = PatternSuggester.suggest(
            listOf("+5511400412345", "+5511400467890"),
        )
        assertEquals(2, sugestoes.first().samples.size)
    }

    @Test
    fun `ordena pelo que pega mais numeros`() {
        val sugestoes = PatternSuggester.suggest(
            numbers = listOf(
                "+5511400412345", "+5511400467890", "+5511400411111",
                "+5521555512345", "+5521555567890",
            ),
            limit = 5,
        )
        assertTrue(sugestoes.first().distinctNumbers >= sugestoes.last().distinctNumbers)
        assertEquals(3, sugestoes.first().distinctNumbers)
    }

    @Test
    fun `lista vazia nao quebra`() {
        assertTrue(PatternSuggester.suggest(emptyList()).isEmpty())
        assertTrue(PatternSuggester.suggest(listOf("", "  ")).isEmpty())
    }
}
