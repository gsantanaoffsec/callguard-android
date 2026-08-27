package br.dev.callguard.core

/**
 * A regra de negocio, isolada do Android.
 *
 * Janela deslizante: ao chegar uma chamada de X em `now`, contamos quantas tentativas
 * anteriores de X existem no intervalo `(now - windowMillis, now]`. Se esse total ja
 * atingiu `maxAllowedCalls`, a chamada atual e rejeitada.
 *
 * Com o padrao `maxAllowedCalls = 3`:
 *   1a chamada -> 0 anteriores -> ALLOW
 *   2a chamada -> 1 anterior   -> ALLOW
 *   3a chamada -> 2 anteriores -> ALLOW
 *   4a chamada -> 3 anteriores -> BLOCK
 *
 * O bloqueio nao e permanente: as tentativas saem da janela sozinhas conforme o tempo
 * passa, entao quem para de ligar volta a ser tratado como primeira tentativa.
 *
 * Borda da janela: uma tentativa exatamente `windowMillis` atras esta FORA. A janela e
 * aberta no inicio e fechada no fim.
 */
class InsistentCallPolicy {

    /**
     * Decisoes que podem ser tomadas sem consultar o historico.
     *
     * O servico usa isto para evitar tocar no banco (e na agenda) quando ja da para
     * responder -- menos I/O dentro do orcamento de 5 s e menos dado manipulado.
     * Retorna `null` quando o historico e realmente necessario.
     */
    fun preScreen(call: IncomingCall): ScreeningDecision.Allow? = when {
        !call.isIncoming -> ScreeningDecision.Allow(AllowReason.NOT_INCOMING)
        call.isEmergencyNumber -> ScreeningDecision.Allow(AllowReason.EMERGENCY_NUMBER)
        !call.settings.protectionEnabled -> ScreeningDecision.Allow(AllowReason.PROTECTION_DISABLED)
        call.normalizedNumber.isNullOrBlank() ->
            ScreeningDecision.Allow(AllowReason.NUMBER_NOT_AVAILABLE)
        call.isAllowlisted -> ScreeningDecision.Allow(AllowReason.ALLOWLISTED)
        call.isSavedContact && !call.settings.applyToContacts ->
            ScreeningDecision.Allow(AllowReason.SAVED_CONTACT)
        else -> null
    }

    /**
     * Decisao completa.
     *
     * @param previousAttempts instantes das tentativas ANTERIORES do mesmo numero.
     *   Podem vir sujas: timestamps fora da janela sao descartados aqui.
     *   A chamada atual nao deve estar nesta lista.
     */
    fun evaluate(call: IncomingCall, previousAttempts: List<Long>): ScreeningDecision {
        preScreen(call)?.let { return it }

        val attemptsInWindow = countInWindow(
            attempts = previousAttempts,
            now = call.timestampMillis,
            windowMillis = call.settings.windowMillis,
        )

        return if (attemptsInWindow >= call.settings.maxAllowedCalls) {
            ScreeningDecision.Block(
                reason = BlockReason.CALL_LIMIT_EXCEEDED,
                attemptsInWindow = attemptsInWindow + 1,
            )
        } else {
            ScreeningDecision.Allow(AllowReason.UNDER_LIMIT)
        }
    }

    /**
     * Conta tentativas dentro de `(now - windowMillis, now]`.
     *
     * Timestamps no futuro sao ignorados: protege contra ajuste de relogio do usuario
     * que, de outra forma, poderia inflar o contador com registros invalidos.
     */
    fun countInWindow(attempts: List<Long>, now: Long, windowMillis: Long): Int {
        val windowStart = now - windowMillis
        return attempts.count { it > windowStart && it <= now }
    }
}
