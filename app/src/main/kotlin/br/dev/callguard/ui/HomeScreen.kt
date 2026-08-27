package br.dev.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.dev.callguard.core.ProtectionSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: CallGuardUiState,
    onRequestRole: () -> Unit,
    onProtectionChange: (Boolean) -> Unit,
    onMaxCallsChange: (Int) -> Unit,
    onWindowMinutesChange: (Int) -> Unit,
    onApplyToContactsChange: (Boolean) -> Unit,
    onNotifyOnBlockChange: (Boolean) -> Unit,
    onAddAllowlistEntry: (rawNumber: String, label: String) -> Boolean,
    onRemoveAllowlistEntry: (String) -> Unit,
    onOpenBlockedCalls: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Proteção contra chamadas insistentes") })
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            item { StatusCard(uiState = uiState, onRequestRole = onRequestRole) }

            item {
                SectionCard(title = "Proteção automática") {
                    SwitchRow(
                        title = "Bloquear chamadas insistentes",
                        subtitle = "Rejeita automaticamente quem passa do limite dentro da janela.",
                        checked = uiState.settings.protectionEnabled,
                        onCheckedChange = onProtectionChange,
                    )
                }
            }

            item {
                SectionCard(title = "Regra") {
                    Text(
                        text = "Número máximo de chamadas permitidas",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Com ${uiState.settings.maxAllowedCalls}, a chamada de número " +
                            "${uiState.settings.maxAllowedCalls + 1} dentro da janela é rejeitada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        options = ProtectionSettings.MAX_CALL_OPTIONS,
                        selected = uiState.settings.maxAllowedCalls,
                        label = { it.toString() },
                        onSelected = onMaxCallsChange,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Intervalo de tempo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Janela deslizante: contam apenas as chamadas dos últimos " +
                            "${uiState.settings.windowMinutes} minutos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        options = ProtectionSettings.WINDOW_MINUTE_OPTIONS,
                        selected = uiState.settings.windowMinutes,
                        label = { if (it >= 60) "${it / 60} h" else "$it min" },
                        onSelected = onWindowMinutesChange,
                    )
                }
            }

            item {
                SectionCard(title = "Contatos") {
                    SwitchRow(
                        title = "Aplicar a regra também aos contatos salvos",
                        subtitle = if (uiState.settings.applyToContacts) {
                            "Precisa da permissão de contatos: sem ela o Android nem entrega " +
                                "chamadas de contatos ao aplicativo."
                        } else {
                            "Desligado, contatos salvos nunca são bloqueados — o próprio " +
                                "Android não envia essas chamadas para o app."
                        },
                        checked = uiState.settings.applyToContacts,
                        onCheckedChange = onApplyToContactsChange,
                    )
                    if (uiState.contactsModeNeedsPermission) {
                        Spacer(Modifier.height(8.dp))
                        WarningLine(
                            "Permissão de contatos não concedida. Enquanto isso, contatos " +
                                "salvos continuam passando.",
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Avisos") {
                    SwitchRow(
                        title = "Avisar quando bloquear",
                        subtitle = "Notificação silenciosa, sem som e sem vibração. Sem ela " +
                            "você só descobre um bloqueio abrindo o app.",
                        checked = uiState.settings.notifyOnBlock,
                        onCheckedChange = onNotifyOnBlockChange,
                    )
                    if (uiState.notificationsNeedPermission) {
                        Spacer(Modifier.height(8.dp))
                        WarningLine(
                            "Permissão de notificações não concedida. Os bloqueios vão " +
                                "acontecer sem aviso.",
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Lista de exceções") {
                    Text(
                        text = "Números que nunca serão bloqueados, não importa quantas vezes " +
                            "liguem. Fica apenas neste aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar número")
                    }
                }
            }

            if (uiState.allowlist.isNotEmpty()) {
                items(uiState.allowlist, key = { it.normalizedNumber }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    entry.normalizedNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRemoveAllowlistEntry(entry.normalizedNumber) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenBlockedCalls,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Chamadas bloqueadas",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${uiState.blockedCallsTotal} no total",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        AddAllowlistDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { number, label ->
                val added = onAddAllowlistEntry(number, label)
                if (added) showAddDialog = false
                added
            },
        )
    }
}

@Composable
private fun StatusCard(uiState: CallGuardUiState, onRequestRole: () -> Unit) {
    val protecting = uiState.isActuallyProtecting
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (protecting) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (protecting) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (protecting) "Proteção ativa" else "Proteção inativa",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                !uiState.roleAvailable -> Text(
                    "Este aparelho não oferece a função de filtragem de chamadas para " +
                        "aplicativos de terceiros.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                !uiState.roleHeld -> {
                    Text(
                        "Para poder recusar chamadas antes que o telefone toque, o Android " +
                            "exige que este aplicativo seja o serviço de identificação e " +
                            "filtragem de chamadas. Essa autorização só pode ser dada por " +
                            "você, na tela do sistema.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRequestRole) {
                        Text("Definir como app de filtragem de chamadas")
                    }
                }

                !uiState.settings.protectionEnabled -> Text(
                    "Autorização concedida, mas o bloqueio de chamadas insistentes está " +
                        "desligado abaixo.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> Text(
                    "A partir da chamada ${uiState.settings.maxAllowedCalls + 1} do mesmo " +
                        "número em ${uiState.settings.windowMinutes} minutos, a ligação é " +
                        "recusada automaticamente.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AddAllowlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (number: String, label: String) -> Boolean,
) {
    var number by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar exceção") },
        text = {
            Column {
                OutlinedTextField(
                    value = number,
                    onValueChange = {
                        number = it
                        showError = false
                    },
                    label = { Text("Telefone") },
                    placeholder = { Text("(11) 99999-9999") },
                    singleLine = true,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nome (opcional)") },
                    placeholder = { Text("Mãe") },
                    singleLine = true,
                )
                if (showError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Número inválido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "O número fica apenas neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (!onConfirm(number, label)) showError = true },
                enabled = number.isNotBlank(),
            ) { Text("Adicionar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
