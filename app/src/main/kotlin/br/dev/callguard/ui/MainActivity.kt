package br.dev.callguard.ui

import android.Manifest
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
import br.dev.callguard.ui.theme.CallGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: CallGuardViewModel = viewModel(
                factory = CallGuardViewModel.factory(application),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            var screen by remember { mutableStateOf(CallGuardScreen.HOME) }

            // O papel e a permissao mudam em telas do sistema, entao o resultado do
            // launcher e o unico momento confiavel para reconsultar o estado.
            val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
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
                        onAddAllowlistEntry = viewModel::addToAllowlist,
                        onRemoveAllowlistEntry = viewModel::removeFromAllowlist,
                        onOpenBlockedCalls = { screen = CallGuardScreen.BLOCKED_CALLS },
                    )

                    CallGuardScreen.BLOCKED_CALLS -> BlockedCallsScreen(
                        blockedCalls = uiState.blockedCalls,
                        onBack = { screen = CallGuardScreen.HOME },
                        onClearHistory = viewModel::clearBlockedCalls,
                    )
                }
            }
        }
    }
}
