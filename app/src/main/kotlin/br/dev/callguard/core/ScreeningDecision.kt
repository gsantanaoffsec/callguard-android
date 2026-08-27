package br.dev.callguard.core

/** Resultado da politica: a chamada toca normalmente ou e rejeitada. */
sealed interface ScreeningDecision {

    data class Allow(val reason: AllowReason) : ScreeningDecision

    data class Block(
        val reason: BlockReason,
        /** Tentativas dentro da janela contando a chamada atual. Usado apenas para exibicao. */
        val attemptsInWindow: Int,
    ) : ScreeningDecision

    val isBlocking: Boolean get() = this is Block
}

enum class AllowReason {
    /** Chamada de saida ou direcao desconhecida: a politica nao opina. */
    NOT_INCOMING,

    /** Numero de emergencia. Jamais bloqueado. */
    EMERGENCY_NUMBER,

    /** O interruptor mestre esta desligado. */
    PROTECTION_DISABLED,

    /** O Android nao forneceu um numero utilizavel para esta chamada. */
    NUMBER_NOT_AVAILABLE,

    /** Numero na lista de excecoes do usuario. */
    ALLOWLISTED,

    /** Contato salvo, com "aplicar aos contatos" desligado. */
    SAVED_CONTACT,

    /** Ainda dentro do limite configurado para a janela. */
    UNDER_LIMIT,

    /**
     * A decisao nao terminou dentro do orcamento de tempo do screening.
     * Falhamos permitindo a chamada: nunca derrubar uma ligacao por lentidao nossa.
     */
    TIMEOUT_FAILSAFE,

    /** Erro inesperado ao decidir. Tambem falha permitindo. */
    ERROR_FAILSAFE,
}

enum class BlockReason {
    /** "Limite de chamadas excedido" -- a unica razao de bloqueio deste app. */
    CALL_LIMIT_EXCEEDED,
}
