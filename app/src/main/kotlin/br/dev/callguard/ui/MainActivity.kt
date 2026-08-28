package br.dev.callguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.TelephonyManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import br.dev.callguard.core.CallerIdCodes
import br.dev.callguard.screening.BlockedCallNotifier
import br.dev.callguard.ui.theme.CallGuardTheme

class MainActivity : ComponentActivity() {

    /** Atualizado por `onNewIntent` quando o app ja esta aberto e a notificacao e tocada. */
    private var pendingOpenBlockedCalls by mutableStateOf(false)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(BlockedCallNotifier.EXTRA_OPEN_BLOCKED_CALLS, false)) {
            pendingOpenBlockedCalls = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openBlockedFromNotification =
            intent?.getBooleanExtra(BlockedCallNotifier.EXTRA_OPEN_BLOCKED_CALLS, false) == true

        setContent {
            val viewModel: CallGuardViewModel = viewModel(
                factory = CallGuardViewModel.factory(application),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                onPauseOrDispose { }
            }

            CallGuardTheme {
                val barraDeAbas: @Composable () -> Unit = {
                    CallGuardNavigationBar(
                        currentScreen = screen,
                        onScreenSelected = { screen = it },
                    )
                }

                when (screen) {
                    CallGuardScreen.HOME -> HomeScreen(
                        uiState = uiState,
                        onRequestRole = {
                            viewModel.createRoleRequestIntent()?.let(roleLauncher::launch)
                        },
                        onProtectionChange = viewModel::setProtectionEnabled,
                        onMaxCallsChange = viewModel::setMaxAllowedCalls,
                        onWindowMinutesChange = viewModel::setWindowMinutes,
                        onApplyToContactsChange = { enabled ->
                            if (enabled && !uiState.hasReadContactsPermission) {
                                // Pedimos READ_CONTACTS somente neste ponto: e o unico
                                // momento em que ela e realmente necessaria.
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
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
                                notificationsPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
                            viewModel.setNotifyOnBlock(enabled)
                        },
                        onAddAllowlistEntry = viewModel::addToAllowlist,
                        onRemoveAllowlistEntry = viewModel::removeFromAllowlist,
                        onOpenBlockedCalls = { screen = CallGuardScreen.BLOCKED_CALLS },
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

                    CallGuardScreen.ANONYMOUS_CALL -> AnonymousCallScreen(
                        isEmergencyNumber = ::isEmergencyNumber,
                        onPlaceCall = { numero ->
                            numeroPendente = numero
                            if (temPermissaoParaLigar()) {
                                placeHiddenCall(numero)
                            } else {
                                // Pedida so agora, no primeiro uso real do recurso.
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        },
                        bottomBar = barraDeAbas,
                    )

                    CallGuardScreen.LOGS -> LogsScreen(
                        events = uiState.screeningEvents,
                        friendlyPath = uiState.logFilePath,
                        statusMessage = uiState.logStatusMessage,
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
}
