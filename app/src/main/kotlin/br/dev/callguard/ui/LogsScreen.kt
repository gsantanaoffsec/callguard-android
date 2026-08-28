package br.dev.callguard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.dev.callguard.core.PhoneOrigin
import br.dev.callguard.data.ScreeningLogRepository
import br.dev.callguard.data.db.ScreeningEventEntity
import br.dev.callguard.ui.design.CgCallout
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgDataRow
import br.dev.callguard.ui.design.CgDialog
import br.dev.callguard.ui.design.CgDivider
import br.dev.callguard.ui.design.CgEmptyState
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgIconButton
import br.dev.callguard.ui.design.CgListItem
import br.dev.callguard.ui.design.CgNotice
import br.dev.callguard.ui.design.CgNoticeTone
import br.dev.callguard.ui.design.CgPrimaryButton
import br.dev.callguard.ui.design.CgRevealRow
import br.dev.callguard.ui.design.CgScreen
import br.dev.callguard.ui.design.CgSecondaryButton
import br.dev.callguard.ui.design.CgSectionHeader
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgTag
import br.dev.callguard.ui.design.CgType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATA_HORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm:ss", Locale.forLanguageTag("pt-BR"))

/**
 * Registro de decisões.
 *
 * Cada entrada é uma linha com o essencial — número, horário, veredito — e os dados
 * técnicos aparecem embaixo em pares rótulo/valor. Antes cada evento era um cartão com
 * seis linhas dentro; era informação demais competindo entre si para uma tela cuja
 * função é ser escaneada.
 */
@Composable
fun LogsScreen(
    events: List<ScreeningEventEntity>,
    friendlyPath: String,
    statusMessage: String?,
    hasCrashReport: Boolean,
    crashReportPath: String,
    onOpenCrashReport: () -> Unit,
    onShareCrashReport: () -> Unit,
    onClearCrashReport: () -> Unit,
    onGenerateAndOpen: () -> Unit,
    onGenerateAndShare: () -> Unit,
    onRefreshFile: () -> Unit,
    onClear: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var revelarNumeros by remember { mutableStateOf(false) }
    var confirmandoLimpeza by remember { mutableStateOf(false) }

    CgScreen(
        title = "Registro",
        subtitle = "Toda decisão do filtro, na ordem em que aconteceu.",
        bottomBar = bottomBar,
        actions = {
            CgIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Atualizar arquivo",
                tint = CgColor.TextSecondary,
                onClick = onRefreshFile,
            )
            CgIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Limpar registros",
                enabled = events.isNotEmpty(),
                tint = CgColor.TextSecondary,
                onClick = { confirmandoLimpeza = true },
            )
        },
    ) {
        if (hasCrashReport) {
            item("falha") {
                RelatorioDeFalha(
                    caminho = crashReportPath,
                    onAbrir = onOpenCrashReport,
                    onEnviar = onShareCrashReport,
                    onApagar = onClearCrashReport,
                )
            }
        }

        item("arquivo") {
            Column(Modifier.fillMaxWidth()) {
                CgSectionHeader(label = "Arquivo no celular", top = true)
                Text(text = friendlyPath, style = CgType.mono, color = CgColor.TextSecondary)
                CgGap(CgSpace.md)
                Text(
                    text = "Texto comum, escrito para ser lido por gente. Fica só neste " +
                        "aparelho e guarda os ${ScreeningLogRepository.MAX_EVENTS_KEPT} " +
                        "registros mais recentes.",
                    style = CgType.caption,
                    color = CgColor.TextTertiary,
                )
                CgGap(CgSpace.xl)
                CgPrimaryButton(
                    text = "Gerar e abrir",
                    onClick = onGenerateAndOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
                CgGap(CgSpace.sm)
                CgSecondaryButton(
                    text = "Enviar para outro app",
                    icon = Icons.Default.Share,
                    onClick = onGenerateAndShare,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (statusMessage != null) {
                    CgNotice(text = statusMessage, tone = CgNoticeTone.POSITIVE)
                }
            }
        }

        item("nota-operadora") {
            Column(Modifier.padding(top = CgSpace.xxl)) {
                CgCallout(
                    text = "A operadora do número não aparece aqui. Com a portabilidade o " +
                        "prefixo deixou de indicá-la, e descobrir a atual exigiria consulta " +
                        "pela internet — que este app não faz. Melhor não mostrar do que " +
                        "mostrar errado.",
                )
            }
        }

        if (events.isEmpty()) {
            item("vazio") {
                CgEmptyState(
                    icon = Icons.Outlined.Info,
                    title = "Nenhuma chamada analisada",
                    description = "As decisões aparecem aqui assim que a primeira ligação " +
                        "passar pelo filtro.",
                )
            }
            return@CgScreen
        }

        item("cabecalho-eventos") {
            Column {
                CgSectionHeader(
                    label = "${events.size} decisões",
                )
                CgRevealRow(revealed = revelarNumeros, onChange = { revelarNumeros = it })
                CgDivider()
            }
        }

        items(count = events.size, key = { indice -> events[indice].id }) { indice ->
            Column {
                EntradaDeRegistro(evento = events[indice], revelar = revelarNumeros)
                if (indice < events.lastIndex) CgDivider()
            }
        }
    }

    if (confirmandoLimpeza) {
        CgDialog(
            title = "Apagar os registros?",
            description = "As ${events.size} decisões guardadas são removidas, e o arquivo " +
                "é regravado vazio. As regras não mudam.",
            onDismiss = { confirmandoLimpeza = false },
            confirmText = "Apagar",
            destructive = true,
            onConfirm = {
                confirmandoLimpeza = false
                onClear()
            },
        )
    }
}

@Composable
private fun EntradaDeRegistro(evento: ScreeningEventEntity, revelar: Boolean) {
    val origem = PhoneOrigin.of(evento.normalizedNumber)

    Column(Modifier.padding(vertical = CgSpace.md)) {
        CgListItem(
            title = ScreeningLogRepository.maskIfNeeded(evento.normalizedNumber, revelar),
            titleStyle = CgType.monoStrong,
            subtitle = DATA_HORA.format(
                Instant.ofEpochMilli(evento.occurredAt).atZone(ZoneId.systemDefault()),
            ),
            trailing = {
                // Etiqueta com peso diferente para cada estado: "recusada" é o evento
                // digno de nota, "permitida" é o normal e não deve gritar.
                if (evento.blocked) {
                    CgTag(
                        text = "recusada",
                        color = CgColor.Negative,
                        background = CgColor.NegativeDim,
                    )
                } else {
                    CgTag(
                        text = "permitida",
                        color = CgColor.TextTertiary,
                        background = CgColor.Surface,
                    )
                }
            },
        )

        CgDataRow("Procedência", origem.describe())
        origem.areaCode?.let { CgDataRow("DDD", it) }
        CgDataRow(
            label = "Verificação da rede",
            value = ScreeningLogRepository.describeVerification(evento.verificationStatus),
        )
        CgDataRow("Motivo", ScreeningLogRepository.translateReason(evento.reason))
        if (evento.attemptsInWindow > 0) {
            CgDataRow("Tentativas na janela", evento.attemptsInWindow.toString())
        }
    }
}

/**
 * Aviso de que uma falha foi registrada.
 *
 * Fica no topo da aba porque, quando existe, e a informacao mais importante da tela --
 * mais do que qualquer decisao de filtragem. O app nao envia nada sozinho: o arquivo so
 * sai daqui se a pessoa mandar.
 */
@Composable
private fun RelatorioDeFalha(
    caminho: String,
    onAbrir: () -> Unit,
    onEnviar: () -> Unit,
    onApagar: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        CgSectionHeader(label = "Falha registrada", top = true)
        Text(
            text = "O app fechou sozinho em algum momento e o motivo tecnico ficou gravado " +
                "neste arquivo. Ele nao contem numero de telefone -- so nomes de classe e " +
                "linha. Nada e enviado automaticamente.",
            style = CgType.caption,
            color = CgColor.TextSecondary,
        )
        CgGap(CgSpace.md)
        Text(text = caminho, style = CgType.mono, color = CgColor.TextTertiary)
        CgGap(CgSpace.xl)
        CgPrimaryButton(
            text = "Enviar o relatorio",
            icon = Icons.Default.Share,
            onClick = onEnviar,
            modifier = Modifier.fillMaxWidth(),
        )
        CgGap(CgSpace.sm)
        Row(Modifier.fillMaxWidth()) {
            CgSecondaryButton(text = "Abrir", onClick = onAbrir, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(CgSpace.md))
            CgSecondaryButton(text = "Apagar", onClick = onApagar, modifier = Modifier.weight(1f))
        }
    }
}
