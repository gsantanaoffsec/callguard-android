package br.dev.callguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telephony.TelephonyManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.dev.callguard.core.CallerIdCodes
import br.dev.callguard.core.DiagnosticFix
import br.dev.callguard.data.ServiceLocator
import br.dev.callguard.screening.BlockedCallNotifier
import br.dev.callguard.ui.theme.CallGuardTheme

/**
 * `FragmentActivity` e nao `ComponentActivity` por uma exigencia concreta:
 * `BiometricPrompt` precisa de um host com `FragmentManager` para sobreviver a
 * recriacao da tela durante a autenticacao. Nao ha Fragment nenhum na interface.
 */
class MainActivity : FragmentActivity() {

    /** Atualizado por `onNewIntent` quando o app ja esta aberto e a notificacao e tocada. */
    private var pendingOpenBlockedCalls by mutableStateOf(false)

    /** Autenticado nesta ida ao primeiro plano. */
    private var desbloqueado by mutableStateOf(false)

    /** Momento em que o app saiu da frente, para a tolerancia curta abaixo. */
    private var saiuEm = 0L

    /** Numero aguardando a resposta do pedido de permissao para ligar. */
    private var numeroPendente: String? = null

    private fun temPermissaoParaLigar(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    private fun isEmergencyNumber(numero: String): Boolean = runCatching {
        getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(numero) ?: false
    }.getOrDefault(false)

    /**
     * Inicia a chamada com a identificacao oculta, sem passar pelo discador.
     *
     * O prefixo nao chega a ser exibido: a telefonia reconhece o codigo de servico, disca
     * o numero real e a tela de chamada do sistema mostra so o numero limpo (o handle da
     * chamada e substituido pelo da conexao).
     *
     * Emergencia nunca passa por aqui -- `ACTION_CALL` recusa esses numeros por
     * documentacao, e a tela ja bloqueia antes.
     */
    private fun placeHiddenCall(numero: String) {
        val discar = CallerIdCodes.buildHiddenCallerIdNumber(numero) ?: return
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(discar)))
        runCatching { startActivity(intent) }.onFailure { openDialerWithHiddenCall(numero) }
    }

    /** Caminho alternativo quando nao ha permissao: abre o discador preenchido. */
    private fun openDialerWithHiddenCall(numero: String) {
        val discar = CallerIdCodes.buildHiddenCallerIdNumber(numero) ?: return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(discar)))
        runCatching { startActivity(intent) }
    }

    /**
     * Tolerancia curta ao voltar.
     *
     * Sem ela, cada ida a uma tela do sistema -- conceder uma permissao, escolher onde
     * salvar o backup, definir o app como filtro -- devolveria o usuario a uma tela de
     * bloqueio. Trinta segundos cobrem esses desvios sem transformar o celular deixado
     * em cima da mesa em porta aberta.
     */
    override fun onStart() {
        super.onStart()
        if (desbloqueado && saiuEm > 0L &&
            SystemClock.elapsedRealtime() - saiuEm > TOLERANCIA_DE_RETORNO_MILLIS
        ) {
            desbloqueado = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (desbloqueado) saiuEm = SystemClock.elapsedRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(BlockedCallNotifier.EXTRA_OPEN_BLOCKED_CALLS, false)) {
            pendingOpenBlockedCalls = true
        }
    }

    /**
     * Dispara um pedido de permissao sem deixar o app morrer se o aparelho recusar.
     *
     * `ActivityResultLauncher.launch` pode lancar -- por registro perdido, por politica
     * do fabricante, por estado de ciclo de vida. Quando isso acontece o certo nao e
     * engolir em silencio nem cair: o rastro vai para o arquivo de falhas e o usuario e
     * levado a tela de permissoes do sistema, onde consegue conceder na mao. O recurso
     * continua alcancavel, e a causa fica registrada.
     */
    private fun pedirPermissao(
        launcher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
        permissao: String,
    ) {
        runCatching { launcher.launch(permissao) }.onFailure { erro ->
            ServiceLocator.crashReporter(this)
                .recordHandled(erro, "pedido da permissao $permissao")
            abrirAjustesDoApp()
        }
    }

    /** Pede a autenticacao e traduz a falha em uma frase que o usuario entenda. */
    private fun pedirDesbloqueio(onOk: () -> Unit, onErro: (String) -> Unit) {
        BiometricSupport.prompt(
            activity = this,
            onSuccess = onOk,
            onFailure = { desistiu ->
                onErro(
                    if (desistiu) {
                        "Autenticação cancelada. Toque em Desbloquear para tentar de novo."
                    } else {
                        "Não foi possível autenticar neste momento. " +
                            "Você também pode usar a senha do aparelho."
                    },
                )
            },
        )
    }

    /** Correcoes do diagnostico que exigem abrir uma tela do sistema. */
    private fun aplicarCorrecaoDoSistema(
        correcao: DiagnosticFix,
        pedirPapel: () -> Unit,
        pedirContatos: () -> Unit,
        pedirNotificacoes: () -> Unit,
    ) {
        when (correcao) {
            DiagnosticFix.REQUEST_ROLE -> pedirPapel()
            DiagnosticFix.GRANT_CONTACTS -> pedirContatos()
            DiagnosticFix.GRANT_NOTIFICATIONS -> pedirNotificacoes()
            DiagnosticFix.OPEN_APP_SETTINGS -> abrirAjustesDoApp()
            // A LISTA de otimizacao, e nao o pedido direto de isencao: este ultimo exige
            // a permissao REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, que o app nao declara
            // porque o filtro nao depende dela para funcionar.
            DiagnosticFix.OPEN_BATTERY_SETTINGS -> abrir(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            DiagnosticFix.ENABLE_PROTECTION -> Unit
        }
    }

    private fun abrirAjustesDoApp() = abrir(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )

    private fun abrir(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Estilo escuro fixo nas duas barras: o app é preto em todas as telas, então
        // deixar o padrão (que segue o tema do sistema) faria as barras clarearem no
        // modo claro do aparelho e brigarem com o conteúdo.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val openBlockedFromNotification =
            intent?.getBooleanExtra(BlockedCallNotifier.EXTRA_OPEN_BLOCKED_CALLS, false) == true

        setContent {
            val viewModel: CallGuardViewModel = viewModel(
                factory = CallGuardViewModel.factory(application),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var mostrandoSplash by remember { mutableStateOf(true) }
            var screen by remember {
                mutableStateOf(
                    if (openBlockedFromNotification) {
                        CallGuardScreen.BLOCKED_CALLS
                    } else {
                        CallGuardScreen.HOME
                    },
                )
            }

            // Toque na notificacao com o app ja aberto.
            androidx.compose.runtime.LaunchedEffect(pendingOpenBlockedCalls) {
                if (pendingOpenBlockedCalls) {
                    screen = CallGuardScreen.BLOCKED_CALLS
                    pendingOpenBlockedCalls = false
                }
            }

            // O papel e a permissao mudam em telas do sistema, entao o resultado do
            // launcher e o unico momento confiavel para reconsultar o estado.
            val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { viewModel.refreshSystemState() }

            val callPermissionLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { concedida ->
                    val numero = numeroPendente
                    if (numero != null) {
                        // Sem a permissao o app nao fica sem saida: cai para o discador,
                        // que nao exige nada. So nesse caminho o codigo aparece na tela.
                        if (concedida) placeHiddenCall(numero) else openDialerWithHiddenCall(numero)
                    }
                }

            // SAF: o proprio usuario escolhe onde o arquivo nasce e de onde ele vem.
            // Nenhuma permissao de armazenamento e necessaria por causa disso.
            val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> if (uri != null) viewModel.exportBackupTo(uri) }

            val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) viewModel.stageImportFrom(uri) }

            // Um pedido em lote: o Android encadeia os dialogos sozinho. Terminada a
            // sequencia, o papel de filtro e pedido em seguida -- ele nao e uma permissao
            // e tem um Intent proprio, entao nao pode entrar no mesmo lote.
            val batchPermissionLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    viewModel.refreshSystemState()
                    if (!viewModel.uiState.value.roleHeld) {
                        val pedido = viewModel.createRoleRequestIntent()
                        if (pedido != null) {
                            runCatching { roleLauncher.launch(pedido) }.onFailure { erro ->
                                ServiceLocator.crashReporter(this@MainActivity)
                                    .recordHandled(erro, "pedido do papel apos o lote")
                                abrirAjustesDoApp()
                            }
                        }
                    }
                }

            val notificationsPermissionLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { viewModel.refreshSystemState() }

            val contactsPermissionLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    viewModel.refreshSystemState()
                    // Sem a permissao o Modo 2 nao funciona; voltamos para o Modo 1 em
                    // vez de deixar o usuario com um ajuste que nao faz nada.
                    if (!granted) viewModel.setApplyToContacts(false)
                }

            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                viewModel.refreshSystemState()
                viewModel.refreshCrashReport()
                onPauseOrDispose { }
            }

            var erroDeBloqueio by remember { mutableStateOf<String?>(null) }
            val disponibilidadeBiometrica = remember { BiometricSupport.availability(this) }
            val precisaDesbloquear = uiState.settings.biometricLockEnabled && !desbloqueado

            CallGuardTheme {
                if (mostrandoSplash) {
                    SplashScreen(onFinished = { mostrandoSplash = false })
                    return@CallGuardTheme
                }

                if (precisaDesbloquear) {
                    androidx.compose.runtime.LaunchedEffect(precisaDesbloquear) {
                        // Falha na disponibilidade LIBERA. Uma tranca cuja chave deixou de
                        // existir no aparelho so trancaria o dono do lado de fora.
                        if (BiometricSupport.availability(this@MainActivity) !=
                            BiometricAvailability.AVAILABLE
                        ) {
                            erroDeBloqueio = null
                            desbloqueado = true
                        } else {
                            pedirDesbloqueio(
                                onOk = { desbloqueado = true; erroDeBloqueio = null },
                                onErro = { erroDeBloqueio = it },
                            )
                        }
                    }
                    LockedScreen(
                        mensagemDeErro = erroDeBloqueio,
                        onUnlock = {
                            pedirDesbloqueio(
                                onOk = { desbloqueado = true; erroDeBloqueio = null },
                                onErro = { erroDeBloqueio = it },
                            )
                        },
                    )
                    return@CallGuardTheme
                }

                uiState.pendingImport?.let { pendente ->
                    br.dev.callguard.ui.design.CgDialog(
                        title = "Substituir suas regras?",
                        description = "O arquivo tem ${pendente.summary()}. Importar " +
                            "SUBSTITUI as listas e ajustes atuais deste aparelho. O " +
                            "histórico de chamadas não é afetado.",
                        onDismiss = viewModel::cancelImport,
                        confirmText = "Substituir",
                        destructive = true,
                        onConfirm = viewModel::confirmImport,
                    )
                }

                val barraDeAbas: @Composable () -> Unit = {
                    CallGuardNavigationBar(
                        currentScreen = screen,
                        onScreenSelected = { screen = it },
                    )
                }

                when (screen) {
                    CallGuardScreen.HOME -> HomeScreen(
                        uiState = uiState,
                        onOpenPermissions = { screen = CallGuardScreen.PERMISSIONS },
                        onProtectionChange = viewModel::setProtectionEnabled,
                        onMaxCallsChange = viewModel::setMaxAllowedCalls,
                        onWindowMinutesChange = viewModel::setWindowMinutes,
                        onApplyToContactsChange = { enabled ->
                            if (enabled && !uiState.hasReadContactsPermission) {
                                // Pedimos READ_CONTACTS somente neste ponto: e o unico
                                // momento em que ela e realmente necessaria.
                                pedirPermissao(
                                    contactsPermissionLauncher,
                                    Manifest.permission.READ_CONTACTS,
                                )
                            }
                            viewModel.setApplyToContacts(enabled)
                        },
                        onNotifyOnBlockChange = { enabled ->
                            // POST_NOTIFICATIONS so existe a partir do Android 13; abaixo
                            // disso basta o usuario nao ter desativado as notificacoes.
                            if (enabled &&
                                !uiState.canPostNotifications &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                pedirPermissao(
                                    notificationsPermissionLauncher,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
                            viewModel.setNotifyOnBlock(enabled)
                        },
                        onAddAllowlistEntry = viewModel::addToAllowlist,
                        onRemoveAllowlistEntry = viewModel::removeFromAllowlist,
                        onOpenBlockedCalls = { screen = CallGuardScreen.BLOCKED_CALLS },
                        onOpenDiagnostics = {
                            screen = CallGuardScreen.DIAGNOSTICS
                            viewModel.refreshDiagnostics()
                        },
                        onBiometricLockChange = viewModel::setBiometricLock,
                        biometricAvailability = disponibilidadeBiometrica,
                        bottomBar = barraDeAbas,
                    )

                    CallGuardScreen.BLOCKED_CALLS -> BlockedCallsScreen(
                        blockedCalls = uiState.blockedCalls,
                        allowlistedNumbers = uiState.allowlistedNumbers,
                        onBack = { screen = CallGuardScreen.HOME },
                        onClearHistory = viewModel::clearBlockedCalls,
                        onAllowlistNumber = viewModel::allowlistBlockedNumber,
                        bottomBar = barraDeAbas,
                    )

                    CallGuardScreen.RULES -> RulesScreen(
                        uiState = uiState,
                        onAddBlocklist = viewModel::addToBlocklist,
                        onRemoveBlocklist = viewModel::removeFromBlocklist,
                        onAddCustomRule = viewModel::addCustomRule,
                        onRemoveCustomRule = viewModel::removeCustomRule,
                        onScheduleChange = { viewModel.setSchedule(it) },
                        bottomBar = barraDeAbas,
                    )

                    CallGuardScreen.ANONYMOUS_CALL -> AnonymousCallScreen(
                        isEmergencyNumber = ::isEmergencyNumber,
                        onPlaceCall = { numero ->
                            numeroPendente = numero
                            if (temPermissaoParaLigar()) {
                                placeHiddenCall(numero)
                            } else {
                                // Pedida so agora, no primeiro uso real do recurso.
                                pedirPermissao(
                                    callPermissionLauncher,
                                    Manifest.permission.CALL_PHONE,
                                )
                            }
                        },
                        bottomBar = barraDeAbas,
                    )

                    CallGuardScreen.DIAGNOSTICS -> DiagnosticsScreen(
                        report = uiState.diagnostics,
                        simulation = uiState.simulation,
                        backupMessage = uiState.backupMessage,
                        onBack = { screen = CallGuardScreen.HOME },
                        onRefresh = viewModel::refreshDiagnostics,
                        onSimulate = viewModel::simulateNumber,
                        onClearSimulation = viewModel::clearSimulation,
                        onFix = { correcao ->
                            // O ViewModel resolve o que e configuracao; o que abre tela do
                            // sistema so a Activity pode fazer.
                            if (!viewModel.applyFix(correcao)) {
                                aplicarCorrecaoDoSistema(
                                    correcao = correcao,
                                    pedirPapel = {
                                        viewModel.createRoleRequestIntent()
                                            ?.let(roleLauncher::launch)
                                    },
                                    pedirContatos = {
                                        pedirPermissao(
                                            contactsPermissionLauncher,
                                            Manifest.permission.READ_CONTACTS,
                                        )
                                    },
                                    pedirNotificacoes = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            pedirPermissao(
                                                notificationsPermissionLauncher,
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        } else {
                                            abrirAjustesDoApp()
                                        }
                                    },
                                )
                            }
                        },
                        onExport = { exportLauncher.launch(viewModel.suggestedBackupFileName()) },
                        onImport = {
                            // "application/json" sozinho deixa arquivos invisiveis em
                            // gerenciadores que rotulam .json como text/plain ou octet-stream.
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "*/*"),
                            )
                        },
                        onClearAttempts = viewModel::clearAttemptHistory,
                        bottomBar = barraDeAbas,
                    )

                    CallGuardScreen.PERMISSIONS -> PermissionsScreen(
                        statuses = uiState.permissionStatuses,
                        sdkInt = Build.VERSION.SDK_INT,
                        onBack = { screen = CallGuardScreen.HOME },
                        onGrantAll = {
                            val pedidos = br.dev.callguard.core.PermissionCatalog
                                .runtimePermissionsToRequest(
                                    statuses = uiState.permissionStatuses,
                                    sdkInt = Build.VERSION.SDK_INT,
                                )
                            if (pedidos.isNotEmpty()) {
                                runCatching {
                                    batchPermissionLauncher.launch(pedidos.toTypedArray())
                                }.onFailure { erro ->
                                    ServiceLocator.crashReporter(this@MainActivity)
                                        .recordHandled(erro, "pedido de permissoes em lote")
                                    abrirAjustesDoApp()
                                }
                            } else {
                                // So falta o papel: vai direto para o dialogo dele.
                                val pedido = viewModel.createRoleRequestIntent()
                                if (pedido != null) {
                                    runCatching { roleLauncher.launch(pedido) }
                                        .onFailure { abrirAjustesDoApp() }
                                }
                            }
                        },
                        bottomBar = barraDeAbas,
                    )

                    CallGuardScreen.LOGS -> LogsScreen(
                        events = uiState.screeningEvents,
                        friendlyPath = uiState.logFilePath,
                        statusMessage = uiState.logStatusMessage,
                        hasCrashReport = uiState.hasCrashReport,
                        crashReportPath = uiState.crashReportPath,
                        onOpenCrashReport = {
                            viewModel.crashReportUri()?.let { uri ->
                                abrir(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "text/plain")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                )
                            }
                        },
                        onShareCrashReport = {
                            viewModel.crashReportUri()?.let { uri ->
                                val enviar = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                abrir(Intent.createChooser(enviar, "Enviar relatório de falha"))
                            }
                        },
                        onClearCrashReport = viewModel::clearCrashReport,
                        onGenerateAndOpen = {
                            viewModel.prepareLogFile { uri ->
                                val abrir = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "text/plain")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                // Nem todo aparelho tem um leitor de texto instalado; nesse
                                // caso o caminho da pasta continua na tela para o usuario
                                // navegar ate la pelo gerenciador de arquivos.
                                runCatching { startActivity(abrir) }.onFailure {
                                    viewModel.setLogStatusMessage(
                                        "Arquivo gerado, mas nenhum aplicativo deste " +
                                            "aparelho abre arquivos de texto. Use " +
                                            "\"Enviar para outro app\" ou abra a pasta " +
                                            "pelo Meus Arquivos.",
                                    )
                                }
                            }
                        },
                        onGenerateAndShare = {
                            viewModel.prepareLogFile { uri ->
                                val enviar = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    startActivity(
                                        Intent.createChooser(enviar, "Enviar registro"),
                                    )
                                }
                            }
                        },
                        onRefreshFile = viewModel::refreshLogFile,
                        onClear = viewModel::clearLogs,
                        bottomBar = barraDeAbas,
                    )
                }
            }
        }
    }

    private companion object {
        /** Janela em que voltar ao app nao pede autenticacao de novo. */
        const val TOLERANCIA_DE_RETORNO_MILLIS = 30_000L
    }
}
