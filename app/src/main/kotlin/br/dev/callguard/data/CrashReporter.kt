package br.dev.callguard.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Grava o rastro de uma falha num arquivo que o dono do aparelho consegue abrir.
 *
 * Existe porque este app nao tem rede: nao ha Crashlytics, nao ha telemetria, e nenhum
 * relatorio sai daqui sozinho. Sem isto, uma falha que so acontece no aparelho de quem
 * usa e invisivel para quem mantem o codigo -- resta o dialogo generico do sistema
 * dizendo "este app tem um bug", que nao diz qual.
 *
 * O arquivo fica na mesma pasta visivel do registro de chamadas, e sai do aparelho
 * apenas se a pessoa decidir compartilha-lo.
 *
 * **Nao contem numero de telefone.** O rastro e composto por nomes de classe, metodo e
 * linha; nada do dominio do app entra aqui.
 */
class CrashReporter(context: Context) {

    private val appContext = context.applicationContext

    fun file(): File = File(
        File(appContext.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() },
        FILE_NAME,
    )

    fun hasReport(): Boolean = runCatching { file().length() > 0L }.getOrDefault(false)

    fun friendlyPath(): String = "Android/data/${appContext.packageName}/files/$DIR_NAME/$FILE_NAME"

    fun clear() {
        runCatching { file().delete() }
    }

    /**
     * Instala o registrador como ultimo recurso do processo.
     *
     * O manipulador anterior continua sendo chamado no fim: engolir a excecao deixaria o
     * processo num estado indefinido em vez de encerra-lo, o que e pior do que a falha.
     */
    fun install() {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            runCatching { write(erro, "falha nao tratada em ${thread.name}") }
            anterior?.uncaughtException(thread, erro)
        }
    }

    /**
     * Registra um problema que foi contornado.
     *
     * Usado onde o app se recupera mas o motivo interessa -- um `launch` de permissao que
     * o aparelho recusa, por exemplo. Sem isto, o contorno esconderia a causa.
     */
    fun recordHandled(erro: Throwable, contexto: String) {
        runCatching { write(erro, "contornado: $contexto") }
    }

    private fun write(erro: Throwable, cabecalho: String) {
        val destino = file()

        // Mantem so o mais recente: um arquivo que cresce sem limite acaba nao sendo
        // lido por ninguem, e a falha que interessa e quase sempre a ultima.
        val anterior = if (destino.exists()) destino.readText().takeLast(MAX_CHARS_KEPT) else ""

        val pilha = StringWriter().also { erro.printStackTrace(PrintWriter(it)) }.toString()
        val quando = CARIMBO.format(Instant.now().atZone(ZoneId.systemDefault()))

        destino.writeText(
            buildString {
                append(anterior)
                if (anterior.isNotEmpty()) append("\n\n")
                append("=".repeat(64)).append('\n')
                append("CallGuard — $cabecalho\n")
                append("quando: $quando\n")
                append("aparelho: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
                append("versao do app: ${versionName()}\n")
                append("=".repeat(64)).append('\n')
                append(pilha)
            },
        )
    }

    private fun versionName(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "desconhecida"

    companion object {
        private const val DIR_NAME = "logs"
        private const val FILE_NAME = "callguard-falhas.txt"
        private const val MAX_CHARS_KEPT = 40_000

        private val CARIMBO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))
    }
}
