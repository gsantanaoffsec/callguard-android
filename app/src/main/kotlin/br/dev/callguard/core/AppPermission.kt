package br.dev.callguard.core

/**
 * Catálogo das autorizações que o CallGuard usa, com a divulgação escrita por extenso.
 *
 * Kotlin puro e sem constantes do Android: o texto que explica ao usuário o que será
 * pedido é regra de produto, não detalhe de plataforma, e precisa de teste. O nome de
 * manifesto entra como `String` justamente para que este arquivo não dependa de
 * `android.Manifest`.
 *
 * Um esclarecimento que o app não pode omitir: **nenhum aplicativo concede permissões a
 * si mesmo.** O botão de "conceder tudo" apenas dispara os diálogos oficiais do sistema,
 * em sequência. Quem concede continua sendo a pessoa.
 */
enum class AppPermission {
    /** Não é uma permissão: é o papel de filtro de chamadas, concedido em outra tela. */
    CALL_SCREENING_ROLE,
    READ_CONTACTS,
    POST_NOTIFICATIONS,
    CALL_PHONE,
    BIOMETRIC,
}

/** Situação de uma autorização neste aparelho, agora. */
enum class PermissionStatus {
    GRANTED,
    MISSING,

    /** Não existe nesta versão do Android, ou o aparelho não oferece. */
    NOT_APPLICABLE,
}

/**
 * O que se diz ao usuário antes de pedir.
 *
 * `withoutIt` existe porque uma tela de permissões que só lista benefícios é propaganda.
 * Saber o que se perde ao recusar é o que torna a escolha informada — e quase tudo aqui
 * é recusável sem quebrar o app.
 */
data class PermissionDisclosure(
    val id: AppPermission,
    /** `null` quando não é uma permissão pedida em tempo de execução. */
    val manifestName: String?,
    val title: String,
    val purpose: String,
    val withoutIt: String,
    /** Sem esta, o produto simplesmente não funciona. */
    val essential: Boolean,
    /** Já vem concedida na instalação; nunca há diálogo. */
    val installTime: Boolean = false,
    /** Versão mínima do Android em que ela existe. */
    val minSdk: Int = 1,
)

object PermissionCatalog {

    const val READ_CONTACTS = "android.permission.READ_CONTACTS"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val CALL_PHONE = "android.permission.CALL_PHONE"

    /** API 33. Abaixo disso, notificar não exige permissão. */
    const val SDK_NOTIFICATIONS = 33

    private val TODAS = listOf(
        PermissionDisclosure(
            id = AppPermission.CALL_SCREENING_ROLE,
            manifestName = null,
            title = "Filtro de chamadas",
            purpose = "Deixa o CallGuard analisar cada ligação recebida antes de o telefone " +
                "tocar. É o que torna o bloqueio possível.",
            withoutIt = "Nada é bloqueado. O Android só entrega as chamadas para o app que " +
                "tem esse papel, e ele é concedido em uma tela do próprio sistema.",
            essential = true,
        ),
        PermissionDisclosure(
            id = AppPermission.READ_CONTACTS,
            manifestName = READ_CONTACTS,
            title = "Agenda",
            purpose = "Necessária apenas se você quiser que a regra valha também para " +
                "contatos salvos. Serve para saber se um número está na agenda — nada é " +
                "lido além disso.",
            withoutIt = "Contatos salvos nunca são bloqueados. Sem esta permissão o próprio " +
                "Android não entrega essas chamadas ao app — é o sistema garantindo, não o " +
                "app prometendo.",
            essential = false,
        ),
        PermissionDisclosure(
            id = AppPermission.POST_NOTIFICATIONS,
            manifestName = POST_NOTIFICATIONS,
            title = "Notificações",
            purpose = "Avisar, em silêncio, quando uma chamada for recusada.",
            withoutIt = "Os bloqueios continuam acontecendo, mas em silêncio. Você só " +
                "descobre abrindo a aba Bloqueadas.",
            essential = false,
            minSdk = SDK_NOTIFICATIONS,
        ),
        PermissionDisclosure(
            id = AppPermission.CALL_PHONE,
            manifestName = CALL_PHONE,
            title = "Fazer chamadas",
            purpose = "Usada só na aba Ligar oculto, para iniciar a ligação dentro do app.",
            withoutIt = "A aba continua funcionando: o número vai para o discador do " +
                "sistema, que não exige permissão nenhuma.",
            essential = false,
        ),
        PermissionDisclosure(
            id = AppPermission.BIOMETRIC,
            manifestName = null,
            title = "Biometria",
            purpose = "Exigir sua digital ou a senha do aparelho para abrir o app, se você " +
                "ligar essa opção.",
            withoutIt = "—",
            essential = false,
            installTime = true,
        ),
    )

    /** O que faz sentido mostrar nesta versão do Android. */
    fun disclosures(sdkInt: Int): List<PermissionDisclosure> = TODAS.filter { sdkInt >= it.minSdk }

    /**
     * Nomes de manifesto a pedir de uma vez.
     *
     * O papel de filtro fica de fora porque não é uma permissão: ele tem um `Intent`
     * próprio e precisa ser pedido depois, em sequência. E a biometria também, porque é
     * concedida na instalação e nunca abre diálogo.
     */
    fun runtimePermissionsToRequest(
        statuses: Map<AppPermission, PermissionStatus>,
        sdkInt: Int,
    ): List<String> = disclosures(sdkInt)
        .filter { !it.installTime && it.manifestName != null }
        .filter { statuses[it.id] != PermissionStatus.GRANTED }
        .mapNotNull { it.manifestName }

    /**
     * O que o app **não** pede.
     *
     * Faz parte da divulgação tanto quanto a lista do que ele pede. Uma tela que só
     * enumera o que quer não deixa ninguém avaliar se o pedido é proporcional.
     */
    val neverRequested: List<String> = listOf(
        "Internet — o app não tem acesso à rede. Seus números não podem sair do aparelho.",
        "Registro de chamadas — a API de filtragem já entrega o necessário.",
        "Microfone, câmera, localização e armazenamento.",
        "Estado do telefone, atender ou encerrar chamadas.",
        "Ser o discador padrão do aparelho.",
    )
}
