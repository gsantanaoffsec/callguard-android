package br.dev.callguard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import br.dev.callguard.core.CallPolicy
import br.dev.callguard.core.PolicySource
import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.core.SchedulePolicy
import br.dev.callguard.ui.design.CgCallout
import br.dev.callguard.ui.design.cgEnter
import br.dev.callguard.ui.design.CgChoiceChip
import br.dev.callguard.ui.design.CgChoiceRow
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgDialog
import br.dev.callguard.ui.design.CgDivider
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgIconButton
import br.dev.callguard.ui.design.CgListItem
import br.dev.callguard.ui.design.CgMotion
import br.dev.callguard.ui.design.CgNotice
import br.dev.callguard.ui.design.CgNoticeTone
import br.dev.callguard.ui.design.CgScreen
import br.dev.callguard.ui.design.CgSectionHeader
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgSwitchRow
import br.dev.callguard.ui.design.CgTag
import br.dev.callguard.ui.design.CgTextAction
import br.dev.callguard.ui.design.CgTextField
import br.dev.callguard.ui.design.CgType
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
 * Regras que fogem da configuração geral.
 *
 * A tela abre com a ordem de precedência escrita como uma escada numerada — quem lê de
 * cima para baixo enxerga a mesma hierarquia que o motor aplica. Depois vêm as três
 * seções na mesma ordem em que elas vencem umas às outras.
 */
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

    CgScreen(
        title = "Regras",
        subtitle = "Exceções que passam na frente da regra geral.",
        bottomBar = bottomBar,
    ) {
        item("precedencia") { Box(Modifier.cgEnter(1)) { EscadaDePrecedencia() } }

        secaoBloqueio(uiState, onRemoveBlocklist) { dialogoBloqueio = true }
        secaoRegrasPorNumero(uiState, onRemoveCustomRule) { dialogoRegra = true }

        item("noturno") { Box(Modifier.cgEnter(4)) { ModoNoturno(uiState.schedule, onScheduleChange) } }
    }

    if (dialogoBloqueio) {
        DialogoBloqueio(
            onDismiss = { dialogoBloqueio = false },
            onConfirmar = onAddBlocklist,
            onSucesso = { dialogoBloqueio = false },
        )
    }

    if (dialogoRegra) {
        DialogoRegraPorNumero(
            onDismiss = { dialogoRegra = false },
            onConfirmar = onAddCustomRule,
            onSucesso = { dialogoRegra = false },
        )
    }
}

/**
 * A ordem de precedência, desenhada.
 *
 * Antes era um parágrafo com setas dentro de um cartão cinza. Uma lista numerada com o
 * número em monoespaçada é lida como o que é: uma sequência em que a primeira que casa
 * decide.
 */
@Composable
private fun EscadaDePrecedencia() {
    val niveis = listOf(
        "Emergência" to "nunca bloqueada",
        "Nunca bloquear" to "sua lista de permitidos",
        "Bloqueio permanente" to "sua lista de bloqueados",
        "Contatos salvos" to "conforme o ajuste da tela inicial",
        "Regra do número" to "limite próprio",
        "Modo noturno" to "dentro do horário",
        "Regra geral" to "todo o resto",
    )

    Column(Modifier.fillMaxWidth()) {
        CgSectionHeader(label = "Quem decide primeiro", top = true)
        niveis.forEachIndexed { indice, (nome, detalhe) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CgSpace.sm),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = "${indice + 1}",
                    style = CgType.mono,
                    color = CgColor.TextDisabled,
                    modifier = Modifier.padding(end = CgSpace.lg),
                )
                Text(text = nome, style = CgType.body, color = CgColor.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(text = detalhe, style = CgType.caption, color = CgColor.TextTertiary)
            }
        }
        CgGap(CgSpace.md)
        Text(
            text = "A primeira que se aplicar decide. Nada abaixo dela é consultado.",
            style = CgType.caption,
            color = CgColor.TextTertiary,
        )
    }
}

private fun LazyListScope.secaoBloqueio(
    uiState: CallGuardUiState,
    onRemover: (String) -> Unit,
    onAdicionar: () -> Unit,
) {
    item("bloq-cabecalho") {
        CgSectionHeader(
            label = "Bloqueio permanente",
            description = "Sempre recusados, não importa quantas vezes liguem.",
        )
    }
    if (uiState.blocklist.isEmpty()) {
        item("bloq-vazio") { LinhaVazia("Nenhum número bloqueado permanentemente.") }
    } else {
        items(
            count = uiState.blocklist.size,
            key = { i -> "b-${uiState.blocklist[i].normalizedNumber}" },
        ) { indice ->
            val item = uiState.blocklist[indice]
            Column(Modifier.animateItem()) {
                CgListItem(
                    title = item.label,
                    subtitle = item.normalizedNumber,
                    trailing = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CgTag(
                                text = "sempre",
                                color = CgColor.Negative,
                                background = CgColor.NegativeDim,
                            )
                            CgIconButton(
                                icon = Icons.Default.Delete,
                                contentDescription = "Remover ${item.label}",
                                tint = CgColor.TextTertiary,
                                onClick = { onRemover(item.normalizedNumber) },
                            )
                        }
                    },
                )
                if (indice < uiState.blocklist.lastIndex) CgDivider()
            }
        }
    }
    item("bloq-add") {
        CgTextAction(
            text = "Bloquear um número",
            icon = Icons.Default.Add,
            onClick = onAdicionar,
            modifier = Modifier.padding(top = CgSpace.sm),
        )
    }
}

private fun LazyListScope.secaoRegrasPorNumero(
    uiState: CallGuardUiState,
    onRemover: (String) -> Unit,
    onAdicionar: () -> Unit,
) {
    item("regra-cabecalho") {
        CgSectionHeader(
            label = "Regra por número",
            description = "Limite próprio, mais forte que o modo noturno e que a regra geral.",
        )
    }
    if (uiState.customRules.isEmpty()) {
        item("regra-vazio") { LinhaVazia("Nenhuma regra por número.") }
    } else {
        items(
            count = uiState.customRules.size,
            key = { i -> "r-${uiState.customRules[i].normalizedNumber}" },
        ) { indice ->
            val regra = uiState.customRules[indice]
            val politica = CallPolicy(regra.maxAllowedCalls, regra.windowMillis, PolicySource.CUSTOM)
            Column(Modifier.animateItem()) {
                CgListItem(
                    title = regra.label,
                    subtitle = regra.normalizedNumber,
                    meta = if (regra.enabled) politica.describe() else "Desativada",
                    metaColor = if (regra.enabled) CgColor.TextSecondary else CgColor.TextDisabled,
                    trailing = {
                        CgIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "Remover ${regra.label}",
                            tint = CgColor.TextTertiary,
                            onClick = { onRemover(regra.normalizedNumber) },
                        )
                    },
                )
                if (indice < uiState.customRules.lastIndex) CgDivider()
            }
        }
    }
    item("regra-add") {
        CgTextAction(
            text = "Criar regra",
            icon = Icons.Default.Add,
            onClick = onAdicionar,
            modifier = Modifier.padding(top = CgSpace.sm),
        )
    }
}

@Composable
private fun LinhaVazia(texto: String) {
    Text(
        text = texto,
        style = CgType.caption,
        color = CgColor.TextTertiary,
        modifier = Modifier.padding(vertical = CgSpace.sm),
    )
}

/**
 * Modo noturno.
 *
 * Os ajustes só existem quando o modo está ligado, e entram com uma expansão curta em
 * vez de aparecerem de uma vez — a animação explica que aquele bloco pertence ao
 * interruptor de cima.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModoNoturno(schedule: SchedulePolicy, onChange: (SchedulePolicy) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CgSectionHeader("Modo noturno")
        CgSwitchRow(
            title = "Regra mais rígida em um horário",
            description = "Segue o relógio do aparelho. Não há serviço nem alarme por trás: " +
                "quando a chamada chega, o app pergunta que horas são.",
            checked = schedule.enabled,
            onCheckedChange = { onChange(schedule.copy(enabled = it)) },
        )

        AnimatedVisibility(
            visible = schedule.enabled,
            enter = expandVertically(tween(CgMotion.slow, easing = CgMotion.standard)) +
                fadeIn(tween(CgMotion.slow)),
            exit = shrinkVertically(tween(CgMotion.normal, easing = CgMotion.standard)) +
                fadeOut(tween(CgMotion.fast)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                CgDivider()
                CgGap(CgSpace.xl)

                CampoDeAjuste("Começa às") {
                    SeletorHora(schedule.startMinuteOfDay) {
                        onChange(schedule.copy(startMinuteOfDay = it))
                    }
                }
                CampoDeAjuste("Termina às") {
                    SeletorHora(schedule.endMinuteOfDay) {
                        onChange(schedule.copy(endMinuteOfDay = it))
                    }
                }

                if (schedule.startMinuteOfDay > schedule.endMinuteOfDay) {
                    CgNotice(
                        text = "Atravessa a meia-noite. O dia escolhido é o do início do " +
                            "período — a madrugada de terça pertence à segunda.",
                        tone = CgNoticeTone.INFO,
                    )
                    CgGap(CgSpace.md)
                }

                CampoDeAjuste("Dias") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CgSpace.sm),
                        verticalArrangement = Arrangement.spacedBy(CgSpace.sm),
                    ) {
                        DIAS.forEach { (dia, rotulo) ->
                            CgChoiceChip(
                                text = rotulo,
                                selected = dia in schedule.activeDays,
                                onClick = {
                                    val novos = if (dia in schedule.activeDays) {
                                        schedule.activeDays - dia
                                    } else {
                                        schedule.activeDays + dia
                                    }
                                    // Um período sem nenhum dia nunca valeria; recusamos
                                    // em vez de deixar o usuário com um modo inerte.
                                    if (novos.isNotEmpty()) {
                                        onChange(schedule.copy(activeDays = novos))
                                    }
                                },
                            )
                        }
                    }
                }

                CampoDeAjuste("Chamadas permitidas no período") {
                    CgChoiceRow(
                        options = ProtectionSettings.MAX_CALL_OPTIONS,
                        selected = schedule.maxAllowedCalls,
                        label = { it.toString() },
                        onSelected = { onChange(schedule.copy(maxAllowedCalls = it)) },
                    )
                }

                CampoDeAjuste("Dentro de") {
                    CgChoiceRow(
                        options = ProtectionSettings.WINDOW_MINUTE_OPTIONS,
                        selected = TimeUnit.MILLISECONDS.toMinutes(schedule.windowMillis).toInt(),
                        label = { if (it >= 60) "${it / 60} h" else "$it min" },
                        onSelected = {
                            onChange(
                                schedule.copy(windowMillis = TimeUnit.MINUTES.toMillis(it.toLong())),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CampoDeAjuste(rotulo: String, conteudo: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = CgSpace.xl)) {
        Text(text = rotulo, style = CgType.subtitle, color = CgColor.TextPrimary)
        Spacer(Modifier.height(CgSpace.md))
        conteudo()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeletorHora(minutoDoDia: Int, onChange: (Int) -> Unit) {
    val horaAtual = minutoDoDia / 60
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CgSpace.sm),
        verticalArrangement = Arrangement.spacedBy(CgSpace.sm),
    ) {
        (0..23).forEach { h ->
            CgChoiceChip(
                text = "%02d".format(h),
                selected = h == horaAtual,
                onClick = { onChange(h * 60) },
            )
        }
    }
}

@Composable
private fun DialogoBloqueio(
    onDismiss: () -> Unit,
    onConfirmar: (numero: String, nome: String, forcar: Boolean) -> RuleConflict,
    onSucesso: () -> Unit,
) {
    var numero by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var conflito by remember { mutableStateOf(RuleConflict.NONE) }

    CgDialog(
        title = "Bloquear sempre",
        description = "Chamadas deste número serão recusadas enquanto ele estiver aqui. " +
            "Você pode remover a qualquer momento.",
        onDismiss = onDismiss,
        confirmText = if (conflito == RuleConflict.IN_ALLOWLIST) "Remover e bloquear" else "Bloquear",
        confirmEnabled = numero.isNotBlank(),
        destructive = true,
        onConfirm = {
            val forcar = conflito != RuleConflict.NONE
            conflito = onConfirmar(numero, nome, forcar)
            if (conflito == RuleConflict.NONE) onSucesso()
        },
    ) {
        Column {
            CamposDeNumero(
                numero = numero,
                onNumero = { numero = it; conflito = RuleConflict.NONE },
                nome = nome,
                onNome = { nome = it },
            )
            AvisoConflito(conflito)
        }
    }
}

@Composable
private fun DialogoRegraPorNumero(
    onDismiss: () -> Unit,
    onConfirmar: (raw: String, label: String, max: Int, windowMinutes: Int, force: Boolean) -> RuleConflict,
    onSucesso: () -> Unit,
) {
    var numero by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var maximo by remember { mutableIntStateOf(1) }
    var janela by remember { mutableIntStateOf(10) }
    var conflito by remember { mutableStateOf(RuleConflict.NONE) }

    CgDialog(
        title = "Regra por número",
        description = "Um limite só para este número, que passa na frente do modo noturno " +
            "e da regra geral.",
        onDismiss = onDismiss,
        confirmText = if (conflito == RuleConflict.NONE) "Criar regra" else "Remover e criar",
        confirmEnabled = numero.isNotBlank(),
        onConfirm = {
            val forcar = conflito != RuleConflict.NONE
            conflito = onConfirmar(numero, nome, maximo, janela, forcar)
            if (conflito == RuleConflict.NONE) onSucesso()
        },
    ) {
        Column {
            CamposDeNumero(
                numero = numero,
                onNumero = { numero = it; conflito = RuleConflict.NONE },
                nome = nome,
                onNome = { nome = it },
            )
            CgGap(CgSpace.xl)
            Text("Chamadas permitidas", style = CgType.caption, color = CgColor.TextSecondary)
            CgGap(CgSpace.sm)
            CgChoiceRow(
                options = ProtectionSettings.MAX_CALL_OPTIONS,
                selected = maximo,
                label = { it.toString() },
                onSelected = { maximo = it },
            )
            CgGap(CgSpace.lg)
            Text("Dentro de", style = CgType.caption, color = CgColor.TextSecondary)
            CgGap(CgSpace.sm)
            CgChoiceRow(
                options = ProtectionSettings.WINDOW_MINUTE_OPTIONS,
                selected = janela,
                label = { if (it >= 60) "${it / 60} h" else "$it min" },
                onSelected = { janela = it },
            )
            AvisoConflito(conflito)
        }
    }
}

@Composable
private fun CamposDeNumero(
    numero: String,
    onNumero: (String) -> Unit,
    nome: String,
    onNome: (String) -> Unit,
) {
    CgTextField(
        value = numero,
        onValueChange = onNumero,
        label = "Telefone",
        placeholder = "(11) 99999-8888",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Spacer(Modifier.height(CgSpace.md))
    CgTextField(
        value = nome,
        onValueChange = onNome,
        label = "Nome (opcional)",
    )
}

/**
 * Conflitos nunca são resolvidos em silêncio: a tela diz o que existe e exige um segundo
 * toque para trocar uma exceção por outra.
 */
@Composable
private fun AvisoConflito(conflito: RuleConflict) {
    val texto = when (conflito) {
        RuleConflict.NONE -> return
        RuleConflict.INVALID_NUMBER -> "Número inválido."
        RuleConflict.IN_ALLOWLIST ->
            "Este número está na lista de permitidos e hoje nunca é bloqueado. Confirme de " +
                "novo para removê-lo de lá."

        RuleConflict.IN_BLOCKLIST ->
            "Este número está bloqueado permanentemente. Confirme de novo para remover o " +
                "bloqueio e usar a regra."
    }
    CgGap(CgSpace.md)
    CgCallout(text = texto, color = CgColor.Warning, background = CgColor.WarningDim)
}
