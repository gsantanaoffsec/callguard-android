package br.dev.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import br.dev.callguard.core.CheckLevel
import br.dev.callguard.core.DiagnosticCheck
import br.dev.callguard.core.DiagnosticFix
import br.dev.callguard.core.DiagnosticsReport
import br.dev.callguard.core.NumberSimulation
import br.dev.callguard.core.ScreeningDecision

/**
 * Central de diagnostico.
 *
 * Existe porque "o app parece que nao esta bloqueando" e a duvida mais cara de um app
 * de filtragem: o usuario nao tem como ver o que nao aconteceu. Esta tela responde com
 * fatos verificaveis no proprio aparelho -- inclusive deixando ele testar um numero de
 * verdade contra as regras e o historico reais, sem gravar nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    report: DiagnosticsReport?,
    simulation: NumberSimulation?,
    backupMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSimulate: (String) -> Unit,
    onClearSimulation: () -> Unit,
    onFix: (DiagnosticFix) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearAttempts: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var numeroDeTeste by remember { mutableStateOf("") }
    var confirmandoLimpeza by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (report == null) {
                item {
                    Text(
                        "Coletando informações do aparelho…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                item { ResumoCard(report) }

                checkItems(report.checks, onFix)

                item { TesteDeNumeroCard(
                    numero = numeroDeTeste,
                    onNumeroChange = {
                        numeroDeTeste = it
                        onClearSimulation()
                    },
                    simulation = simulation,
                    onSimulate = { onSimulate(numeroDeTeste) },
                ) }

                item { ArmazenamentoCard(report) }
            }

            item {
                BackupCard(
                    mensagem = backupMessage,
                    onExport = onExport,
                    onImport = onImport,
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Manutenção", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Apagar o histórico de tentativas zera a contagem de todos os " +
                                "números. As regras, listas e o registro de decisões continuam.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = { confirmandoLimpeza = true }) {
                                Text("Zerar contagens")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onRefresh) { Text("Atualizar laudo") }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmandoLimpeza) {
        AlertDialog(
            onDismissRequest = { confirmandoLimpeza = false },
            title = { Text("Zerar contagens?") },
            text = {
                Text(
                    "Todo número volta a ter zero tentativas registradas. Quem já estava " +
                        "perto do limite ganha as chamadas de volta.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoLimpeza = false
                    onClearAttempts()
                }) { Text("Zerar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoLimpeza = false }) { Text("Cancelar") }
            },
        )
    }
}

/** Um `DiagnosticCheck` por linha, extraido para manter a lista principal legivel. */
private fun androidx.compose.foundation.lazy.LazyListScope.checkItems(
    checks: List<DiagnosticCheck>,
    onFix: (DiagnosticFix) -> Unit,
) {
    checks.forEach { check ->
        item(key = check.title) { CheckCard(check, onFix) }
    }
}

@Composable
private fun ResumoCard(report: DiagnosticsReport) {
    val (cor, titulo, texto) = when (report.worstLevel) {
        CheckLevel.BLOCKING -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            "A proteção NÃO está funcionando",
            "Existe pelo menos um item abaixo que impede o bloqueio de acontecer.",
        )

        CheckLevel.ATTENTION -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            "Protegendo, com ressalvas",
            "O bloqueio acontece, mas há um item que vale ajustar.",
        )

        CheckLevel.OK -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            "Tudo certo",
            "As ligações passam pelo CallGuard antes de tocar.",
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cor),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Regra em vigor: ${report.activePolicy.source.label} — " +
                    report.activePolicy.describe(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CheckCard(check: DiagnosticCheck, onFix: (DiagnosticFix) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Marcador(check.level)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    check.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // O botao de correcao so aparece onde existe uma correcao possivel: um
                // item informativo com botao desabilitado seria ruido.
                val correcao = check.fix
                if (correcao != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onFix(correcao) }) {
                        Text(check.fixLabel ?: "Corrigir")
                    }
                }
            }
        }
    }
}

/**
 * Um circulo colorido em vez de icones diferentes: o estado precisa ser lido de relance,
 * na vertical, sem o olho ter que interpretar simbolo por simbolo.
 */
@Composable
private fun Marcador(level: CheckLevel) {
    val cor = when (level) {
        CheckLevel.OK -> MaterialTheme.colorScheme.primary
        CheckLevel.ATTENTION -> MaterialTheme.colorScheme.tertiary
        CheckLevel.BLOCKING -> MaterialTheme.colorScheme.error
    }
    Spacer(
        Modifier
            .padding(top = 4.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(cor),
    )
}

@Composable
private fun TesteDeNumeroCard(
    numero: String,
    onNumeroChange: (String) -> Unit,
    simulation: NumberSimulation?,
    onSimulate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Testar um número", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Mostra o que aconteceria se este número ligasse agora, usando as regras e o " +
                    "histórico reais. Nada é gravado: consultar não conta como ligação.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = numero,
                onValueChange = onNumeroChange,
                label = { Text("Número") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSimulate, enabled = numero.isNotBlank()) { Text("Simular") }

            if (simulation != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                ResultadoDaSimulacao(simulation)
            }
        }
    }
}

@Composable
private fun ResultadoDaSimulacao(s: NumberSimulation) {
    val bloqueia = s.decision is ScreeningDecision.Block
    Surface(
        color = if (bloqueia) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(s.verdict(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(s.explanation(), style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(8.dp))
    LinhaDeDado("Número normalizado", s.normalizedNumber ?: "não reconhecido")
    LinhaDeDado("Procedência", s.origin.describe())
    val marcas = buildList {
        if (s.isEmergency) add("emergência")
        if (s.isAllowlisted) add("sempre permitido")
        if (s.isBlocklisted) add("sempre bloqueado")
        if (s.isSavedContact) add("contato salvo")
        if (s.hasCustomRule) add("tem regra própria")
    }
    LinhaDeDado("Situação", marcas.joinToString(", ").ifEmpty { "número comum" })
    s.appliedPolicy?.let {
        LinhaDeDado("Regra aplicada", "${it.source.label} — ${it.describe()}")
    }
}

@Composable
private fun ArmazenamentoCard(report: DiagnosticsReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("O que está guardado aqui", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tudo isto fica no armazenamento privado do app, neste aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val e = report.storage
            LinhaDeDado("Tentativas registradas", "${e.attempts} (${e.distinctNumbers} números)")
            LinhaDeDado("Números sempre permitidos", "${e.allowlist}")
            LinhaDeDado("Números sempre bloqueados", "${e.blocklist}")
            LinhaDeDado("Regras por número", "${e.customRules}")
            LinhaDeDado("Chamadas bloqueadas", "${e.blockedCalls}")
            LinhaDeDado("Decisões no registro", "${e.screeningEvents}")
            LinhaDeDado("Versão do banco", "v${e.databaseVersion}")
        }
    }
}

@Composable
private fun BackupCard(mensagem: String?, onExport: () -> Unit, onImport: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Backup das regras", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Salva ajustes, listas e regras num arquivo JSON legível, escolhido por você. " +
                    "O histórico de chamadas não vai junto — backup serve para recriar as " +
                    "regras, não para levar embora quem ligou para você.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onExport) { Text("Exportar") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onImport) { Text("Importar") }
            }
            if (mensagem != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    mensagem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LinhaDeDado(rotulo: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            valor,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1.2f),
        )
    }
}
