package br.dev.callguard.data

import android.content.Context
import br.dev.callguard.core.PhoneNumberMasker
import br.dev.callguard.core.PhoneOrigin
import br.dev.callguard.data.db.ScreeningEventDao
import br.dev.callguard.data.db.ScreeningEventEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Registro legivel do que o app decidiu, e o arquivo que o usuario abre no celular.
 *
 * O arquivo fica em `getExternalFilesDir("logs")`, ou seja
 * `Android/data/br.dev.callguard/files/logs/`. Essa pasta e visivel para o usuario nos
 * gerenciadores de arquivos e **nao exige nenhuma permissao de armazenamento**; a partir
 * do Android 11 ela tambem nao e legivel por outros aplicativos.
 *
 * O arquivo e gerado sob demanda, e nao a cada chamada: escrever em disco no caminho do
 * screening seria trabalho desnecessario dentro de um fluxo que tem orcamento de tempo.
 */
class ScreeningLogRepository(
    context: Context,
    private val dao: ScreeningEventDao,
) {

    private val appContext = context.applicationContext

    fun observeEvents(): Flow<List<ScreeningEventEntity>> = dao.observeAll()

    suspend fun record(
        occurredAt: Long,
        normalizedNumber: String?,
        blocked: Boolean,
        reason: String,
        attemptsInWindow: Int,
        verificationStatus: Int?,
    ) = dao.record(
        event = ScreeningEventEntity(
            occurredAt = occurredAt,
            normalizedNumber = normalizedNumber,
            blocked = blocked,
            reason = reason,
            attemptsInWindow = attemptsInWindow,
            verificationStatus = verificationStatus,
        ),
        keep = MAX_EVENTS_KEPT,
    )

    suspend fun clear() = dao.deleteAll()

    /** Pasta mostrada na tela, para quem preferir navegar ate ela na mao. */
    fun logDirectory(): File = File(appContext.getExternalFilesDir(null), LOG_DIR_NAME)

    fun logFile(): File = File(logDirectory(), LOG_FILE_NAME)

    /**
     * Caminho amigavel. O prefixo real do armazenamento varia entre aparelhos, entao
     * mostramos a forma que o usuario encontra no "Meus Arquivos" da Samsung.
     */
    fun friendlyLogPath(): String =
        "Android/data/${appContext.packageName}/files/$LOG_DIR_NAME/$LOG_FILE_NAME"

    /** Regenera o arquivo do zero e devolve o `File`. */
    suspend fun writeLogFile(): File {
        val eventos = dao.recent(MAX_EVENTS_KEPT)
        val destino = logFile()
        destino.parentFile?.mkdirs()
        destino.writeText(render(eventos))
        return destino
    }

    private fun render(eventos: List<ScreeningEventEntity>): String = buildString {
        appendLine("CallGuard — registro de chamadas")
        appendLine("=================================")
        appendLine()
        appendLine("Gerado em: ${DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault()))}")
        appendLine("Total de registros: ${eventos.size}")
        appendLine()
        appendLine("Este arquivo fica somente neste aparelho. Nada é enviado para lugar nenhum.")
        appendLine("Ele guarda os $MAX_EVENTS_KEPT registros mais recentes; os antigos saem sozinhos.")
        appendLine()
        appendLine("Sobre a operadora: não é possível saber de qual operadora o número é.")
        appendLine("Com a portabilidade numérica, o prefixo deixou de indicar a operadora, e")
        appendLine("descobrir a atual exigiria consulta pela internet. Este app não acessa a rede,")
        appendLine("então a operadora não aparece aqui — em vez de aparecer errada.")
        appendLine()
        appendLine("---------------------------------")
        appendLine()

        if (eventos.isEmpty()) {
            appendLine("Nenhuma chamada foi analisada ainda.")
            return@buildString
        }

        eventos.forEach { evento ->
            val quando = DATE_TIME.format(
                Instant.ofEpochMilli(evento.occurredAt).atZone(ZoneId.systemDefault()),
            )
            val numero = evento.normalizedNumber ?: "(número não informado pela operadora)"
            val origem = PhoneOrigin.of(evento.normalizedNumber)

            appendLine(quando)
            appendLine("  Número....: $numero")
            appendLine("  Procedência: ${origem.describe()}")
            origem.areaCode?.let { appendLine("  DDD.......: $it") }
            appendLine("  Verificação da rede: ${describeVerification(evento.verificationStatus)}")
            appendLine("  Resultado.: ${if (evento.blocked) "BLOQUEADA" else "permitida"}")
            appendLine("  Motivo....: ${translateReason(evento.reason)}")
            if (evento.attemptsInWindow > 0) {
                appendLine("  Tentativas recentes: ${evento.attemptsInWindow}")
            }
            appendLine()
        }
    }

    companion object {
        private const val LOG_DIR_NAME = "logs"
        private const val LOG_FILE_NAME = "callguard-registro.txt"

        /** Um app que lida com telefones nao deve acumular telefones indefinidamente. */
        const val MAX_EVENTS_KEPT = 300

        private val DATE_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))

        /** Textos em portugues, para o arquivo nao parecer log de sistema. */
        fun translateReason(reason: String): String = when (reason) {
            "UNDER_GLOBAL_LIMIT" -> "Dentro do limite da regra geral"
            "UNDER_CUSTOM_LIMIT" -> "Dentro do limite da regra do número"
            "UNDER_SCHEDULE_LIMIT" -> "Dentro do limite do modo noturno"
            "GLOBAL_LIMIT_EXCEEDED" -> "Limite da regra geral excedido"
            "CUSTOM_LIMIT_EXCEEDED" -> "Limite da regra do número excedido"
            "SCHEDULE_LIMIT_EXCEEDED" -> "Limite do modo noturno excedido"
            "PERMANENT_BLOCKLIST" -> "Bloqueado permanentemente pelo usuário"
            "BLOCKED_PATTERN" -> "Caiu numa faixa de números bloqueada"
            "PROTECTION_DISABLED" -> "Proteção desligada"
            "ALLOWLISTED" -> "Número na lista de permitidos"
            "CONTACT_EXEMPT" -> "Contato salvo na agenda"
            "EMERGENCY_NUMBER" -> "Número de emergência"
            "UNSUPPORTED_CALL" -> "Número não informado pela operadora"
            "NOT_INCOMING" -> "Não é chamada recebida"
            "TIMEOUT_FAILSAFE" -> "Decisão demorou demais; chamada permitida por segurança"
            "ERROR_FAILSAFE" -> "Erro ao decidir; chamada permitida por segurança"
            else -> reason
        }

        /** `Connection.VERIFICATION_STATUS_*`, sem depender do framework aqui. */
        fun describeVerification(status: Int?): String = when (status) {
            null -> "não disponível neste Android"
            1 -> "aprovada — a rede confirma que o número é legítimo"
            2 -> "REPROVADA — o número pode estar falsificado"
            else -> "a rede não conseguiu verificar"
        }

        fun maskIfNeeded(number: String?, reveal: Boolean): String = when {
            number == null -> "(número não informado)"
            reveal -> number
            else -> PhoneNumberMasker.mask(number)
        }
    }
}
