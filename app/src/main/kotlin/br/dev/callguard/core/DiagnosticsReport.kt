package br.dev.callguard.core

/**
 * Central de diagnostico: por que o app esta (ou nao esta) protegendo agora.
 *
 * A montagem do laudo e Kotlin puro e vive aqui, separada da coleta dos dados, porque a
 * pergunta "este conjunto de condicoes significa protegido ou nao?" e regra de produto e
 * precisa de teste. A parte que fala com o Android so preenche [DiagnosticsInput].
 */
enum class CheckLevel {
    /** Esta como deveria. */
    OK,

    /** Funciona, mas alguma coisa esta pior do que poderia. */
    ATTENTION,

    /** A protecao NAO esta acontecendo. */
    BLOCKING,
}

/**
 * Uma correcao que a tela sabe executar.
 *
 * Enum, e nao lambda, para que a montagem do laudo continue sem dependencia de Android:
 * a tela traduz cada valor em uma acao.
 */
enum class DiagnosticFix {
    REQUEST_ROLE,
    ENABLE_PROTECTION,
    GRANT_CONTACTS,
    GRANT_NOTIFICATIONS,
    OPEN_APP_SETTINGS,
    OPEN_BATTERY_SETTINGS,
}

data class DiagnosticCheck(
    val title: String,
    val detail: String,
    val level: CheckLevel,
    val fix: DiagnosticFix? = null,
    val fixLabel: String? = null,
)

/** Tudo que a montagem precisa saber, ja coletado do sistema. */
data class DiagnosticsInput(
    val roleAvailable: Boolean,
    val roleHeld: Boolean,
    val protectionEnabled: Boolean,
    val applyToContacts: Boolean,
    val hasContactsPermission: Boolean,
    val notifyOnBlock: Boolean,
    val canPostNotifications: Boolean,
    /** `null` quando o aparelho nao respondeu a consulta. */
    val ignoringBatteryOptimizations: Boolean?,
    /**
     * A permissao INTERNET aparece no pacote instalado?
     *
     * Verificado em tempo de execucao, e nao assumido: e a unica forma de o usuario
     * confirmar no proprio aparelho que a promessa de "nada sai daqui" continua valendo
     * na versao que ele instalou.
     */
    val hasInternetPermission: Boolean,
    val activePolicy: CallPolicy,
    val scheduleActiveNow: Boolean,
    val customRuleCount: Int,
    val blocklistCount: Int,
    val allowlistCount: Int,
)

/** Contagens da base local, para o usuario ver o tamanho do que o app guarda. */
data class StorageStats(
    val attempts: Int,
    val distinctNumbers: Int,
    val allowlist: Int,
    val blocklist: Int,
    val customRules: Int,
    val blockedCalls: Int,
    val screeningEvents: Int,
    val databaseVersion: Int,
)

data class DiagnosticsReport(
    val checks: List<DiagnosticCheck>,
    val storage: StorageStats,
    val activePolicy: CallPolicy,
) {
    val isProtecting: Boolean get() = checks.none { it.level == CheckLevel.BLOCKING }

    val worstLevel: CheckLevel
        get() = when {
            checks.any { it.level == CheckLevel.BLOCKING } -> CheckLevel.BLOCKING
            checks.any { it.level == CheckLevel.ATTENTION } -> CheckLevel.ATTENTION
            else -> CheckLevel.OK
        }
}

object DiagnosticsAssembler {

    /**
     * Ordem proposital: primeiro o que impede a protecao de existir, depois o que a
     * degrada, e so entao o que e informativo. Quem abre esta tela abriu porque algo
     * parece errado -- a resposta tem que estar em cima.
     */
    fun build(input: DiagnosticsInput): List<DiagnosticCheck> = buildList {
        add(papel(input))
        add(interruptor(input))
        add(contatos(input))
        add(notificacoes(input))
        bateria(input)?.let(::add)
        add(internet(input))
        add(regraEmVigor(input))
        add(excecoes(input))
    }

    private fun papel(input: DiagnosticsInput): DiagnosticCheck = when {
        !input.roleAvailable -> DiagnosticCheck(
            title = "Filtro de chamadas",
            detail = "Este aparelho não oferece o papel de filtro de chamadas do Android. " +
                "Sem ele nenhum app consegue rejeitar chamadas antes de tocar.",
            level = CheckLevel.BLOCKING,
        )

        !input.roleHeld -> DiagnosticCheck(
            title = "Filtro de chamadas",
            detail = "O CallGuard não é o app de filtro de chamadas. O Android só entrega " +
                "as ligações para quem tem esse papel, então nada é bloqueado enquanto isso.",
            level = CheckLevel.BLOCKING,
            fix = DiagnosticFix.REQUEST_ROLE,
            fixLabel = "Definir como filtro",
        )

        else -> DiagnosticCheck(
            title = "Filtro de chamadas",
            detail = "O CallGuard é o app de filtro. As ligações passam por ele antes de tocar.",
            level = CheckLevel.OK,
        )
    }

    private fun interruptor(input: DiagnosticsInput): DiagnosticCheck =
        if (input.protectionEnabled) {
            DiagnosticCheck(
                title = "Proteção",
                detail = "Ligada.",
                level = CheckLevel.OK,
            )
        } else {
            DiagnosticCheck(
                title = "Proteção",
                detail = "Desligada. As ligações chegam ao app, mas todas são liberadas.",
                level = CheckLevel.BLOCKING,
                fix = DiagnosticFix.ENABLE_PROTECTION,
                fixLabel = "Ligar proteção",
            )
        }

    private fun contatos(input: DiagnosticsInput): DiagnosticCheck = when {
        input.applyToContacts && !input.hasContactsPermission -> DiagnosticCheck(
            title = "Contatos salvos",
            detail = "Você pediu para aplicar a regra também aos contatos, mas a permissão " +
                "de agenda não está concedida. Sem ela o próprio Android não entrega as " +
                "ligações de contatos ao app — elas passam sem serem contadas.",
            level = CheckLevel.BLOCKING,
            fix = DiagnosticFix.GRANT_CONTACTS,
            fixLabel = "Conceder acesso à agenda",
        )

        input.applyToContacts -> DiagnosticCheck(
            title = "Contatos salvos",
            detail = "A regra vale para contatos salvos e para números desconhecidos.",
            level = CheckLevel.OK,
        )

        else -> DiagnosticCheck(
            title = "Contatos salvos",
            detail = "Contatos nunca são bloqueados. Sem a permissão de agenda, o próprio " +
                "Android nem entrega essas ligações ao app — é o sistema garantindo, " +
                "não o app prometendo.",
            level = CheckLevel.OK,
        )
    }

    private fun notificacoes(input: DiagnosticsInput): DiagnosticCheck = when {
        input.notifyOnBlock && !input.canPostNotifications -> DiagnosticCheck(
            title = "Aviso de bloqueio",
            detail = "Os avisos estão ligados, mas o app não tem permissão para notificar. " +
                "Os bloqueios continuam acontecendo, só que em silêncio.",
            level = CheckLevel.ATTENTION,
            fix = DiagnosticFix.GRANT_NOTIFICATIONS,
            fixLabel = "Permitir notificações",
        )

        input.notifyOnBlock -> DiagnosticCheck(
            title = "Aviso de bloqueio",
            detail = "Você recebe uma notificação silenciosa a cada bloqueio.",
            level = CheckLevel.OK,
        )

        else -> DiagnosticCheck(
            title = "Aviso de bloqueio",
            detail = "Desligado. Os bloqueios só aparecem na aba Bloqueadas.",
            level = CheckLevel.OK,
        )
    }

    /**
     * A economia de bateria nao impede o screening -- quem faz o bind e o sistema, e o
     * servico vive por poucos segundos. Por isso ATTENTION e nao BLOCKING: dizer que
     * esta quebrado seria alarme falso, e omitir seria esconder um fator real em
     * aparelhos Samsung, onde a gestao agressiva de background e conhecida.
     */
    private fun bateria(input: DiagnosticsInput): DiagnosticCheck? = when (input.ignoringBatteryOptimizations) {
        null -> null
        true -> DiagnosticCheck(
            title = "Economia de bateria",
            detail = "O app está fora das restrições de bateria.",
            level = CheckLevel.OK,
        )

        false -> DiagnosticCheck(
            title = "Economia de bateria",
            detail = "O app está sujeito à economia de bateria. O filtro em si não depende " +
                "disso (quem inicia o serviço é o sistema), mas em aparelhos Samsung vale " +
                "liberar o app se você notar bloqueios deixando de acontecer.",
            level = CheckLevel.ATTENTION,
            fix = DiagnosticFix.OPEN_BATTERY_SETTINGS,
            fixLabel = "Abrir ajustes de bateria",
        )
    }

    private fun internet(input: DiagnosticsInput): DiagnosticCheck =
        if (input.hasInternetPermission) {
            DiagnosticCheck(
                title = "Acesso à rede",
                detail = "Esta instalação declara a permissão de internet. Não deveria: " +
                    "o CallGuard não envia nada para lugar nenhum. Desinstale e instale " +
                    "novamente a partir de uma fonte confiável.",
                level = CheckLevel.BLOCKING,
            )
        } else {
            DiagnosticCheck(
                title = "Acesso à rede",
                detail = "Esta instalação não declara a permissão de internet. O app é " +
                    "incapaz de enviar seus números para qualquer lugar — verificado " +
                    "agora, no pacote instalado neste aparelho.",
                level = CheckLevel.OK,
            )
        }

    private fun regraEmVigor(input: DiagnosticsInput): DiagnosticCheck = DiagnosticCheck(
        title = "Regra em vigor agora",
        detail = buildString {
            append(input.activePolicy.source.label)
            append(": ")
            append(input.activePolicy.describe())
            append(". A ligação seguinte dentro da janela é rejeitada.")
            if (input.scheduleActiveNow) append(" O modo noturno está valendo neste momento.")
            if (input.customRuleCount > 0) {
                append(" ${input.customRuleCount} número(s) têm regra própria, que passa na frente desta.")
            }
        },
        level = CheckLevel.OK,
    )

    private fun excecoes(input: DiagnosticsInput): DiagnosticCheck = DiagnosticCheck(
        title = "Exceções",
        detail = "${input.allowlistCount} número(s) nunca bloqueado(s), " +
            "${input.blocklistCount} sempre bloqueado(s).",
        level = CheckLevel.OK,
    )
}

/**
 * Resultado do teste de um numero, feito SEM gravar nada.
 *
 * Esta e a diferenca entre a tela dizer "está tudo certo" e o usuario poder conferir:
 * ele digita o numero que o incomoda e ve, com os dados reais que ja estao no aparelho,
 * qual regra pegaria essa ligacao e o que aconteceria se ela chegasse agora.
 */
data class NumberSimulation(
    val rawInput: String,
    val normalizedNumber: String?,
    val origin: PhoneOrigin,
    val isEmergency: Boolean,
    val isAllowlisted: Boolean,
    val isBlocklisted: Boolean,
    val isSavedContact: Boolean,
    val hasCustomRule: Boolean,
    val appliedPolicy: CallPolicy?,
    val attemptsInWindow: Int,
    val decision: ScreeningDecision,
) {
    /** A frase que a tela mostra em destaque. */
    fun verdict(): String = when (decision) {
        is ScreeningDecision.Block -> "Seria BLOQUEADA agora"
        is ScreeningDecision.Allow -> "Tocaria normalmente agora"
    }

    fun explanation(): String = when (val d = decision) {
        is ScreeningDecision.Block -> when (d.reason) {
            BlockReason.PERMANENT_BLOCKLIST -> "Este número está na lista de bloqueio permanente."
            BlockReason.GLOBAL_LIMIT_EXCEEDED,
            BlockReason.CUSTOM_LIMIT_EXCEEDED,
            BlockReason.SCHEDULE_LIMIT_EXCEEDED,
            -> "Já são $attemptsInWindow tentativas dentro da janela de " +
                "${appliedPolicy?.describe() ?: "—"} (${appliedPolicy?.source?.label})."
        }

        is ScreeningDecision.Allow -> when (d.reason) {
            AllowReason.EMERGENCY_NUMBER -> "Número de emergência. Nunca é bloqueado."
            AllowReason.PROTECTION_DISABLED -> "A proteção está desligada."
            AllowReason.UNSUPPORTED_CALL -> "Número inválido ou não utilizável como chave."
            AllowReason.ALLOWLISTED -> "Está na lista de números sempre permitidos."
            AllowReason.CONTACT_EXEMPT -> "É um contato salvo, e a regra não se aplica a contatos."
            AllowReason.NOT_INCOMING -> "Não é uma chamada recebida."
            else -> "Ainda dentro do limite: $attemptsInWindow de " +
                "${appliedPolicy?.maxAllowedCalls ?: "—"} em ${appliedPolicy?.describe() ?: "—"}."
        }
    }
}
