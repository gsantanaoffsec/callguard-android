package br.dev.callguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.dev.callguard.core.PhoneNumberMasker
import br.dev.callguard.core.PhoneOrigin
import br.dev.callguard.data.db.BlockedCallEntity
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgDialog
import br.dev.callguard.ui.design.CgDivider
import br.dev.callguard.ui.design.CgEmptyState
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgIconButton
import br.dev.callguard.ui.design.CgListItem
import br.dev.callguard.ui.design.CgMetric
import br.dev.callguard.ui.design.CgScreen
import br.dev.callguard.ui.design.CgSectionHeader
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgRevealRow
import br.dev.callguard.ui.design.CgSize
import br.dev.callguard.ui.design.CgTag
import br.dev.callguard.ui.design.CgTextAction
import br.dev.callguard.ui.design.CgType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATA_HORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", Locale.forLanguageTag("pt-BR"))

/**
 * Histórico do que foi recusado.
 *
 * Uma lista de linhas, não de cartões. Cada linha entrega, nesta ordem: o número, quando
 * foi, e o que fazer a respeito. A leitura vertical fica contínua — que é o ponto de um
 * histórico, e o que a pilha de cartões anterior impedia.
 */
@Composable
fun BlockedCallsScreen(
    blockedCalls: List<BlockedCallEntity>,
    allowlistedNumbers: Set<String>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    onAllowlistNumber: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    // Voltar leva para a aba principal, não para fora do app.
    BackHandler(onBack = onBack)

    // Números ficam mascarados por padrão; revelar é uma escolha consciente e vale só
    // enquanto a tela está aberta.
    var revelarNumeros by remember { mutableStateOf(false) }
    var confirmandoLimpeza by remember { mutableStateOf(false) }

    CgScreen(
        title = "Bloqueadas",
        bottomBar = bottomBar,
        actions = {
            CgIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Limpar histórico",
                enabled = blockedCalls.isNotEmpty(),
                tint = CgColor.TextSecondary,
                onClick = { confirmandoLimpeza = true },
            )
        },
    ) {
        if (blockedCalls.isEmpty()) {
            item("vazio") {
                CgEmptyState(
                    icon = Icons.Outlined.Lock,
                    title = "Nenhuma chamada bloqueada",
                    description = "Quando alguém passar do limite, a ligação aparece aqui.",
                )
            }
            return@CgScreen
        }

        item("resumo") {
            Column(Modifier.fillMaxWidth()) {
                CgMetric(
                    value = blockedCalls.size.toString(),
                    label = if (blockedCalls.size == 1) "chamada recusada" else "chamadas recusadas",
                )
                CgGap(CgSpace.xxl)
                CgRevealRow(revealed = revelarNumeros, onChange = { revelarNumeros = it })
                CgDivider()
            }
        }

        items(
            count = blockedCalls.size,
            key = { indice -> blockedCalls[indice].id },
        ) { indice ->
            val bloqueada = blockedCalls[indice]
            Column {
                ItemBloqueado(
                    bloqueada = bloqueada,
                    revelar = revelarNumeros,
                    jaPermitido = bloqueada.normalizedNumber in allowlistedNumbers,
                    onPermitir = { onAllowlistNumber(bloqueada.normalizedNumber) },
                )
                if (indice < blockedCalls.lastIndex) CgDivider()
            }
        }
    }

    if (confirmandoLimpeza) {
        CgDialog(
            title = "Apagar o histórico?",
            description = "As ${blockedCalls.size} chamadas listadas aqui são removidas. " +
                "As regras e as listas de exceção não mudam.",
            onDismiss = { confirmandoLimpeza = false },
            confirmText = "Apagar",
            destructive = true,
            onConfirm = {
                confirmandoLimpeza = false
                onClearHistory()
            },
        )
    }
}

/**
 * Uma chamada recusada.
 *
 * O número em monoespaçada para que colunas de dígitos fiquem alinhadas na vertical e a
 * lista seja escaneável. A cor entra só na etiqueta e no motivo — o resto da hierarquia
 * é tipográfica, senão a lista inteira vira vermelha e nada mais se destaca.
 */
@Composable
private fun ItemBloqueado(
    bloqueada: BlockedCallEntity,
    revelar: Boolean,
    jaPermitido: Boolean,
    onPermitir: () -> Unit,
) {
    val origem = PhoneOrigin.of(bloqueada.normalizedNumber)
    val quando = DATA_HORA.format(
        Instant.ofEpochMilli(bloqueada.blockedAt).atZone(ZoneId.systemDefault()),
    )
    val procedencia = origem.region?.let { " · $it" } ?: ""

    Column(Modifier.padding(vertical = CgSpace.md)) {
        CgListItem(
            title = if (revelar) {
                bloqueada.normalizedNumber
            } else {
                PhoneNumberMasker.mask(bloqueada.normalizedNumber)
            },
            titleStyle = CgType.monoStrong,
            subtitle = "$quando$procedencia",
            meta = "${bloqueada.attemptsInWindow} tentativas na janela",
            trailing = {
                CgTag(
                    text = "recusada",
                    color = CgColor.Negative,
                    background = CgColor.NegativeDim,
                )
            },
        )

        // Consertar um bloqueio errado no momento em que ele é visto: sem isto o usuário
        // teria que redigitar o número na tela principal.
        if (jaPermitido) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = CgColor.Positive,
                    modifier = Modifier.size(CgSize.iconSm),
                )
                Spacer(Modifier.width(CgSpace.sm))
                Text(
                    text = "Na lista de permitidos — não será mais bloqueado",
                    style = CgType.caption,
                    color = CgColor.Positive,
                )
            }
        } else {
            CgTextAction(
                text = "Nunca bloquear este número",
                onClick = onPermitir,
                color = CgColor.TextSecondary,
            )
        }
    }
}
