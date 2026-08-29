package br.dev.callguard.ui

import br.dev.callguard.core.AppPermission
import br.dev.callguard.core.BackupPayload
import br.dev.callguard.core.PermissionStatus
import br.dev.callguard.core.DiagnosticsReport
import br.dev.callguard.core.NumberSimulation
import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.data.db.AllowlistEntryEntity
import br.dev.callguard.core.SchedulePolicy
import br.dev.callguard.data.db.BlockedCallEntity
import br.dev.callguard.data.db.BlocklistEntryEntity
import br.dev.callguard.data.db.CustomRuleEntity
import br.dev.callguard.data.db.ScreeningEventEntity

/** Estado unico da tela principal. */
data class CallGuardUiState(
    val settings: ProtectionSettings = ProtectionSettings(),
    val roleAvailable: Boolean = true,
    val roleHeld: Boolean = false,
    val hasReadContactsPermission: Boolean = false,
    val canPostNotifications: Boolean = false,
    val allowlist: List<AllowlistEntryEntity> = emptyList(),
    val blocklist: List<BlocklistEntryEntity> = emptyList(),
    val customRules: List<CustomRuleEntity> = emptyList(),
    val schedule: SchedulePolicy = SchedulePolicy(),
    val blockedCalls: List<BlockedCallEntity> = emptyList(),
    val blockedCallsTotal: Int = 0,
    val screeningEvents: List<ScreeningEventEntity> = emptyList(),
    val logFilePath: String = "",
    /** Mensagem curta apos gerar/abrir o arquivo. Some na proxima acao. */
    val logStatusMessage: String? = null,
    /**
     * Existe um relatorio de falha guardado?
     *
     * Aparece na aba de registro quando verdadeiro. Sem rede, esta e a unica forma de um
     * problema no aparelho de quem usa chegar a quem mantem o codigo.
     */
    /** Situacao de cada autorizacao, para a tela de permissoes e o botao de conceder. */
    val permissionStatuses: Map<AppPermission, PermissionStatus> = emptyMap(),
    val hasCrashReport: Boolean = false,
    /** Caminho legivel do arquivo de falhas, mostrado ao lado do aviso. */
    val crashReportPath: String = "",
    /** Laudo da central de diagnostico. `null` enquanto ainda esta sendo montado. */
    val diagnostics: DiagnosticsReport? = null,
    /** Resultado do ultimo numero testado na central de diagnostico. */
    val simulation: NumberSimulation? = null,
    /** Mensagem do fluxo de backup (exportou, falhou, importou). */
    val backupMessage: String? = null,
    /**
     * Backup lido e validado, esperando a confirmacao do usuario.
     *
     * A importacao substitui as regras; ela nao pode acontecer no mesmo toque que abriu
     * o arquivo. Este campo e o que segura o processo entre "li o arquivo" e "aplique".
     */
    val pendingImport: BackupPayload? = null,
) {
    /** A regra so tem efeito real quando o papel foi concedido e a protecao esta ligada. */
    val isActuallyProtecting: Boolean get() = roleHeld && settings.protectionEnabled

    /** Modo 2 pedido pelo usuario mas ainda sem a permissao que o torna possivel. */
    val contactsModeNeedsPermission: Boolean
        get() = settings.applyToContacts && !hasReadContactsPermission

    /** Avisos ligados, mas sem permissao para notificar: os bloqueios passariam despercebidos. */
    val notificationsNeedPermission: Boolean
        get() = settings.notifyOnBlock && !canPostNotifications

    /**
     * Quantas autorizacoes ainda faltam.
     *
     * Mostrado na tela inicial para que a pendencia seja visivel sem precisar abrir a
     * tela de permissoes -- do contrario, uma permissao recusada some da vista.
     */
    val pendingPermissions: Int
        get() = permissionStatuses.count { it.value == PermissionStatus.MISSING }

    /** Numeros ja liberados, para a tela de bloqueios saber o que ja foi tratado. */
    val allowlistedNumbers: Set<String>
        get() = allowlist.map { it.normalizedNumber }.toSet()

    val blocklistedNumbers: Set<String>
        get() = blocklist.map { it.normalizedNumber }.toSet()

    val numbersWithCustomRule: Set<String>
        get() = customRules.map { it.normalizedNumber }.toSet()
}

/**
 * Abas do app.
 *
 * Um `when` sobre este enum basta; tres telas nao justificam um grafo de navegacao.
 */
enum class CallGuardScreen(
    val label: String,
    val inNavBar: Boolean = true,
    /**
     * Nivel de profundidade na navegacao.
     *
     * Decide a DIRECAO da transicao: entrar numa tela mais funda desliza da direita,
     * voltar desliza da esquerda, e trocar de aba (mesmo nivel) faz uma fusao vertical
     * curta. Sem isso toda transicao seria igual, e o movimento deixaria de dizer se
     * voce avancou ou voltou.
     */
    val depth: Int = 0,
) {
    HOME("Proteção"),
    BLOCKED_CALLS("Bloqueadas"),
    RULES("Regras"),
    ANONYMOUS_CALL("Ligar oculto"),
    LOGS("Logs"),

    /**
     * Fora da barra de abas de proposito.
     *
     * Cinco abas ja e o limite do que uma barra inferior comporta sem virar sopa de
     * icones. O diagnostico e uma tela que se procura quando algo parece errado, nao um
     * lugar onde se fica -- entao ele mora atras de um cartao na tela de Proteção.
     */
    DIAGNOSTICS("Diagnóstico", inNavBar = false, depth = 1),

    /** Também fora da barra: é uma tela de configuração inicial, não um destino diário. */
    PERMISSIONS("Permissões", inNavBar = false, depth = 1),
}
