package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Testes do motor de decisao.
 *
 * Kotlin puro: nenhum Robolectric, nenhum emulador, nenhuma dependencia do framework.
 */
class CallScreeningPolicyTest {

    private val policy = CallScreeningPolicy()

    private val window = TimeUnit.MINUTES.toMillis(15)
    private val baseTime = TimeUnit.HOURS.toMillis(22)
    private val numeroA = "+5511999990000"
    private val numeroB = "+5511988887777"

    private val defaultSettings = ProtectionSettings(
        protectionEnabled = true,
        maxAllowedCalls = 3,
        windowMillis = window,
        applyToContacts = false,
    )

    private fun minutes(v: Long) = TimeUnit.MINUTES.toMillis(v)

    /** Meio-dia de uma quarta-feira: fora de qualquer modo noturno padrao. */
    private val meioDiaQuarta: LocalDateTime = LocalDateTime.of(2026, 8, 26, 12, 0)

    private fun callAt(
        offsetMinutes: Long,
        number: String? = numeroA,
        settings: ProtectionSettings = defaultSettings,
        isAllowlisted: Boolean = false,
        isBlocklisted: Boolean = false,
        isSavedContact: Boolean = false,
        isEmergencyNumber: Boolean = false,
        isIncoming: Boolean = true,
        customRule: CustomRule? = null,
        schedule: SchedulePolicy = SchedulePolicy(),
        localDateTime: LocalDateTime = meioDiaQuarta,
    ) = IncomingCall(
        normalizedNumber = number,
        timestampMillis = baseTime + minutes(offsetMinutes),
        localDateTime = localDateTime,
        settings = settings,
        globalPolicy = settings.globalPolicy(),
        isAllowlisted = isAllowlisted,
        isBlocklisted = isBlocklisted,
        isSavedContact = isSavedContact,
        isEmergencyNumber = isEmergencyNumber,
        isIncoming = isIncoming,
        customRule = customRule,
        schedule = schedule,
    )

    private fun attemptsAt(vararg offsets: Long) = offsets.map { baseTime + minutes(it) }

    private fun assertAllow(d: ScreeningDecision, expected: AllowReason) {
        assertTrue("Esperava ALLOW, veio $d", d is ScreeningDecision.Allow)
        assertEquals(expected, (d as ScreeningDecision.Allow).reason)
    }

    private fun assertBlock(d: ScreeningDecision, reason: BlockReason, attempts: Int? = null) {
        assertTrue("Esperava BLOCK, veio $d", d is ScreeningDecision.Block)
        d as ScreeningDecision.Block
        assertEquals(reason, d.reason)
        attempts?.let { assertEquals(it, d.attemptsInWindow) }
    }

    // ------------------------------------------------ regra base (comportamento antigo)

    @Test
    fun `primeira chamada e permitida`() =
        assertAllow(policy.evaluate(callAt(1), emptyList()), AllowReason.UNDER_GLOBAL_LIMIT)

    @Test
    fun `segunda e terceira dentro da janela sao permitidas`() {
        assertAllow(policy.evaluate(callAt(3), attemptsAt(1)), AllowReason.UNDER_GLOBAL_LIMIT)
        assertAllow(policy.evaluate(callAt(5), attemptsAt(1, 3)), AllowReason.UNDER_GLOBAL_LIMIT)
    }

    @Test
    fun `quarta chamada dentro da janela e bloqueada`() =
        assertBlock(
            policy.evaluate(callAt(7), attemptsAt(1, 3, 5)),
            BlockReason.GLOBAL_LIMIT_EXCEEDED,
            attempts = 4,
        )

    @Test
    fun `quarta chamada fora da janela volta a ser permitida`() =
        assertAllow(policy.evaluate(callAt(60), attemptsAt(0, 2, 4)), AllowReason.UNDER_GLOBAL_LIMIT)

    @Test
    fun `contadores nao se misturam entre numeros diferentes`() {
        assertBlock(
            policy.evaluate(callAt(7, number = numeroA), attemptsAt(1, 3, 5)),
            BlockReason.GLOBAL_LIMIT_EXCEEDED,
        )
        assertAllow(
            policy.evaluate(callAt(8, number = numeroB), emptyList()),
            AllowReason.UNDER_GLOBAL_LIMIT,
        )
    }

    @Test
    fun `borda exata da janela fica de fora`() {
        val now = baseTime + minutes(15)
        assertEquals(0, policy.countInWindow(listOf(now - window), now, window))
        assertEquals(1, policy.countInWindow(listOf(now - window + 1), now, window))
        assertEquals(0, policy.countInWindow(listOf(now - window - 1), now, window))
    }

    @Test
    fun `timestamps no futuro sao ignorados`() {
        val now = baseTime + minutes(10)
        assertEquals(0, policy.countInWindow(listOf(now + 1000, now + 2000), now, window))
    }

    // ------------------------------------------------------------------- PRECEDENCIA

    @Test
    fun `emergencia vence tudo, inclusive blocklist`() =
        assertAllow(
            policy.evaluate(
                callAt(7, isEmergencyNumber = true, isBlocklisted = true),
                attemptsAt(1, 2, 3, 4, 5),
            ),
            AllowReason.EMERGENCY_NUMBER,
        )

    @Test
    fun `protecao desligada vence blocklist e regra personalizada`() =
        assertAllow(
            policy.evaluate(
                callAt(
                    7,
                    settings = defaultSettings.copy(protectionEnabled = false),
                    isBlocklisted = true,
                    customRule = CustomRule(numeroA, 1, minutes(10)),
                ),
                attemptsAt(1, 3, 5),
            ),
            AllowReason.PROTECTION_DISABLED,
        )

    @Test
    fun `numero indisponivel e permitido mesmo com historico cheio`() =
        assertAllow(
            policy.evaluate(callAt(7, number = null), attemptsAt(1, 3, 5)),
            AllowReason.UNSUPPORTED_CALL,
        )

    @Test
    fun `allowlist vence blocklist, regra personalizada e horario`() =
        assertAllow(
            policy.evaluate(
                callAt(
                    7,
                    isAllowlisted = true,
                    isBlocklisted = true,
                    customRule = CustomRule(numeroA, 1, minutes(10)),
                    schedule = madrugadaAtiva(),
                    localDateTime = LocalDateTime.of(2026, 8, 26, 23, 30),
                ),
                attemptsAt(1, 2, 3, 4, 5),
            ),
            AllowReason.ALLOWLISTED,
        )

    @Test
    fun `blocklist bloqueia na hora, sem consultar historico`() {
        val d = policy.evaluate(callAt(1, isBlocklisted = true), emptyList())
        assertBlock(d, BlockReason.PERMANENT_BLOCKLIST, attempts = 0)
    }

    @Test
    fun `blocklist vence a protecao de contatos`() =
        assertBlock(
            policy.evaluate(callAt(1, isBlocklisted = true, isSavedContact = true), emptyList()),
            BlockReason.PERMANENT_BLOCKLIST,
        )

    @Test
    fun `blocklist vence regra personalizada permissiva`() =
        assertBlock(
            policy.evaluate(
                callAt(1, isBlocklisted = true, customRule = CustomRule(numeroA, 99, minutes(10))),
                emptyList(),
            ),
            BlockReason.PERMANENT_BLOCKLIST,
        )

    @Test
    fun `contato protegido e permitido quando a regra nao se aplica a contatos`() =
        assertAllow(
            policy.evaluate(callAt(7, isSavedContact = true), attemptsAt(1, 3, 5)),
            AllowReason.CONTACT_EXEMPT,
        )

    @Test
    fun `contato salvo e bloqueado quando a regra se aplica a contatos`() =
        assertBlock(
            policy.evaluate(
                callAt(7, settings = defaultSettings.copy(applyToContacts = true), isSavedContact = true),
                attemptsAt(1, 3, 5),
            ),
            BlockReason.GLOBAL_LIMIT_EXCEEDED,
        )

    // --------------------------------------------------------- regras personalizadas

    @Test
    fun `regra personalizada vence a global`() {
        val regra = CustomRule(numeroA, maxAllowedCalls = 1, windowMillis = minutes(10))
        assertAllow(
            policy.evaluate(callAt(1, customRule = regra), emptyList()),
            AllowReason.UNDER_CUSTOM_LIMIT,
        )
        assertBlock(
            policy.evaluate(callAt(4, customRule = regra), attemptsAt(1)),
            BlockReason.CUSTOM_LIMIT_EXCEEDED,
            attempts = 2,
        )
    }

    @Test
    fun `regra personalizada mais permissiva que a global tambem vale`() {
        val regra = CustomRule(numeroA, maxAllowedCalls = 5, windowMillis = minutes(30))
        assertAllow(
            policy.evaluate(callAt(7, customRule = regra), attemptsAt(1, 2, 3, 4)),
            AllowReason.UNDER_CUSTOM_LIMIT,
        )
    }

    @Test
    fun `regra personalizada desabilitada e ignorada`() {
        val regra = CustomRule(numeroA, 1, minutes(10), enabled = false)
        assertAllow(
            policy.evaluate(callAt(3, customRule = regra), attemptsAt(1)),
            AllowReason.UNDER_GLOBAL_LIMIT,
        )
    }

    @Test
    fun `janela da regra personalizada expira sozinha`() {
        val regra = CustomRule(numeroA, 1, minutes(10))
        // Tentativa aos 0 min; nova chamada aos 11 min ja saiu da janela de 10.
        assertAllow(
            policy.evaluate(callAt(11, customRule = regra), attemptsAt(0)),
            AllowReason.UNDER_CUSTOM_LIMIT,
        )
    }

    @Test
    fun `regras de numeros diferentes nao se contaminam`() {
        val regraA = CustomRule(numeroA, 1, minutes(10))
        assertBlock(
            policy.evaluate(callAt(4, number = numeroA, customRule = regraA), attemptsAt(1)),
            BlockReason.CUSTOM_LIMIT_EXCEEDED,
        )
        // B nao tem regra propria: cai na global, que permite ate 3.
        assertAllow(
            policy.evaluate(callAt(4, number = numeroB, customRule = null), attemptsAt(1)),
            AllowReason.UNDER_GLOBAL_LIMIT,
        )
    }

    // ----------------------------------------------------------------- modo noturno

    private fun madrugadaAtiva() = SchedulePolicy(
        enabled = true,
        startMinuteOfDay = 22 * 60,
        endMinuteOfDay = 7 * 60,
        activeDays = DayOfWeek.entries.toSet(),
        maxAllowedCalls = 1,
        windowMillis = minutes(30),
    )

    @Test
    fun `modo noturno vence a regra global`() {
        val noite = LocalDateTime.of(2026, 8, 26, 23, 30)
        assertBlock(
            policy.evaluate(
                callAt(4, schedule = madrugadaAtiva(), localDateTime = noite),
                attemptsAt(1),
            ),
            BlockReason.SCHEDULE_LIMIT_EXCEEDED,
            attempts = 2,
        )
    }

    @Test
    fun `regra personalizada vence o modo noturno`() {
        val noite = LocalDateTime.of(2026, 8, 26, 23, 0)
        val joao = CustomRule(numeroA, maxAllowedCalls = 5, windowMillis = minutes(15))
        assertAllow(
            policy.evaluate(
                callAt(6, customRule = joao, schedule = madrugadaAtiva(), localDateTime = noite),
                attemptsAt(1, 2, 3, 4),
            ),
            AllowReason.UNDER_CUSTOM_LIMIT,
        )
    }

    @Test
    fun `fora do periodo vale a regra global`() {
        assertAllow(
            policy.evaluate(
                callAt(5, schedule = madrugadaAtiva(), localDateTime = meioDiaQuarta),
                attemptsAt(1, 3),
            ),
            AllowReason.UNDER_GLOBAL_LIMIT,
        )
    }

    @Test
    fun `allowlist continua valendo durante o modo noturno`() {
        val noite = LocalDateTime.of(2026, 8, 26, 23, 30)
        assertAllow(
            policy.evaluate(
                callAt(9, isAllowlisted = true, schedule = madrugadaAtiva(), localDateTime = noite),
                attemptsAt(1, 2, 3),
            ),
            AllowReason.ALLOWLISTED,
        )
    }

    @Test
    fun `blocklist continua valendo durante o modo noturno`() {
        val noite = LocalDateTime.of(2026, 8, 26, 23, 30)
        assertBlock(
            policy.evaluate(
                callAt(1, isBlocklisted = true, schedule = madrugadaAtiva(), localDateTime = noite),
                emptyList(),
            ),
            BlockReason.PERMANENT_BLOCKLIST,
        )
    }

    // ----------------------------------------------------- resolucao sem historico

    @Test
    fun `resolucao devolve decisao pronta quando nao precisa do historico`() {
        val r = policy.resolve(callAt(1, isAllowlisted = true))
        assertTrue(r is PolicyResolution.Immediate)
    }

    @Test
    fun `resolucao devolve a regra correta quando precisa do historico`() {
        val regra = CustomRule(numeroA, 2, minutes(20))
        val r = policy.resolve(callAt(1, customRule = regra))
        assertTrue(r is PolicyResolution.UseWindow)
        assertEquals(PolicySource.CUSTOM, (r as PolicyResolution.UseWindow).policy.source)
        assertEquals(2, r.policy.maxAllowedCalls)
    }

    @Test
    fun `chamada de saida nao e avaliada`() =
        assertAllow(policy.evaluate(callAt(7, isIncoming = false), attemptsAt(1, 3, 5)), AllowReason.NOT_INCOMING)

    @Test
    fun `limite 1 bloqueia ja na segunda chamada`() {
        val s = defaultSettings.copy(maxAllowedCalls = 1)
        assertAllow(policy.evaluate(callAt(1, settings = s), emptyList()), AllowReason.UNDER_GLOBAL_LIMIT)
        assertBlock(
            policy.evaluate(callAt(2, settings = s), attemptsAt(1)),
            BlockReason.GLOBAL_LIMIT_EXCEEDED,
            attempts = 2,
        )
    }
}
