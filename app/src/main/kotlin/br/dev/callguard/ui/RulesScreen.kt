package br.dev.callguard.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import br.dev.callguard.core.CallPolicy
import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.core.SchedulePolicy
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit

private val DIAS = listOf(
    DayOfWeek.MONDAY to "seg", DayOfWeek.TUESDAY to "ter", DayOfWeek.WEDNESDAY to "qua",
    DayOfWeek.THURSDAY to "qui", DayOfWeek.FRIDAY to "sex", DayOfWeek.SATURDAY to "sáb",
    DayOfWeek.SUNDAY to "dom",
)

/** Resultado da tentativa de criar uma exceção, para a tela mostrar o conflito. */
enum class RuleConflict { NONE, IN_ALLOWLIST, IN_BLOCKLIST, INVALID_NUMBER }

/**
 * Regras que fogem da configuração geral: bloqueio permanente, limite por número e o
 * período noturno.
 *
 * A tela existe para tornar a hierarquia visível — quem lê de cima para baixo enxerga a
 * mesma ordem de precedência que o motor aplica.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    uiState: CallGuardUiState,
    onAddBlocklist: (raw: String, label: String, force: Boolean) -> RuleConflict,
    onRemoveBlocklist: (String) -> Unit,
    onAddCustomRule: (raw: String, label: String, max: Int, windowMinutes: Int, force: Boolean) -> RuleConflict,
    onRemoveCustomRule: (String) -> Unit,
    onScheduleChange: (SchedulePolicy) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var dialogoBloqueio by remember { mutableStateOf(false) }
    var dialogoRegra by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Regras") }) },
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Ordem das regras", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Emergência → Lista de permitidos → Bloqueio permanente → " +
                                "Contatos → Regra do número → Modo noturno → Regra geral.\n\n" +
                                "A primeira que se aplicar decide. Emergência nunca é bloqueada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ------------------------------------------------------------ blocklist
            item {
                SecaoRegras(
                    titulo = "Bloqueio permanente",
                    descricao = "Chamadas destes números são sempre recusadas, " +
                        "independentemente de quantas vezes ligarem.",
                    acao = "Bloquear um número",
                    onAcao = { dialogoBloqueio = true },
                )
            }
            items(uiState.blocklist, key = { "b-${it.normalizedNumber}" }) { item ->
                LinhaRegra(
                    titulo = item.label,
                    subtitulo = item.normalizedNumber,
                    detalhe = "Sempre bloqueado",
                    onRemover = { onRemoveBlocklist(item.normalizedNumber) },
                )
            }

            // -------------------------------------------------------- regras do número
            item {
                SecaoRegras(
                    titulo = "Regra por número",
                    descricao = "Limite próprio para um número, mais forte que a regra geral " +
                        "e que o modo noturno.",
                    acao = "Criar regra",
                    onAcao = { dialogoRegra = true },
                )
            }
            items(uiState.customRules, key = { "r-${it.normalizedNumber}" }) { regra ->
                val politica = CallPolicy(
                    regra.maxAllowedCalls,
                    regra.windowMillis,
                    br.dev.callguard.core.PolicySource.CUSTOM,
                )
                LinhaRegra(
                    titulo = regra.label,
                    subtitulo = regra.normalizedNumber,
                    detalhe = if (regra.enabled) politica.describe() else "Desativada",
                    onRemover = { onRemoveCustomRule(regra.normalizedNumber) },
                )
            }

            // --------------------------------------------------------- modo noturno
            item { BlocoModoNoturno(uiState.schedule, onScheduleChange) }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (dialogoBloqueio) {
        DialogoNumero(
            titulo = "Bloquear sempre",
            explicacao = "Chamadas deste número serão recusadas automaticamente enquanto " +
                "ele estiver aqui. Você pode remover a qualquer momento.",
            onDismiss = { dialogoBloqueio = false },
            onConfirmar = { numero, nome, forcar -> onAddBlocklist(numero, nome, forcar) },
            onSucesso = { dialogoBloqueio = false },
        )
    }

    if (dialogoRegra) {
        DialogoRegraPersonalizada(
            onDismiss = { dialogoRegra = false },
            onConfirmar = onAddCustomRule,
            onSucesso = { dialogoRegra = false },
        )
    }
}

@Composable
private fun SecaoRegras(
    titulo: String,
    descricao: String,
    acao: String,
    onAcao: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                descricao,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAcao, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(acao)
            }
        }
    }
}

@Composable
private fun LinhaRegra(
    titulo: String,
    subtitulo: String,
    detalhe: String,
    onRemover: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    detalhe,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemover) {
                Icon(Icons.Default.Delete, contentDescription = "Remover")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BlocoModoNoturno(schedule: SchedulePolicy, onChange: (SchedulePolicy) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo noturno", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Regra mais rígida dentro de um horário. Segue o relógio do aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onChange(schedule.copy(enabled = it)) },
                )
            }

            if (!schedule.enabled) return@Column

            Spacer(Modifier.height(16.dp))
            Text("Começa às", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            SeletorHora(schedule.startMinuteOfDay) { onChange(schedule.copy(startMinuteOfDay = it)) }

            Spacer(Modifier.height(12.dp))
            Text("Termina às", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            SeletorHora(schedule.endMinuteOfDay) { onChange(schedule.copy(endMinuteOfDay = it)) }

            if (schedule.startMinuteOfDay > schedule.endMinuteOfDay) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Atravessa a meia-noite. O dia escolhido é o do início do período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Dias", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DIAS.forEach { (dia, rotulo) ->
                    FilterChip(
                        selected = dia in schedule.activeDays,
                        onClick = {
                            val novos = if (dia in schedule.activeDays) {
                                schedule.activeDays - dia
                            } else {
                                schedule.activeDays + dia
                            }
                            if (novos.isNotEmpty()) onChange(schedule.copy(activeDays = novos))
                        },
                        label = { Text(rotulo, maxLines = 1) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Máximo de chamadas no período",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectionSettings.MAX_CALL_OPTIONS.forEach { n ->
                    FilterChip(
                        selected = n == schedule.maxAllowedCalls,
                        onClick = { onChange(schedule.copy(maxAllowedCalls = n)) },
                        label = { Text("$n", maxLines = 1) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Intervalo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectionSettings.WINDOW_MINUTE_OPTIONS.forEach { min ->
                    FilterChip(
                        selected = TimeUnit.MILLISECONDS.toMinutes(schedule.windowMillis).toInt() == min,
                        onClick = {
                            onChange(schedule.copy(windowMillis = TimeUnit.MINUTES.toMillis(min.toLong())))
                        },
                        label = { Text(if (min >= 60) "${min / 60} h" else "$min min", maxLines = 1) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SeletorHora(minutoDoDia: Int, onChange: (Int) -> Unit) {
    val horaAtual = minutoDoDia / 60
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..23).forEach { h ->
            FilterChip(
                selected = h == horaAtual,
                onClick = { onChange(h * 60) },
                label = { Text("%02d".format(h), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun DialogoNumero(
    titulo: String,
    explicacao: String,
    onDismiss: () -> Unit,
    onConfirmar: (numero: String, nome: String, forcar: Boolean) -> RuleConflict,
    onSucesso: () -> Unit,
) {
    var numero by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var conflito by remember { mutableStateOf(RuleConflict.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                Text(explicacao, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                CampoNumero(numero, { numero = it; conflito = RuleConflict.NONE }, nome) { nome = it }
                AvisoConflito(conflito)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val forcar = conflito != RuleConflict.NONE
                    conflito = onConfirmar(numero, nome, forcar)
                    if (conflito == RuleConflict.NONE) onSucesso()
                },
                enabled = numero.isNotBlank(),
            ) {
                Text(if (conflito == RuleConflict.IN_ALLOWLIST) "Remover e bloquear" else "Confirmar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DialogoRegraPersonalizada(
    onDismiss: () -> Unit,
    onConfirmar: (raw: String, label: String, max: Int, windowMinutes: Int, force: Boolean) -> RuleConflict,
    onSucesso: () -> Unit,
) {
    var numero by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var maximo by remember { mutableStateOf(1) }
    var janela by remember { mutableStateOf(10) }
    var conflito by remember { mutableStateOf(RuleConflict.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Regra por número") },
        text = {
            Column {
                CampoNumero(numero, { numero = it; conflito = RuleConflict.NONE }, nome) { nome = it }
                Spacer(Modifier.height(12.dp))
                Text("Máximo de chamadas", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProtectionSettings.MAX_CALL_OPTIONS.forEach { n ->
                        FilterChip(
                            selected = n == maximo,
                            onClick = { maximo = n },
                            label = { Text("$n", maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Intervalo", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProtectionSettings.WINDOW_MINUTE_OPTIONS.forEach { m ->
                        FilterChip(
                            selected = m == janela,
                            onClick = { janela = m },
                            label = { Text(if (m >= 60) "${m / 60}h" else "${m}m", maxLines = 1) },
                        )
                    }
                }
                AvisoConflito(conflito)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val forcar = conflito != RuleConflict.NONE
                    conflito = onConfirmar(numero, nome, maximo, janela, forcar)
                    if (conflito == RuleConflict.NONE) onSucesso()
                },
                enabled = numero.isNotBlank(),
            ) {
                Text(if (conflito == RuleConflict.NONE) "Criar" else "Remover e criar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CampoNumero(
    numero: String,
    onNumero: (String) -> Unit,
    nome: String,
    onNome: (String) -> Unit,
) {
    OutlinedTextField(
        value = numero,
        onValueChange = onNumero,
        label = { Text("Telefone") },
        placeholder = { Text("(11) 99999-8888") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = nome,
        onValueChange = onNome,
        label = { Text("Nome (opcional)") },
        singleLine = true,
    )
}

/**
 * Conflitos nunca são resolvidos em silêncio: a tela diz o que existe e exige um
 * segundo toque para trocar uma exceção por outra.
 */
@Composable
private fun AvisoConflito(conflito: RuleConflict) {
    val texto = when (conflito) {
        RuleConflict.NONE -> return
        RuleConflict.INVALID_NUMBER -> "Número inválido."
        RuleConflict.IN_ALLOWLIST ->
            "Este número está na lista de permitidos e hoje nunca é bloqueado. " +
                "Confirme de novo para removê-lo de lá."
        RuleConflict.IN_BLOCKLIST ->
            "Este número está bloqueado permanentemente. " +
                "Confirme de novo para remover o bloqueio e usar a regra."
    }
    Spacer(Modifier.height(12.dp))
    Row {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
