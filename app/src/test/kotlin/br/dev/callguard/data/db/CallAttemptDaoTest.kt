package br.dev.callguard.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Testes da transacao que sustenta a garantia de ausencia de race condition.
 *
 * Esta e a parte mais delicada do app: a leitura da janela e a gravacao da tentativa
 * precisam ser atomicas, senao duas chamadas quase simultaneas leem o mesmo contador e
 * ambas passam. Room roda sobre SQLite de verdade aqui (via Robolectric), entao o que
 * esta sendo verificado e o comportamento real, nao uma simulacao.
 */
@RunWith(RobolectricTestRunner::class)
class CallAttemptDaoTest {

    private lateinit var database: CallGuardDatabase
    private lateinit var dao: CallAttemptDao

    private val window = TimeUnit.MINUTES.toMillis(15)
    private val retention = TimeUnit.HOURS.toMillis(6)
    private val number = "+5511999990000"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CallGuardDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.callAttemptDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `grava a tentativa e devolve apenas as anteriores`() = runBlocking {
        val now = 1_000_000L

        val primeira = dao.recordAttemptAndGetPrevious(number, now, window, retention)
        assertEquals("A primeira chamada nao tem anteriores", 0, primeira.size)

        val segunda = dao.recordAttemptAndGetPrevious(number, now + 1000, window, retention)
        assertEquals("A segunda ve a primeira", listOf(now), segunda)

        assertEquals("As duas foram gravadas", 2, dao.count())
    }

    @Test
    fun `tentativas fora da janela nao sao devolvidas`() = runBlocking {
        val inicio = 10_000_000L
        dao.recordAttemptAndGetPrevious(number, inicio, window, retention)

        val depois = inicio + window + 1
        val anteriores = dao.recordAttemptAndGetPrevious(number, depois, window, retention)

        assertTrue("A tentativa antiga saiu da janela", anteriores.isEmpty())
    }

    @Test
    fun `numeros diferentes nao compartilham historico`() = runBlocking {
        val outro = "+5511988887777"
        val now = 5_000_000L

        repeat(3) { i ->
            dao.recordAttemptAndGetPrevious(number, now + i * 1000L, window, retention)
        }

        val anterioresDoOutro =
            dao.recordAttemptAndGetPrevious(outro, now + 4000L, window, retention)
        assertTrue("O historico de B esta vazio", anterioresDoOutro.isEmpty())
    }

    @Test
    fun `a retencao remove tentativas antigas demais`() = runBlocking {
        val antigo = 1_000L
        dao.recordAttemptAndGetPrevious(number, antigo, window, retention)
        assertEquals(1, dao.count())

        // Uma chamada muito depois: a antiga esta alem da retencao e deve sumir do banco.
        val agora = antigo + retention + TimeUnit.HOURS.toMillis(1)
        dao.recordAttemptAndGetPrevious(number, agora, window, retention)

        assertEquals("Sobrou apenas a tentativa nova", 1, dao.count())
    }

    /**
     * O teste que importa: 20 chamadas do mesmo numero disparadas ao mesmo tempo.
     *
     * Se a leitura e a escrita nao fossem atomicas, varias delas leriam a mesma contagem
     * anterior e teriamos valores repetidos -- exatamente a race condition que deixaria
     * chamadas passarem alem do limite. Com a transacao, cada uma enxerga o resultado da
     * anterior e as contagens formam a sequencia 0, 1, 2, ..., 19 sem repetir.
     */
    @Test
    fun `chamadas simultaneas do mesmo numero nao leem a mesma contagem`() = runBlocking {
        val total = 20
        val now = 20_000_000L

        val contagens = (0 until total)
            .map { i ->
                async {
                    dao.recordAttemptAndGetPrevious(
                        number = number,
                        now = now + i,
                        windowMillis = window,
                        retentionMillis = retention,
                    ).size
                }
            }
            .awaitAll()

        assertEquals("Toda tentativa foi gravada", total, dao.count())
        assertEquals(
            "Nenhuma contagem pode se repetir: isso seria a race condition",
            (0 until total).toList(),
            contagens.sorted(),
        )
    }

    /**
     * Duas chamadas simultaneas de numeros diferentes nao podem contaminar uma a outra,
     * mesmo compartilhando a mesma transacao de banco.
     */
    @Test
    fun `chamadas simultaneas de numeros diferentes mantem historicos separados`() = runBlocking {
        val a = "+5511999990000"
        val b = "+5511988887777"
        val now = 30_000_000L

        val resultados = (0 until 10)
            .map { i ->
                val alvo = if (i % 2 == 0) a else b
                async {
                    alvo to dao.recordAttemptAndGetPrevious(
                        number = alvo,
                        now = now + i,
                        windowMillis = window,
                        retentionMillis = retention,
                    ).size
                }
            }
            .awaitAll()

        val deA = resultados.filter { it.first == a }.map { it.second }.sorted()
        val deB = resultados.filter { it.first == b }.map { it.second }.sorted()

        assertEquals("A viu apenas o proprio historico", (0 until 5).toList(), deA)
        assertEquals("B viu apenas o proprio historico", (0 until 5).toList(), deB)
    }
}
