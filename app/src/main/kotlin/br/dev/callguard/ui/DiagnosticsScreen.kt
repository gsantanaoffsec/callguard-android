package br.dev.callguard.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.dev.callguard.core.CheckLevel
import br.dev.callguard.core.DiagnosticCheck
import br.dev.callguard.core.DiagnosticFix
import br.dev.callguard.core.DiagnosticsReport
import br.dev.callguard.core.NumberSimulation
import br.dev.callguard.core.ScreeningDecision
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgDataRow
import br.dev.callguard.ui.design.CgDialog
import br.dev.callguard.ui.design.CgDivider
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgPrimaryButton
import br.dev.callguard.ui.design.CgScreen
import br.dev.callguard.ui.design.CgSecondaryButton
import br.dev.callguard.ui.design.CgSectionHeader
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgStatusBlock
import br.dev.callguard.ui.design.CgSurface
import br.dev.callguard.ui.design.CgTag
import br.dev.callguard.ui.design.CgTextAction
import br.dev.callguard.ui.design.CgTextField
import br.dev.callguard.ui.design.CgType

/**
 * Central de diagnóstico.
 *
 * A pergunta que a tela responde — "está mesmo funcionando?" — é respondida no topo, em
 * tipografia grande, antes de qualquer detalhe. O resto é uma lista de verificações em
 * que a cor aparece só no ponto à esquerda: um laudo com sete blocos coloridos seria
 * ilegível justamente quando mais importa.
 */
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
    // Sem isto o gesto de voltar sairia do app: esta tela nao tem aba propria, entao o
    // caminho de volta e a tela inicial.
    BackHandler(onBack = onBack)

    var numeroDeTeste by remember { mutableStateOf("") }
    var confirmandoLimpeza by remember { mutableStateOf(false) }

    CgScreen(title = "Diagnóstico", onBack = onBack, bottomBar = bottomBar) {
        if (report == null) {
            item("carregando") {
                Text(
                    text = "Coletando informações do aparelho…",
                    style = CgType.body,
                    color = CgColor.TextSecondary,
                )
            }
        } else {
            item("resumo") { ResumoDoLaudo(report) }
            verificacoes(report.checks, onFix)
            item("teste") {
                TesteDeNumero(
                    numero = numeroDeTeste,
                    onNumeroChange = {
                        numeroDeTeste = it
                        onClearSimulation()
                    },
                    simulation = simulation,
                    onSimular = { onSimulate(numeroDeTeste) },
                )
            }
            item("armazenamento") { Armazenamento(report) }
        }

        item("backup") { Backup(backupMessage, onExport, onImport) }

        item("manutencao") {
            Column(Modifier.fillMaxWidth()) {
                CgSectionHeader(
                    label = "Manutenção",
                    description = "Zerar as contagens faz todo número voltar a ter zero " +
                        "tentativas. As regras, listas e registros continuam.",
                )
                CgSecondaryButton(
                    text = "Zerar contagens",
                    onClick = { confirmandoLimpeza = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                CgGap(CgSpace.sm)
                CgTextAction(
                    text = "Atualizar laudo",
                    onClick = onRefresh,
                    color = CgColor.TextSecondary,
                )
            }
        }
    }

    if (confirmandoLimpeza) {
        CgDialog(
            title = "Zerar contagens?",
            description = "Todo número volta a ter zero tentativas registradas. Quem já " +
                "estava perto do limite ganha as chamadas de volta.",
            onDismiss = { confirmandoLimpeza = false },
            confirmText = "Zerar",
            destructive = true,
            onConfirm = {
                confirmandoLimpeza = false
                onClearAttempts()
            },
        )
    }
}

@Composable
private fun ResumoDoLaudo(report: DiagnosticsReport) {
    val (manchete, apoio) = when (report.worstLevel) {
        CheckLevel.BLOCKING ->
            "A proteção não está funcionando." to
                "Há pelo menos um item abaixo que impede o bloqueio de acontecer."

        CheckLevel.ATTENTION ->
            "Protegendo, com ressalvas." to
                "O bloqueio acontece, mas há um item que vale ajustar."

        CheckLevel.OK ->
            "Tudo certo." to
                "As ligações passam pelo CallGuard antes de tocar. Regra em vigor: " +
                    "${report.activePolicy.source.label} — ${report.activePolicy.describe()}."
    }

    CgStatusBlock(
        active = report.worstLevel != CheckLevel.BLOCKING,
        headline = manchete,
        supporting = apoio,
    )
}

private fun LazyListScope.verificacoes(
    checks: List<DiagnosticCheck>,
    onFix: (DiagnosticFix) -> Unit,
) {
    item("verif-cabecalho") { CgSectionHeader("Verificações") }
    items(count = checks.size, key = { i -> "chk-${checks[i].title}" }) { indice ->
        Column {
            ItemDeVerificacao(checks[indice], onFix)
            if (indice < checks.lastIndex) CgDivider()
        }
    }
}

/**
 * Uma verificação.
 *
 * O estado é dito pelo ponto à esquerda e reforçado pelo texto do detalhe — nunca só
 * pela cor. O botão de correção só aparece onde existe correção possível: um item
 * informativo com botão desabilitado seria ruído.
 */
@Composable
private fun ItemDeVerificacao(check: DiagnosticCheck, onFix: (DiagnosticFix) -> Unit) {
    val cor = when (check.level) {
        CheckLevel.OK -> CgColor.Positive
        CheckLevel.ATTENTION -> CgColor.Warning
        CheckLevel.BLOCKING -> CgColor.Negative
    }

    Row(Modifier.fillMaxWidth().padding(vertical = CgSpace.lg)) {
        Spacer(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(cor),
        )
        Spacer(Modifier.width(CgSpace.lg))
        Column(Modifier.weight(1f)) {
            Text(text = check.title, style = CgType.subtitle, color = CgColor.TextPrimary)
            Spacer(Modifier.height(CgSpace.xs))
            Text(text = check.detail, style = CgType.caption, color = CgColor.TextSecondary)

            val correcao = check.fix
            if (correcao != null) {
                CgGap(CgSpace.md)
                CgSecondaryButton(
                    text = check.fixLabel ?: "Corrigir",
                    onClick = { onFix(correcao) },
                )
            }
        }
    }
}

@Composable
private fun TesteDeNumero(
    numero: String,
    onNumeroChange: (String) -> Unit,
    simulation: NumberSimulation?,
    onSimular: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        CgSectionHeader(
            label = "Testar um número",
            description = "Mostra o que aconteceria se ele ligasse agora, com as regras e o " +
                "histórico reais. Nada é gravado: consultar não conta como ligação.",
        )
        CgTextField(
            value = numero,
            onValueChange = onNumeroChange,
            label = "Número",
            placeholder = "(11) 99999-8888",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        CgGap(CgSpace.md)
        CgPrimaryButton(
            text = "Simular",
            onClick = onSimular,
            enabled = numero.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (simulation != null) {
            CgGap(CgSpace.xl)
            ResultadoDaSimulacao(simulation)
        }
    }
}

@Composable
private fun ResultadoDaSimulacao(s: NumberSimulation) {
    val bloqueia = s.decision is ScreeningDecision.Block
    val cor = if (bloqueia) CgColor.Negative else CgColor.Positive

    CgSurface(color = if (bloqueia) CgColor.NegativeDim else CgColor.PositiveDim) {
        Column {
            Text(text = s.verdict(), style = CgType.title, color = cor)
            Spacer(Modifier.height(CgSpace.sm))
            Text(text = s.explanation(), style = CgType.caption, color = CgColor.TextSecondary)
        }
    }

    CgGap(CgSpace.lg)
    CgDataRow("Número normalizado", s.normalizedNumber ?: "não reconhecido")
    CgDataRow("Procedência", s.origin.describe())

    val marcas = buildList {
        if (s.isEmergency) add("emergência")
        if (s.isAllowlisted) add("sempre permitido")
        if (s.isBlocklisted) add("sempre bloqueado")
        if (s.isSavedContact) add("contato salvo")
        if (s.hasCustomRule) add("tem regra própria")
    }
    CgDataRow("Situação", marcas.joinToString(", ").ifEmpty { "número comum" })
    s.appliedPolicy?.let {
        CgDataRow("Regra aplicada", "${it.source.label} — ${it.describe()}")
    }
}

@Composable
private fun Armazenamento(report: DiagnosticsReport) {
    val e = report.storage
    Column(Modifier.fillMaxWidth()) {
        CgSectionHeader(
            label = "O que está guardado aqui",
            description = "Tudo isto fica no armazenamento privado do app, neste aparelho.",
        )
        CgDataRow("Tentativas registradas", "${e.attempts} (${e.distinctNumbers} números)")
        CgDataRow("Números sempre permitidos", "${e.allowlist}")
        CgDataRow("Números sempre bloqueados", "${e.blocklist}")
        CgDataRow("Regras por número", "${e.customRules}")
        CgDataRow("Chamadas bloqueadas", "${e.blockedCalls}")
        CgDataRow("Decisões no registro", "${e.screeningEvents}")
        CgDataRow("Versão do banco", "v${e.databaseVersion}")
    }
}

@Composable
private fun Backup(mensagem: String?, onExport: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CgSectionHeader(
            label = "Backup das regras",
            description = "Salva ajustes, listas e regras num arquivo JSON legível, onde " +
                "você escolher. O histórico de chamadas não vai junto.",
        )
        Row(Modifier.fillMaxWidth()) {
            CgPrimaryButton(text = "Exportar", onClick = onExport, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(CgSpace.md))
            CgSecondaryButton(text = "Importar", onClick = onImport, modifier = Modifier.weight(1f))
        }
        if (mensagem != null) {
            CgGap(CgSpace.md)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CgTag(text = "backup", color = CgColor.TextTertiary, background = CgColor.Surface)
                Spacer(Modifier.width(CgSpace.md))
                Text(text = mensagem, style = CgType.caption, color = CgColor.TextSecondary)
            }
        }
    }
}
