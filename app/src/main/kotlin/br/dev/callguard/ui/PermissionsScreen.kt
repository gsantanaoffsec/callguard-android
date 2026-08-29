package br.dev.callguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.dev.callguard.core.AppPermission
import br.dev.callguard.core.PermissionCatalog
import br.dev.callguard.core.PermissionDisclosure
import br.dev.callguard.core.PermissionStatus
import br.dev.callguard.ui.design.CgCallout
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgDivider
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgPrimaryButton
import br.dev.callguard.ui.design.CgScreen
import br.dev.callguard.ui.design.CgSectionHeader
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgTag
import br.dev.callguard.ui.design.CgType

/**
 * Divulgação e concessão das autorizações, num lugar só.
 *
 * Antes cada permissão era pedida no primeiro uso do recurso que dependia dela. Isso
 * continua sendo o padrão do app e é o comportamento correto — mas deixava quem quer
 * configurar tudo de uma vez tendo que descobrir onde cada uma mora.
 *
 * A tela é a divulgação prévia: cada item diz **para que serve** e **o que se perde sem
 * ela** antes de qualquer diálogo aparecer. Só então o botão pede.
 *
 * Uma coisa que a tela não esconde: nenhum aplicativo concede permissões a si mesmo. O
 * botão dispara os diálogos oficiais em sequência; quem concede continua sendo o dono do
 * aparelho, e ele pode recusar item por item.
 */
@Composable
fun PermissionsScreen(
    statuses: Map<AppPermission, PermissionStatus>,
    sdkInt: Int,
    onGrantAll: () -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    // Sem isto o gesto de voltar sairia do app: esta tela nao tem aba propria, entao o
    // caminho de volta e a tela inicial.
    BackHandler(onBack = onBack)

    val itens = PermissionCatalog.disclosures(sdkInt)
    val pendentes = itens.count { statuses[it.id] == PermissionStatus.MISSING }
    val faltaEssencial = itens.any {
        it.essential && statuses[it.id] == PermissionStatus.MISSING
    }

    CgScreen(
        title = "Permissões",
        subtitle = "Tudo o que o CallGuard usa, por que usa, e o que acontece se você " +
            "recusar. Nada é pedido antes desta explicação.",
        onBack = onBack,
        bottomBar = bottomBar,
    ) {
        item("acao") {
            Column(Modifier.fillMaxWidth()) {
                if (pendentes == 0) {
                    CgCallout(
                        text = "Tudo o que o app usa já está concedido.",
                        color = CgColor.Positive,
                        background = CgColor.PositiveDim,
                    )
                } else {
                    CgPrimaryButton(
                        text = if (pendentes == 1) {
                            "Conceder a autorização que falta"
                        } else {
                            "Conceder as $pendentes autorizações que faltam"
                        },
                        onClick = onGrantAll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CgGap(CgSpace.md)
                    Text(
                        text = "O Android vai abrir um diálogo por vez. Você pode recusar " +
                            "qualquer uma — só a primeira da lista é indispensável.",
                        style = CgType.caption,
                        color = CgColor.TextTertiary,
                    )
                }

                if (faltaEssencial) {
                    CgGap(CgSpace.md)
                    CgCallout(
                        text = "Sem o papel de filtro de chamadas o app não bloqueia nada. " +
                            "É a única autorização sem a qual ele não tem função.",
                        color = CgColor.Warning,
                        background = CgColor.WarningDim,
                    )
                }
            }
        }

        item("pede-cabecalho") { CgSectionHeader("O que o app pede") }

        listaDeItens(itens, statuses)

        item("nota-contatos") {
            Column(Modifier.padding(top = CgSpace.xxl)) {
                CgCallout(
                    text = "Conceder o acesso à agenda não liga sozinho a regra para " +
                        "contatos salvos. Isso continua sendo o interruptor na tela " +
                        "inicial — a permissão só torna a opção possível.",
                )
            }
        }

        item("nunca-cabecalho") {
            CgSectionHeader(
                label = "O que o app nunca pede",
                description = "Uma lista do que se quer sem a lista do que não se quer não " +
                    "deixa ninguém julgar se o pedido é proporcional.",
            )
        }
        item("nunca-lista") {
            Column(Modifier.fillMaxWidth()) {
                PermissionCatalog.neverRequested.forEach { linha ->
                    Row(Modifier.fillMaxWidth().padding(vertical = CgSpace.sm)) {
                        Text(
                            text = "—",
                            style = CgType.caption,
                            color = CgColor.TextDisabled,
                            modifier = Modifier.padding(end = CgSpace.md),
                        )
                        Text(text = linha, style = CgType.caption, color = CgColor.TextSecondary)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.listaDeItens(
    itens: List<PermissionDisclosure>,
    statuses: Map<AppPermission, PermissionStatus>,
) {
    items(count = itens.size, key = { i -> "perm-${itens[i].id.name}" }) { indice ->
        val item = itens[indice]
        Column {
            ItemDePermissao(item, statuses[item.id] ?: PermissionStatus.MISSING)
            if (indice < itens.lastIndex) CgDivider()
        }
    }
}

@Composable
private fun ItemDePermissao(item: PermissionDisclosure, status: PermissionStatus) {
    Column(Modifier.fillMaxWidth().padding(vertical = CgSpace.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.title,
                style = CgType.subtitle,
                color = CgColor.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(CgSpace.md))
            EtiquetaDeStatus(status, item.essential, item.installTime)
        }

        CgGap(CgSpace.sm)
        Text(text = item.purpose, style = CgType.caption, color = CgColor.TextSecondary)

        // Um item concedido na instalação não tem "sem ela": nunca houve escolha.
        if (!item.installTime) {
            CgGap(CgSpace.sm)
            Text(
                text = "Sem ela: ${item.withoutIt}",
                style = CgType.caption,
                color = CgColor.TextTertiary,
            )
        }
    }
}

@Composable
private fun EtiquetaDeStatus(
    status: PermissionStatus,
    essencial: Boolean,
    installTime: Boolean,
) {
    when {
        installTime -> CgTag(
            text = "automática",
            color = CgColor.TextTertiary,
            background = CgColor.Surface,
        )

        status == PermissionStatus.GRANTED -> CgTag(
            text = "concedida",
            color = CgColor.Positive,
            background = CgColor.PositiveDim,
        )

        status == PermissionStatus.NOT_APPLICABLE -> CgTag(
            text = "não se aplica",
            color = CgColor.TextTertiary,
            background = CgColor.Surface,
        )

        essencial -> CgTag(
            text = "necessária",
            color = CgColor.Negative,
            background = CgColor.NegativeDim,
        )

        else -> CgTag(
            text = "opcional",
            color = CgColor.Warning,
            background = CgColor.WarningDim,
        )
    }
}
