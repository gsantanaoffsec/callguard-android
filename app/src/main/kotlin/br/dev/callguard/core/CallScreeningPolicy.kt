package br.dev.callguard.core

/**
 * O motor de decisao, isolado do Android.
 *
 * A responsabilidade esta dividida em duas etapas, e isso e proposital:
 *
 *  - [resolve] responde "qual regra vale para esta chamada, agora?" sem tocar no
 *    historico. Quando a resposta ja e a propria decisao (emergencia, allowlist,
 *    blocklist...), ela vem pronta e o banco nem chega a ser aberto.
 *  - [evaluate] aplica a janela deslizante da regra resolvida.
 *
 * Sem essa separacao, cada nova excecao viraria mais um `if` disputando espaco com as
 * outras, e a ordem de precedencia acabaria dependendo de onde alguem escreveu a
 * condicao. Aqui a ordem e explicita e testavel.
 *
 * Precedencia (da mais forte para a mais fraca):
 *
 *  1. Emergencia            -> ALLOW, absoluto
 *  2. Protecao inativa      -> ALLOW
 *  3. Numero indisponivel   -> ALLOW
 *  4. Allowlist             -> ALLOW
 *  5. Blocklist             -> BLOCK
 *  6. Faixa bloqueada       -> BLOCK
 *  7. Contato protegido     -> ALLOW
 *  8. Regra do numero       -> janela propria
 *  9. Regra por horario     -> janela do periodo
 * 10. Regra global          -> janela padrao
 *
 * A blocklist vem depois da allowlist porque as duas sao mutuamente exclusivas por
 * construcao; e vem ANTES da protecao de contatos porque uma acao manual do usuario
 * sobre um numero especifico e mais especifica do que a protecao generica da agenda.
 */
class CallScreeningPolicy {

    /**
     * Qual regra se aplica a esta chamada neste instante.
     *
     * @return [PolicyResolution.Immediate] quando a decisao sai sem historico, ou
     *   [PolicyResolution.UseWindow] com a regra a ser aplicada sobre as tentativas.
     */
    fun resolve(call: IncomingCall): PolicyResolution {
        // 1. Emergencia: nenhuma configuracao sobrescreve.
        if (call.isEmergencyNumber) {
            return allow(AllowReason.EMERGENCY_NUMBER)
        }
        if (!call.isIncoming) {
            return allow(AllowReason.NOT_INCOMING)
        }
        // 2. Protecao desligada.
        if (!call.settings.protectionEnabled) {
            return allow(AllowReason.PROTECTION_DISABLED)
        }
        // 3. Sem numero utilizavel nao ha como agrupar tentativas.
        if (call.normalizedNumber.isNullOrBlank()) {
            return allow(AllowReason.UNSUPPORTED_CALL)
        }
        // 4. Permissao explicita do usuario vence tudo o que vem abaixo.
        if (call.isAllowlisted) {
            return allow(AllowReason.ALLOWLISTED)
        }
        // 5. Bloqueio explicito do usuario, sem consultar historico.
        if (call.isBlocklisted) {
            return PolicyResolution.Immediate(
                ScreeningDecision.Block(BlockReason.PERMANENT_BLOCKLIST),
            )
        }
        // 6. Faixa de numeros bloqueada. Vem depois do numero exato porque o exato e mais
        //    especifico, e ANTES da protecao de contatos pela mesma razao que o bloqueio
        //    permanente: uma faixa que a pessoa escreveu a mao e uma decisao sobre aqueles
        //    numeros, mais especifica que a protecao generica da agenda.
        call.matchedPattern?.takeIf { it.enabled }?.let {
            return PolicyResolution.Immediate(
                ScreeningDecision.Block(BlockReason.BLOCKED_PATTERN),
            )
        }
        // 7. Protecao generica de contatos.
        if (call.isSavedContact && !call.settings.applyToContacts) {
            return allow(AllowReason.CONTACT_EXEMPT)
        }
        // 8. Regra do proprio numero, mais especifica que horario e global.
        call.customRule?.takeIf { it.enabled }?.let {
            return PolicyResolution.UseWindow(it.toPolicy())
        }
        // 9. Periodo especial valendo agora.
        if (call.schedule.isActiveAt(call.localDateTime)) {
            return PolicyResolution.UseWindow(call.schedule.toPolicy())
        }
        // 10. Regra geral.
        return PolicyResolution.UseWindow(call.globalPolicy)
    }

    /**
     * Decisao completa.
     *
     * @param previousAttempts instantes das tentativas ANTERIORES do mesmo numero.
     *   A chamada atual nao deve estar nesta lista. Timestamps fora da janela sao
     *   descartados aqui, entao a lista pode vir suja.
     */
    fun evaluate(call: IncomingCall, previousAttempts: List<Long>): ScreeningDecision {
        val policy = when (val r = resolve(call)) {
            is PolicyResolution.Immediate -> return r.decision
            is PolicyResolution.UseWindow -> r.policy
        }

        val attemptsInWindow = countInWindow(
            attempts = previousAttempts,
            now = call.timestampMillis,
            windowMillis = policy.windowMillis,
        )

        return if (attemptsInWindow >= policy.maxAllowedCalls) {
            ScreeningDecision.Block(
                reason = policy.source.blockReason(),
                attemptsInWindow = attemptsInWindow + 1,
                policy = policy,
            )
        } else {
            ScreeningDecision.Allow(policy.source.underLimitReason(), policy)
        }
    }

    /**
     * Conta tentativas dentro de `(now - windowMillis, now]`.
     *
     * Uma tentativa exatamente `windowMillis` atras esta FORA: a janela e aberta no
     * inicio e fechada no fim. Timestamps no futuro sao ignorados, para que um ajuste de
     * relogio do usuario nao infle o contador.
     */
    fun countInWindow(attempts: List<Long>, now: Long, windowMillis: Long): Int {
        val windowStart = now - windowMillis
        return attempts.count { it > windowStart && it <= now }
    }

    private fun allow(reason: AllowReason) =
        PolicyResolution.Immediate(ScreeningDecision.Allow(reason))
}

/** O que a resolucao de politica devolve antes de qualquer consulta ao historico. */
sealed interface PolicyResolution {

    /** Decisao pronta: nao ha necessidade de abrir o banco. */
    data class Immediate(val decision: ScreeningDecision) : PolicyResolution

    /** Regra a ser aplicada sobre as tentativas recentes do numero. */
    data class UseWindow(val policy: CallPolicy) : PolicyResolution
}
