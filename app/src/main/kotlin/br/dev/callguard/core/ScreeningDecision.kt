package br.dev.callguard.core

/** Resultado da politica: a chamada toca normalmente ou e rejeitada. */
sealed interface ScreeningDecision {

    data class Allow(
        val reason: AllowReason,
        /** Regra consultada, quando houve consulta ao historico. */
        val policy: CallPolicy? = null,
    ) : ScreeningDecision

    data class Block(
        val reason: BlockReason,
        /** Tentativas dentro da janela contando a chamada atual. Zero na blocklist. */
        val attemptsInWindow: Int = 0,
        val policy: CallPolicy? = null,
    ) : ScreeningDecision

    val isBlocking: Boolean get() = this is Block
}

enum class AllowReason {
    /** Chamada de saida ou direcao desconhecida: a politica nao opina. */
    NOT_INCOMING,

    /** Numero de emergencia. Jamais bloqueado -- regra absoluta. */
    EMERGENCY_NUMBER,

    /** Interruptor mestre desligado, ou o app deixou de ser o filtro de chamadas. */
    PROTECTION_DISABLED,

    /**
     * O Android nao forneceu um numero utilizavel (apresentacao restrita, esquema
     * diferente de `tel:`). Sem chave nao ha como contar tentativas, e inventar um
     * identificador unico faria todas as chamadas privadas dividirem o mesmo contador.
     */
    UNSUPPORTED_CALL,

    /** Numero na lista de permitidos do usuario. */
    ALLOWLISTED,

    /** Contato salvo, com "aplicar aos contatos" desligado. */
    CONTACT_EXEMPT,

    UNDER_GLOBAL_LIMIT,
    UNDER_CUSTOM_LIMIT,
    UNDER_SCHEDULE_LIMIT,

    /**
     * A decisao nao terminou dentro do orcamento de tempo.
     * Falha permitindo: nunca derrubar uma ligacao por lentidao nossa.
     */
    TIMEOUT_FAILSAFE,

    /** Erro inesperado ao decidir. Tambem falha permitindo. */
    ERROR_FAILSAFE,
}

enum class BlockReason {
    /** O usuario mandou nunca aceitar este numero. */
    PERMANENT_BLOCKLIST,

    GLOBAL_LIMIT_EXCEEDED,
    CUSTOM_LIMIT_EXCEEDED,
    SCHEDULE_LIMIT_EXCEEDED,
}

/** Motivo de bloqueio correspondente a origem da regra que estourou. */
fun PolicySource.blockReason(): BlockReason = when (this) {
    PolicySource.GLOBAL -> BlockReason.GLOBAL_LIMIT_EXCEEDED
    PolicySource.SCHEDULE -> BlockReason.SCHEDULE_LIMIT_EXCEEDED
    PolicySource.CUSTOM -> BlockReason.CUSTOM_LIMIT_EXCEEDED
}

/** Motivo de permissao correspondente a origem da regra respeitada. */
fun PolicySource.underLimitReason(): AllowReason = when (this) {
    PolicySource.GLOBAL -> AllowReason.UNDER_GLOBAL_LIMIT
    PolicySource.SCHEDULE -> AllowReason.UNDER_SCHEDULE_LIMIT
    PolicySource.CUSTOM -> AllowReason.UNDER_CUSTOM_LIMIT
}
