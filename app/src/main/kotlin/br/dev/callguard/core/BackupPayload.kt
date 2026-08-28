package br.dev.callguard.core

/**
 * Retrato completo da CONFIGURACAO do app, na forma que sai e entra pelo arquivo.
 *
 * Deliberadamente nao inclui historico: nem tentativas, nem chamadas bloqueadas, nem o
 * registro de decisoes. Um backup existe para reconstruir as regras em outro aparelho --
 * levar junto quem ligou para o usuario e quando seria transformar um recurso de
 * conveniencia em vazamento, e ninguem pediu isso.
 *
 * Kotlin puro: o formato do arquivo e regra de dominio, nao detalhe do Android, e
 * precisa ser testavel sem aparelho.
 */
data class BackupPayload(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAtMillis: Long,
    val appVersionName: String,
    val settings: ProtectionSettings,
    val schedule: SchedulePolicy,
    val allowlist: List<BackupNumber> = emptyList(),
    val blocklist: List<BackupNumber> = emptyList(),
    val customRules: List<BackupRule> = emptyList(),
) {
    /** Resumo mostrado ao usuario ANTES de ele confirmar a substituicao. */
    fun summary(): String = buildString {
        append("${allowlist.size} permitido(s), ")
        append("${blocklist.size} bloqueado(s), ")
        append("${customRules.size} regra(s) de número")
        if (schedule.enabled) append(", modo noturno ligado")
    }

    companion object {
        /**
         * Versao do FORMATO, nao do app.
         *
         * Sobe apenas quando a estrutura muda de um jeito que um leitor antigo nao
         * entenderia. A leitura aceita versoes menores ou iguais e recusa maiores --
         * abrir um arquivo do futuro adivinhando o que mudou e como se perde dado.
         */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** Uma entrada de lista (permitidos ou bloqueados). */
data class BackupNumber(
    val normalizedNumber: String,
    val label: String,
)

/** Uma regra propria de numero. */
data class BackupRule(
    val normalizedNumber: String,
    val label: String,
    val maxAllowedCalls: Int,
    val windowMillis: Long,
    val enabled: Boolean,
)

/** Por que um arquivo foi recusado. Cada caso vira uma frase especifica na tela. */
enum class BackupError(val message: String) {
    NOT_JSON("O arquivo não é um backup do CallGuard (não é um JSON válido)."),
    WRONG_APP("O arquivo é um JSON, mas não foi gerado pelo CallGuard."),
    FUTURE_VERSION(
        "O arquivo foi gerado por uma versão mais nova do app. " +
            "Atualize o CallGuard antes de importar.",
    ),
    CORRUPTED("O arquivo está incompleto ou corrompido."),
    EMPTY("O arquivo é um backup válido, mas não tem nenhuma regra dentro."),
}
