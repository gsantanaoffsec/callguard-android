package br.dev.callguard.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.dev.callguard.screening.BlockedCallNotifier
import br.dev.callguard.ui.theme.CallGuardTheme

class MainActivity : ComponentActivity() {

    /** Atualizado por `onNewIntent` quando o app ja esta aberto e a notificacao e tocada. */
    private var pendingOpenBlockedCalls by mutableStateOf(false)

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
                    )

                    CallGuardScreen.BLOCKED_CALLS -> BlockedCallsScreen(
                        blockedCalls = uiState.blockedCalls,
                        allowlistedNumbers = uiState.allowlistedNumbers,
                        onBack = { screen = CallGuardScreen.HOME },
                        onClearHistory = viewModel::clearBlockedCalls,
                        onAllowlistNumber = viewModel::allowlistBlockedNumber,
                    )
                }
            }
        }
    }
}
