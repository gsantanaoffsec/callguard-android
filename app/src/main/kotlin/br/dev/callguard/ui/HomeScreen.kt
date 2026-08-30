package br.dev.callguard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import br.dev.callguard.core.WindowFormat
import androidx.compose.ui.unit.dp
import br.dev.callguard.ui.design.CgLogoMark
import br.dev.callguard.ui.design.LocalScreenEntrance
import br.dev.callguard.ui.design.CgMotion
import br.dev.callguard.ui.design.cgAnimatedCount
import br.dev.callguard.ui.design.cgEnter
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgDialog
import br.dev.callguard.ui.design.CgPickerField
import br.dev.callguard.ui.design.CgDivider
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgIconButton
import br.dev.callguard.ui.design.CgListItem
import br.dev.callguard.ui.design.CgMetric
import br.dev.callguard.ui.design.CgNavRow
import br.dev.callguard.ui.design.CgNotice
import br.dev.callguard.ui.design.CgNoticeTone
import br.dev.callguard.ui.design.CgPrimaryButton
import br.dev.callguard.ui.design.CgScreen
import br.dev.callguard.ui.design.CgSectionHeader
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgStatusBlock
import br.dev.callguard.ui.design.CgSwitchRow
import br.dev.callguard.ui.design.CgTextAction
import br.dev.callguard.ui.design.CgTextField
import br.dev.callguard.ui.design.CgType

/** Qual folha de escolha está aberta. `null` quando nenhuma. */
enum class FolhaDeAjuste { LIMITE, JANELA }

/**
 * Tela inicial.
 *
 * A composição é a de um painel, não a de um formulário: o estado da proteção ocupa o
 * topo em tipografia grande, os números vêm logo abaixo sem moldura, e todo o resto é
 * lido como uma lista de ajustes agrupados por assunto.
 *
 * Nenhum cartão. Os grupos são separados por um rótulo em caixa alta e por espaço; as
 * linhas, por um traço de 1 dp. Era isso que os cartões faziam antes, gastando três
 * vezes mais altura e uma borda a cada item.
 */
@Composable
fun HomeScreen(
    uiState: CallGuardUiState,
    onOpenPermissions: () -> Unit,
    onProtectionChange: (Boolean) -> Unit,
    onMaxCallsChange: (Int) -> Unit,
    onWindowMinutesChange: (Int) -> Unit,
    onApplyToContactsChange: (Boolean) -> Unit,
    onNotifyOnBlockChange: (Boolean) -> Unit,
    onAddAllowlistEntry: (rawNumber: String, label: String) -> Boolean,
    onRemoveAllowlistEntry: (String) -> Unit,
    onOpenBlockedCalls: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onBiometricLockChange: (Boolean) -> Unit,
    biometricAvailability: BiometricAvailability,
    bottomBar: @Composable () -> Unit,
) {
    var mostrarDialogo by remember { mutableStateOf(false) }
    var folhaAberta by remember { mutableStateOf<FolhaDeAjuste?>(null) }

    CgScreen(
        title = "CallGuard",
        // A marca se monta junto com a entrada da tela: o contorno do escudo se fecha e
        // o branco sobe, os mesmos dois tempos da abertura do app, em miniatura.
        leading = {
            val entrada = LocalScreenEntrance.current
            CgLogoMark(size = 30.dp, progress = { entrada.value })
        },
        bottomBar = bottomBar,
    ) {
        item("status") { Box(Modifier.cgEnter(1)) { BlocoDeEstado(uiState, onOpenPermissions) } }

        item("numeros") { Box(Modifier.cgEnter(2)) { FaixaDeNumeros(uiState) } }

        secaoRegra(uiState) { folhaAberta = it }

        secaoComportamento(
            uiState = uiState,
            onProtectionChange = onProtectionChange,
            onApplyToContactsChange = onApplyToContactsChange,
            onNotifyOnBlockChange = onNotifyOnBlockChange,
        )

        secaoPermitidos(
            uiState = uiState,
            onAdicionar = { mostrarDialogo = true },
            onRemover = onRemoveAllowlistEntry,
        )

        secaoPrivacidade(uiState, biometricAvailability, onBiometricLockChange)

        item("mais-cabecalho") { CgSectionHeader("Mais") }
        item("mais-bloqueadas") {
            CgNavRow(
                title = "Chamadas bloqueadas",
                subtitle = "Histórico do que foi recusado",
                value = uiState.blockedCallsTotal.toString(),
                onClick = onOpenBlockedCalls,
            )
        }
        item("mais-divisor") { CgDivider() }
        item("mais-permissoes") {
            CgNavRow(
                title = "Permissões",
                subtitle = "O que o app usa, por que, e o que ele nunca pede",
                value = if (uiState.pendingPermissions > 0) {
                    "${uiState.pendingPermissions} pendente" +
                        if (uiState.pendingPermissions > 1) "s" else ""
                } else {
                    null
                },
                onClick = onOpenPermissions,
            )
        }
        item("mais-divisor2") { CgDivider() }
        item("mais-diagnostico") {
            CgNavRow(
                title = "Diagnóstico e backup",
                subtitle = "Conferir se está funcionando, testar um número, salvar as regras",
                onClick = onOpenDiagnostics,
            )
        }
    }

    when (folhaAberta) {
        FolhaDeAjuste.LIMITE -> LimiteDeChamadasSheet(
            atual = uiState.settings.maxAllowedCalls,
            onSelect = onMaxCallsChange,
            onDismiss = { folhaAberta = null },
        )

        FolhaDeAjuste.JANELA -> JanelaDeTempoSheet(
            atualEmMinutos = uiState.settings.windowMinutes,
            onSelect = onWindowMinutesChange,
            onDismiss = { folhaAberta = null },
        )

        null -> Unit
    }

    if (mostrarDialogo) {
        DialogoAdicionarPermitido(
            onDismiss = { mostrarDialogo = false },
            onConfirmar = { numero, nome ->
                val adicionado = onAddAllowlistEntry(numero, nome)
                if (adicionado) mostrarDialogo = false
                adicionado
            },
        )
    }
}

/**
 * O herói da tela.
 *
 * Duas linhas grandes dizendo o que está acontecendo, e uma linha de apoio dizendo por
 * quê. Quando falta a autorização do sistema, a ação de corrigir aparece aqui mesmo —
 * é o único lugar em que ela é urgente.
 */
@Composable
private fun BlocoDeEstado(uiState: CallGuardUiState, onOpenPermissions: () -> Unit) {
    val protegendo = uiState.isActuallyProtecting

    val (manchete, apoio) = when {
        !uiState.roleAvailable ->
            "Este aparelho não filtra chamadas." to
                "O Android deste modelo não oferece a função de filtragem para aplicativos " +
                "de terceiros."

        !uiState.roleHeld ->
            "Falta a sua autorização." to
                "Para recusar uma chamada antes de o telefone tocar, o Android exige que o " +
                "CallGuard seja o app de filtragem. Só você pode conceder isso."

        !uiState.settings.protectionEnabled ->
            "A proteção está desligada." to
                "A autorização foi concedida, mas nenhuma chamada é recusada enquanto o " +
                "interruptor abaixo estiver desligado."

        else ->
            "Chamadas insistentes são recusadas." to
                "A partir da ${uiState.settings.maxAllowedCalls + 1}ª ligação do mesmo " +
                "número em ${uiState.settings.windowMinutes} minutos, sem o telefone tocar."
    }

    Column(Modifier.fillMaxWidth()) {
        // A manchete muda quando a protecao liga, desliga ou perde a autorizacao.
        // Trocar o texto de uma vez le como um defeito de renderizacao; a fusao curta
        // deixa claro que foi o ESTADO que mudou.
        AnimatedContent(
            targetState = manchete to apoio,
            transitionSpec = {
                (fadeIn(tween(CgMotion.normal, delayMillis = 60)) +
                    slideInVertically(tween(CgMotion.slow, delayMillis = 60)) { it / 8 })
                    .togetherWith(fadeOut(tween(CgMotion.fast)))
            },
            label = "manchete-estado",
        ) { (textoManchete, textoApoio) ->
            CgStatusBlock(
                active = protegendo,
                headline = textoManchete,
                supporting = textoApoio,
            )
        }

        if (uiState.roleAvailable && !uiState.roleHeld) {
            CgGap(CgSpace.xxl)
            // Leva para a tela de permissões em vez de abrir o diálogo do sistema direto:
            // quem chega aqui na primeira vez merece ler o que vai ser pedido antes de o
            // primeiro diálogo aparecer.
            CgPrimaryButton(
                text = "Configurar permissões",
                onClick = onOpenPermissions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Faixa de números.
 *
 * O que antes era um cartão dizendo "12 no total" virou o próprio 12. Três dados que
 * cabem numa olhada, separados por espaço em vez de por bordas.
 */
@Composable
private fun FaixaDeNumeros(uiState: CallGuardUiState) {
    Column(Modifier.fillMaxWidth().padding(top = CgSpace.section)) {
        CgDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = CgSpace.xl)) {
            CgMetric(
                // Anda ate o novo valor: um numero que salta e indistinguivel de um erro
                // de leitura, e este muda enquanto a tela esta aberta.
                value = cgAnimatedCount(uiState.blockedCallsTotal).toString(),
                label = "Bloqueadas",
                valueStyle = CgType.headline,
                modifier = Modifier.weight(1f),
            )
            CgMetric(
                value = uiState.settings.maxAllowedCalls.toString(),
                label = "Limite",
                valueStyle = CgType.headline,
                modifier = Modifier.weight(1f),
            )
            CgMetric(
                value = if (uiState.settings.windowMinutes >= 60) {
                    "${uiState.settings.windowMinutes / 60}h"
                } else {
                    "${uiState.settings.windowMinutes}m"
                },
                label = "Janela",
                valueStyle = CgType.headline,
                modifier = Modifier.weight(1f),
            )
        }
        CgDivider()
    }
}

private fun LazyListScope.secaoRegra(
    uiState: CallGuardUiState,
    onAbrirFolha: (FolhaDeAjuste) -> Unit,
) {
    item("regra-cabecalho") {
        CgSectionHeader(
            label = "Regra geral",
            description = "Vale para todo número que não tenha regra própria.",
        )
    }
    item("regra-max") {
        CgPickerField(
            label = "Chamadas permitidas",
            value = if (uiState.settings.maxAllowedCalls == 1) {
                "1 chamada"
            } else {
                "${uiState.settings.maxAllowedCalls} chamadas"
            },
            onClick = { onAbrirFolha(FolhaDeAjuste.LIMITE) },
            modifier = Modifier.padding(bottom = CgSpace.xl),
        )
    }
    item("regra-janela") {
        Column(Modifier.fillMaxWidth()) {
            CgPickerField(
                label = "Dentro de",
                value = WindowFormat.short(uiState.settings.windowMinutes),
                onClick = { onAbrirFolha(FolhaDeAjuste.JANELA) },
            )
            CgGap(CgSpace.lg)
            Text(
                text = "Janela deslizante: contam só as ligações dos últimos " +
                    "${WindowFormat.long(uiState.settings.windowMinutes)}. Quem para de " +
                    "ligar volta a passar sozinho.",
                style = CgType.caption,
                color = CgColor.TextTertiary,
            )
        }
    }
}

private fun LazyListScope.secaoComportamento(
    uiState: CallGuardUiState,
    onProtectionChange: (Boolean) -> Unit,
    onApplyToContactsChange: (Boolean) -> Unit,
    onNotifyOnBlockChange: (Boolean) -> Unit,
) {
    item("comp-cabecalho") { CgSectionHeader("Comportamento") }
    item("comp-protecao") {
        CgSwitchRow(
            title = "Bloquear chamadas insistentes",
            description = "O interruptor mestre. Desligado, nada é recusado.",
            checked = uiState.settings.protectionEnabled,
            onCheckedChange = onProtectionChange,
        )
    }
    item("comp-div1") { CgDivider() }
    item("comp-contatos") {
        Column {
            CgSwitchRow(
                title = "Aplicar aos contatos salvos",
                description = if (uiState.settings.applyToContacts) {
                    "A regra vale para a agenda também. Precisa da permissão de contatos."
                } else {
                    "Contatos salvos nunca são bloqueados — o próprio Android nem entrega " +
                        "essas chamadas ao app."
                },
                checked = uiState.settings.applyToContacts,
                onCheckedChange = onApplyToContactsChange,
            )
            if (uiState.contactsModeNeedsPermission) {
                CgNotice(
                    text = "Permissão de contatos não concedida. Contatos salvos continuam " +
                        "passando.",
                    tone = CgNoticeTone.WARNING,
                )
                CgGap(CgSpace.sm)
            }
        }
    }
    item("comp-div2") { CgDivider() }
    item("comp-avisos") {
        Column {
            CgSwitchRow(
                title = "Avisar quando bloquear",
                description = "Notificação silenciosa, sem som e sem vibração.",
                checked = uiState.settings.notifyOnBlock,
                onCheckedChange = onNotifyOnBlockChange,
            )
            if (uiState.notificationsNeedPermission) {
                CgNotice(
                    text = "Permissão de notificações não concedida. Os bloqueios vão " +
                        "acontecer sem aviso.",
                    tone = CgNoticeTone.WARNING,
                )
                CgGap(CgSpace.sm)
            }
        }
    }
}

private fun LazyListScope.secaoPermitidos(
    uiState: CallGuardUiState,
    onAdicionar: () -> Unit,
    onRemover: (String) -> Unit,
) {
    item("perm-cabecalho") {
        CgSectionHeader(
            label = "Nunca bloquear",
            description = "Estes números passam sempre, não importa quantas vezes liguem.",
        )
    }

    if (uiState.allowlist.isEmpty()) {
        item("perm-vazio") {
            Text(
                text = "Nenhum número na lista.",
                style = CgType.caption,
                color = CgColor.TextTertiary,
                modifier = Modifier.padding(vertical = CgSpace.sm),
            )
        }
    } else {
        items(
            count = uiState.allowlist.size,
            key = { indice -> "perm-${uiState.allowlist[indice].normalizedNumber}" },
        ) { indice ->
            val entrada = uiState.allowlist[indice]
            // `animateItem` faz as linhas de baixo subirem quando uma e removida, em vez
            // de a lista dar um salto. E o movimento que explica o que aconteceu.
            Column(Modifier.animateItem()) {
                CgListItem(
                    title = entrada.label,
                    subtitle = entrada.normalizedNumber,
                    trailing = {
                        CgIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "Remover ${entrada.label}",
                            tint = CgColor.TextTertiary,
                            onClick = { onRemover(entrada.normalizedNumber) },
                        )
                    },
                )
                if (indice < uiState.allowlist.lastIndex) CgDivider()
            }
        }
    }

    item("perm-adicionar") {
        CgTextAction(
            text = "Adicionar número",
            icon = Icons.Default.Add,
            onClick = onAdicionar,
            modifier = Modifier.padding(top = CgSpace.sm),
        )
    }
}

private fun LazyListScope.secaoPrivacidade(
    uiState: CallGuardUiState,
    disponibilidade: BiometricAvailability,
    onBiometricLockChange: (Boolean) -> Unit,
) {
    item("priv-cabecalho") { CgSectionHeader("Privacidade") }
    item("priv-biometria") {
        Column {
            CgSwitchRow(
                title = "Exigir biometria para abrir",
                description = "Pede sua digital ou a senha do aparelho quando o app volta " +
                    "para a frente.",
                checked = uiState.settings.biometricLockEnabled,
                onCheckedChange = onBiometricLockChange,
                enabled = disponibilidade == BiometricAvailability.AVAILABLE ||
                    uiState.settings.biometricLockEnabled,
            )
            when (disponibilidade) {
                BiometricAvailability.NONE_ENROLLED -> CgNotice(
                    text = "Este aparelho ainda não tem digital, rosto ou senha de tela " +
                        "cadastrados.",
                    tone = CgNoticeTone.WARNING,
                )

                BiometricAvailability.UNSUPPORTED -> CgNotice(
                    text = "Este aparelho não oferece biometria nem bloqueio de tela.",
                    tone = CgNoticeTone.WARNING,
                )

                BiometricAvailability.AVAILABLE -> Unit
            }
        }
    }
}

@Composable
private fun DialogoAdicionarPermitido(
    onDismiss: () -> Unit,
    onConfirmar: (numero: String, nome: String) -> Boolean,
) {
    var numero by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }

    CgDialog(
        title = "Nunca bloquear",
        description = "Este número passará sempre. Fica apenas neste aparelho.",
        onDismiss = onDismiss,
        confirmText = "Adicionar",
        confirmEnabled = numero.isNotBlank(),
        onConfirm = { if (!onConfirmar(numero, nome)) erro = true },
    ) {
        Column {
            CgTextField(
                value = numero,
                onValueChange = { numero = it; erro = false },
                label = "Telefone",
                placeholder = "(11) 99999-8888",
                isError = erro,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Spacer(Modifier.height(CgSpace.md))
            CgTextField(
                value = nome,
                onValueChange = { nome = it },
                label = "Nome (opcional)",
                placeholder = "Mãe",
            )
            if (erro) {
                CgNotice(text = "Número inválido.", tone = CgNoticeTone.ERROR)
            }
        }
    }
}
