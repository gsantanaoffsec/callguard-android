package br.dev.callguard.core

/**
 * Tudo que a politica precisa saber sobre uma chamada, ja traduzido para tipos neutros.
 *
 * Nada aqui depende de `android.telecom.Call.Details`: o servico de screening faz a
 * traducao, e os testes montam esta estrutura diretamente.
 */
data class IncomingCall(
    /**
     * Numero ja normalizado (E.164 quando possivel).
     *
     * `null` quando o Android nao forneceu o numero -- por exemplo chamadas com
     * apresentacao restrita/desconhecida. Nesse caso nao existe chave de agrupamento
     * confiavel e a chamada e sempre permitida.
     */
    val normalizedNumber: String?,
    /** Instante da chamada (epoch millis). Injetado para permitir testes deterministicos. */
    val timestampMillis: Long,
    val settings: ProtectionSettings,
    /** Numero cadastrado na lista de excecoes do proprio app. */
    val isAllowlisted: Boolean = false,
    /** Numero presente na agenda do aparelho (so consultado quando faz diferenca). */
    val isSavedContact: Boolean = false,
    /** Numero classificado como emergencia pelo sistema. Nunca pode ser bloqueado. */
    val isEmergencyNumber: Boolean = false,
    /** `false` para chamadas de saida ou de direcao desconhecida. */
    val isIncoming: Boolean = true,
)
