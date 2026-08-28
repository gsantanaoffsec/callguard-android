package br.dev.callguard.core

import java.util.concurrent.TimeUnit

/**
 * Configuracoes do usuario que alimentam a regra de decisao.
 *
 * Kotlin puro de proposito: a politica precisa ser testavel sem o framework Android.
 */
data class ProtectionSettings(
    /** Interruptor mestre. Quando `false`, nenhuma chamada e rejeitada pela regra. */
    val protectionEnabled: Boolean = DEFAULT_PROTECTION_ENABLED,
    /** Quantas chamadas passam dentro da janela. A de numero (maxAllowedCalls + 1) e bloqueada. */
    val maxAllowedCalls: Int = DEFAULT_MAX_ALLOWED_CALLS,
    /** Tamanho da janela deslizante, em milissegundos. */
    val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    /**
     * `false` (padrao) = numeros da agenda nunca sao bloqueados.
     * `true` = a regra vale tambem para contatos salvos, o que exige READ_CONTACTS
     * concedida (sem ela o proprio Telecom nao entrega chamadas de contatos ao servico).
     */
    val applyToContacts: Boolean = DEFAULT_APPLY_TO_CONTACTS,
    /**
     * Avisar por notificacao silenciosa quando uma chamada for bloqueada.
     *
     * Sem isto o app age em silencio absoluto e o usuario so descobre abrindo a tela de
     * bloqueios -- o que esconde dele uma informacao que e sua.
     */
    val notifyOnBlock: Boolean = DEFAULT_NOTIFY_ON_BLOCK,
    /**
     * Exigir biometria (ou a senha do aparelho) para abrir o app.
     *
     * Protege o que o app acumula -- quem ligou, quando, e as regras -- de quem pega o
     * celular destravado. Nao criptografa nada: o banco ja esta na area privada do app,
     * inacessivel a outros aplicativos. Prometer criptografia aqui seria vender uma
     * garantia que a chave, guardada no mesmo aparelho, nao sustenta.
     */
    val biometricLockEnabled: Boolean = DEFAULT_BIOMETRIC_LOCK,
) {
    init {
        require(maxAllowedCalls >= 1) { "maxAllowedCalls deve ser >= 1" }
        require(windowMillis > 0L) { "windowMillis deve ser > 0" }
    }

    val windowMinutes: Int get() = TimeUnit.MILLISECONDS.toMinutes(windowMillis).toInt()

    /** A regra geral, na forma que o motor de decisao consome. */
    fun globalPolicy(): CallPolicy =
        CallPolicy(maxAllowedCalls, windowMillis, PolicySource.GLOBAL)

    companion object {
        const val DEFAULT_PROTECTION_ENABLED = true
        const val DEFAULT_MAX_ALLOWED_CALLS = 3
        const val DEFAULT_APPLY_TO_CONTACTS = false
        const val DEFAULT_NOTIFY_ON_BLOCK = true
        const val DEFAULT_BIOMETRIC_LOCK = false
        const val DEFAULT_WINDOW_MINUTES = 15

        val DEFAULT_WINDOW_MILLIS: Long = TimeUnit.MINUTES.toMillis(DEFAULT_WINDOW_MINUTES.toLong())

        /** Opcoes oferecidas na UI. A arquitetura aceita qualquer valor; a UI apenas curadora. */
        val MAX_CALL_OPTIONS: List<Int> = listOf(1, 2, 3, 4, 5)
        val WINDOW_MINUTE_OPTIONS: List<Int> = listOf(5, 10, 15, 30, 60)

        /**
         * Ajusta valores vindos da persistencia para dentro de limites validos, em vez de
         * deixar o construtor lancar excecao com um arquivo corrompido.
         */
        fun sanitized(
            protectionEnabled: Boolean,
            maxAllowedCalls: Int,
            windowMinutes: Int,
            applyToContacts: Boolean,
            notifyOnBlock: Boolean = DEFAULT_NOTIFY_ON_BLOCK,
            biometricLockEnabled: Boolean = DEFAULT_BIOMETRIC_LOCK,
        ): ProtectionSettings = ProtectionSettings(
            protectionEnabled = protectionEnabled,
            maxAllowedCalls = maxAllowedCalls.coerceIn(1, 50),
            windowMillis = TimeUnit.MINUTES.toMillis(windowMinutes.coerceIn(1, 24 * 60).toLong()),
            applyToContacts = applyToContacts,
            notifyOnBlock = notifyOnBlock,
            biometricLockEnabled = biometricLockEnabled,
        )
    }
}
