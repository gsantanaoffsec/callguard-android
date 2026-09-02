package br.dev.callguard.core

import java.time.LocalDateTime

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
     * apresentacao restrita. Nesse caso nao existe chave de agrupamento confiavel.
     */
    val normalizedNumber: String?,
    /** Instante da chamada (epoch millis). Injetado para permitir testes deterministicos. */
    val timestampMillis: Long,
    /** Mesmo instante em horario local, usado pela regra por horario. */
    val localDateTime: LocalDateTime,
    val settings: ProtectionSettings,
    /** Regra global vigente, derivada das configuracoes. */
    val globalPolicy: CallPolicy,
    /** Numero na lista de permitidos. */
    val isAllowlisted: Boolean = false,
    /** Numero na lista de bloqueados permanentes. */
    val isBlocklisted: Boolean = false,
    /**
     * Regra de faixa que pegou este numero, quando alguma pegou.
     *
     * Carregada junto com a chamada em vez de consultada dentro da politica: o motor e
     * Kotlin puro e nao conhece repositorio.
     */
    val matchedPattern: NumberPattern? = null,
    /** Numero presente na agenda do aparelho (so consultado quando faz diferenca). */
    val isSavedContact: Boolean = false,
    /** Numero classificado como emergencia pelo sistema. Nunca pode ser bloqueado. */
    val isEmergencyNumber: Boolean = false,
    /** `false` para chamadas de saida ou de direcao desconhecida. */
    val isIncoming: Boolean = true,
    /** Regra propria deste numero, quando existir e estiver ativa. */
    val customRule: CustomRule? = null,
    /** Perfil por horario configurado; a politica decide se esta valendo agora. */
    val schedule: SchedulePolicy = SchedulePolicy(),
)
