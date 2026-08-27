package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Testes da regra de repeticao.
 *
 * Kotlin puro: nenhum Robolectric, nenhum emulador, nenhuma dependencia do framework.
 * Isso e o que torna a politica verificavel de verdade.
 */
class InsistentCallPolicyTest {

    private val policy = InsistentCallPolicy()

    private val window = TimeUnit.MINUTES.toMillis(15)
    private val baseTime = TimeUnit.HOURS.toMillis(22) // 22:00 de um dia qualquer

    private val defaultSettings = ProtectionSettings(
        protectionEnabled = true,
        maxAllowedCalls = 3,
        windowMillis = window,
        applyToContacts = false,
    )

    private fun minutes(value: Long) = TimeUnit.MINUTES.toMillis(value)

    private fun callAt(
        offsetMinutes: Long,
        number: String? = "+5511999990000",
        settings: ProtectionSettings = defaultSettings,
        isAllowlisted: Boolean = false,
        isSavedContact: Boolean = false,
        isEmergencyNumber: Boolean = false,
        isIncoming: Boolean = true,
    ) = IncomingCall(
        normalizedNumber = number,
        timestampMillis = baseTime + minutes(offsetMinutes),
        settings = settings,
        isAllowlisted = isAllowlisted,
        isSavedContact = isSavedContact,
        isEmergencyNumber = isEmergencyNumber,
        isIncoming = isIncoming,
    )

    private fun attemptsAt(vararg offsetMinutes: Long) =
        offsetMinutes.map { baseTime + minutes(it) }

    private fun assertAllow(decision: ScreeningDecision, expected: AllowReason) {
        assertTrue("Esperava ALLOW, veio $decision", decision is ScreeningDecision.Allow)
        assertEquals(expected, (decision as ScreeningDecision.Allow).reason)
    }

    private fun assertBlock(decision: ScreeningDecision, expectedAttempts: Int) {
        assertTrue("Esperava BLOCK, veio $decision", decision is ScreeningDecision.Block)
        decision as ScreeningDecision.Block
        assertEquals(BlockReason.CALL_LIMIT_EXCEEDED, decision.reason)
        assertEquals(expectedAttempts, decision.attemptsInWindow)
    }

    // ---------------------------------------------------------------- Caso 1

    @Test
    fun `caso 1 - primeira chamada e permitida`() {
        val decision = policy.evaluate(callAt(1), previousAttempts = emptyList())
        assertAllow(decision, AllowReason.UNDER_LIMIT)
    }

    // ---------------------------------------------------------------- Caso 2

    @Test
    fun `caso 2 - segunda chamada dentro da janela e permitida`() {
        val decision = policy.evaluate(callAt(3), previousAttempts = attemptsAt(1))
        assertAllow(decision, AllowReason.UNDER_LIMIT)
    }

    // ---------------------------------------------------------------- Caso 3

    @Test
    fun `caso 3 - terceira chamada dentro da janela e permitida`() {
        val decision = policy.evaluate(callAt(5), previousAttempts = attemptsAt(1, 3))
        assertAllow(decision, AllowReason.UNDER_LIMIT)
    }

    // ---------------------------------------------------------------- Caso 4

    @Test
    fun `caso 4 - quarta chamada dentro da janela e bloqueada`() {
        val decision = policy.evaluate(callAt(7), previousAttempts = attemptsAt(1, 3, 5))
        assertBlock(decision, expectedAttempts = 4)
    }

    @Test
    fun `caso 4b - quinta chamada dentro da janela continua bloqueada`() {
        val decision = policy.evaluate(callAt(9), previousAttempts = attemptsAt(1, 3, 5, 7))
        assertBlock(decision, expectedAttempts = 5)
    }

    // ---------------------------------------------------------------- Caso 5

    @Test
    fun `caso 5 - quarta chamada fora da janela volta a ser permitida`() {
        // 10:00, 10:02, 10:04 e depois so as 11:00: as tres primeiras ja sairam da janela.
        val decision = policy.evaluate(callAt(60), previousAttempts = attemptsAt(0, 2, 4))
        assertAllow(decision, AllowReason.UNDER_LIMIT)
    }

    @Test
    fun `caso 5b - janela expira parcialmente e libera a chamada`() {
        // Tentativas em 0, 2 e 4. Aos 18 minutos, apenas 4 ainda esta dentro dos 15 min.
        val decision = policy.evaluate(callAt(18), previousAttempts = attemptsAt(0, 2, 4))
        assertAllow(decision, AllowReason.UNDER_LIMIT)
        assertEquals(1, policy.countInWindow(attemptsAt(0, 2, 4), baseTime + minutes(18), window))
    }

    // ---------------------------------------------------------------- Caso 6

    @Test
    fun `caso 6 - contadores nao se misturam entre numeros diferentes`() {
        val numberA = "+5511999990000"
        val numberB = "+5511988887777"

        // A ja ligou tres vezes; a quarta dele e bloqueada.
        val decisionA = policy.evaluate(
            callAt(7, number = numberA),
            previousAttempts = attemptsAt(1, 3, 5),
        )
        assertBlock(decisionA, expectedAttempts = 4)

        // B liga pela primeira vez: o historico dele esta vazio e ele passa.
        val decisionB = policy.evaluate(
            callAt(8, number = numberB),
            previousAttempts = emptyList(),
        )
        assertAllow(decisionB, AllowReason.UNDER_LIMIT)
    }

    // ---------------------------------------------------------------- Caso 7

    @Test
    fun `caso 7 - numero na allowlist nunca e bloqueado`() {
        val manyAttempts = attemptsAt(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        repeat(5) { index ->
            val decision = policy.evaluate(
                callAt(11L + index, isAllowlisted = true),
                previousAttempts = manyAttempts,
            )
            assertAllow(decision, AllowReason.ALLOWLISTED)
        }
    }

    // ---------------------------------------------------------------- Caso 8

    @Test
    fun `caso 8 - protecao desligada permite qualquer chamada`() {
        val settings = defaultSettings.copy(protectionEnabled = false)
        val decision = policy.evaluate(
            callAt(7, settings = settings),
            previousAttempts = attemptsAt(1, 3, 5),
        )
        assertAllow(decision, AllowReason.PROTECTION_DISABLED)
    }

    // ---------------------------------------------------------------- Caso 9

    @Test
    fun `caso 9 - contato salvo e permitido quando a regra nao se aplica a contatos`() {
        val decision = policy.evaluate(
            callAt(7, isSavedContact = true),
            previousAttempts = attemptsAt(1, 3, 5),
        )
        assertAllow(decision, AllowReason.SAVED_CONTACT)
    }

    @Test
    fun `caso 9b - contato salvo e bloqueado quando a regra se aplica a contatos`() {
        val settings = defaultSettings.copy(applyToContacts = true)
        val decision = policy.evaluate(
            callAt(7, settings = settings, isSavedContact = true),
            previousAttempts = attemptsAt(1, 3, 5),
        )
        assertBlock(decision, expectedAttempts = 4)
    }

    // ---------------------------------------------------------------- Caso 10

    @Test
    fun `caso 10 - tentativa exatamente na borda da janela fica de fora`() {
        val now = baseTime + minutes(15)
        // Tentativa exatamente 15 minutos antes: fora (janela aberta no inicio).
        val onBoundary = listOf(now - window)
        assertEquals(0, policy.countInWindow(onBoundary, now, window))

        // Um milissegundo depois da borda: dentro.
        val insideBoundary = listOf(now - window + 1)
        assertEquals(1, policy.countInWindow(insideBoundary, now, window))

        // Um milissegundo antes da borda: fora.
        val outsideBoundary = listOf(now - window - 1)
        assertEquals(0, policy.countInWindow(outsideBoundary, now, window))
    }

    @Test
    fun `caso 10b - a chamada de numero limite mais um e a primeira bloqueada na borda`() {
        val now = baseTime + minutes(15)
        // Tres tentativas: uma exatamente na borda (nao conta) e duas dentro.
        val attempts = listOf(now - window, now - minutes(5), now - minutes(1))
        val decision = policy.evaluate(
            IncomingCall(
                normalizedNumber = "+5511999990000",
                timestampMillis = now,
                settings = defaultSettings,
            ),
            previousAttempts = attempts,
        )
        // Somente 2 contam -> ainda abaixo do limite de 3.
        assertAllow(decision, AllowReason.UNDER_LIMIT)
    }

    @Test
    fun `caso 10c - timestamps no futuro sao ignorados`() {
        val now = baseTime + minutes(10)
        val attempts = listOf(now + minutes(1), now + minutes(2), now + minutes(3))
        assertEquals(0, policy.countInWindow(attempts, now, window))
    }

    // ---------------------------------------------- Casos adicionais de robustez

    @Test
    fun `numero de emergencia nunca e bloqueado`() {
        val decision = policy.evaluate(
            callAt(7, number = "190", isEmergencyNumber = true),
            previousAttempts = attemptsAt(1, 2, 3, 4, 5),
        )
        assertAllow(decision, AllowReason.EMERGENCY_NUMBER)
    }

    @Test
    fun `chamada sem numero disponivel e permitida`() {
        val decision = policy.evaluate(
            callAt(7, number = null),
            previousAttempts = attemptsAt(1, 3, 5),
        )
        assertAllow(decision, AllowReason.NUMBER_NOT_AVAILABLE)
    }

    @Test
    fun `chamada de saida nao e avaliada pela regra`() {
        val decision = policy.evaluate(
            callAt(7, isIncoming = false),
            previousAttempts = attemptsAt(1, 3, 5),
        )
        assertAllow(decision, AllowReason.NOT_INCOMING)
    }

    @Test
    fun `limite igual a 1 bloqueia ja na segunda chamada`() {
        val settings = defaultSettings.copy(maxAllowedCalls = 1)
        assertAllow(
            policy.evaluate(callAt(1, settings = settings), emptyList()),
            AllowReason.UNDER_LIMIT,
        )
        assertBlock(
            policy.evaluate(callAt(2, settings = settings), attemptsAt(1)),
            expectedAttempts = 2,
        )
    }

    @Test
    fun `limite igual a 5 permite as cinco primeiras e bloqueia a sexta`() {
        val settings = defaultSettings.copy(maxAllowedCalls = 5)
        assertAllow(
            policy.evaluate(callAt(6, settings = settings), attemptsAt(1, 2, 3, 4)),
            AllowReason.UNDER_LIMIT,
        )
        assertBlock(
            policy.evaluate(callAt(7, settings = settings), attemptsAt(1, 2, 3, 4, 5)),
            expectedAttempts = 6,
        )
    }

    @Test
    fun `janela de cinco minutos e mais rigorosa que a de uma hora`() {
        val shortWindow = defaultSettings.copy(windowMillis = minutes(5))
        val longWindow = defaultSettings.copy(windowMillis = minutes(60))
        val attempts = attemptsAt(0, 20, 40)

        // Com 5 minutos, nada disso ainda conta na chamada dos 50 minutos.
        assertAllow(
            policy.evaluate(callAt(50, settings = shortWindow), attempts),
            AllowReason.UNDER_LIMIT,
        )
        // Com 60 minutos, as tres contam e a quarta e bloqueada.
        assertBlock(
            policy.evaluate(callAt(50, settings = longWindow), attempts),
            expectedAttempts = 4,
        )
    }

    @Test
    fun `preScreen nao pede historico quando ja da para decidir`() {
        assertEquals(
            ScreeningDecision.Allow(AllowReason.ALLOWLISTED),
            policy.preScreen(callAt(1, isAllowlisted = true)),
        )
        // Precisa do historico: retorna null.
        assertNull(policy.preScreen(callAt(1)))
    }
}
