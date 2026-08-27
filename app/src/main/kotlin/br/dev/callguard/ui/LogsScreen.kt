package br.dev.callguard.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.dev.callguard.core.PhoneOrigin
import br.dev.callguard.data.ScreeningLogRepository
import br.dev.callguard.data.db.ScreeningEventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm:ss", Locale.forLanguageTag("pt-BR"))

/**
 * Aba de logs: mostra onde o arquivo fica, permite abri-lo, e ja exibe o conteudo aqui
 * para o caso comum de so querer conferir o que aconteceu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogsScreen(
    events: List<ScreeningEventEntity>,
    friendlyPath: String,
    statusMessage: String?,
    onGenerateAndOpen: () -> Unit,
    onGenerateAndShare: () -> Unit,
    onRefreshFile: () -> Unit,
    onClear: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var revealNumbers by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                actions = {
                    IconButton(onClick = onRefreshFile) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar arquivo")
                    }
                    IconButton(onClick = onClear, enabled = events.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Limpar registros")
                    }
                },
            )
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
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Arquivo no celular", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "A pasta é esta:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = friendlyPath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "É um arquivo de texto comum, escrito para ser lido por " +
                                "gente. Fica só neste aparelho e guarda os " +
                                "${ScreeningLogRepository.MAX_EVENTS_KEPT} registros mais " +
                                "recentes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onGenerateAndOpen, modifier = Modifier.fillMaxWidth()) {
                            Text("Gerar e abrir o arquivo")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onGenerateAndShare,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Enviar para outro app")
                        }
                        if (statusMessage != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "A operadora do número não aparece aqui. Com a " +
                                "portabilidade numérica o prefixo deixou de indicar a " +
                                "operadora, e descobrir a atual exigiria consulta pela " +
                                "internet — que este app não faz. Melhor não mostrar do que " +
                                "mostrar errado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (events.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Mostrar números completos",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = revealNumbers, onCheckedChange = { revealNumbers = it })
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = "Nenhuma chamada foi analisada ainda.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(events, key = { it.id }) { evento ->
                    LogEntry(evento = evento, revealNumber = revealNumbers)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogEntry(evento: ScreeningEventEntity, revealNumber: Boolean) {
    val origem = PhoneOrigin.of(evento.normalizedNumber)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ScreeningLogRepository.maskIfNeeded(
                        evento.normalizedNumber,
                        revealNumber,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (evento.blocked) "BLOQUEADA" else "permitida",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (evento.blocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = DATE_TIME.format(
                    Instant.ofEpochMilli(evento.occurredAt).atZone(ZoneId.systemDefault()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            InfoLinha("Procedência", origem.describe())
            origem.areaCode?.let { InfoLinha("DDD", it) }
            InfoLinha(
                "Verificação da rede",
                ScreeningLogRepository.describeVerification(evento.verificationStatus),
            )
            InfoLinha("Motivo", ScreeningLogRepository.translateReason(evento.reason))
            if (evento.attemptsInWindow > 0) {
                InfoLinha("Tentativas recentes", evento.attemptsInWindow.toString())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoLinha(rotulo: String, valor: String) {
    FlowRow(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$rotulo: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = valor, style = MaterialTheme.typography.bodySmall)
    }
}
