package br.dev.callguard.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.core.WindowFormat
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgDivider
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgOptionRow
import br.dev.callguard.ui.design.CgOptionSheet
import br.dev.callguard.ui.design.CgPrimaryButton
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgStepper
import br.dev.callguard.ui.design.CgType

/**
 * Folhas de escolha usadas na tela inicial e nas regras.
 *
 * Ficam num arquivo só porque a mesma pergunta — "quantas chamadas?" e "em quanto
 * tempo?" — aparece em três lugares: a regra geral, o modo noturno e a regra por número.
 * Três cópias divergiriam na primeira vez que uma opção fosse adicionada.
 */

/** Quantas chamadas passam antes de a seguinte ser recusada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimiteDeChamadasSheet(
    atual: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    CgOptionSheet(
        title = "Chamadas permitidas",
        description = "A chamada seguinte a este número, dentro da janela, é recusada.",
        onDismiss = onDismiss,
    ) {
        ProtectionSettings.MAX_CALL_OPTIONS.forEach { opcao ->
            CgOptionRow(
                text = if (opcao == 1) "1 chamada" else "$opcao chamadas",
                supporting = "a ${opcao + 1}ª é recusada",
                selected = opcao == atual,
                onClick = {
                    onSelect(opcao)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Tamanho da janela deslizante.
 *
 * Além das opções prontas, aceita um valor **em horas** escolhido pela pessoa. Foi o que
 * motivou trocar a fileira de chips por uma folha: uma fileira não tem onde encaixar
 * "qualquer número de horas" sem virar uma fileira infinita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JanelaDeTempoSheet(
    atualEmMinutos: Int,
    onSelect: (minutos: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ehPersonalizado = WindowFormat.isCustom(
        atualEmMinutos,
        ProtectionSettings.WINDOW_MINUTE_OPTIONS,
    )
    // O contador começa no valor em vigor quando ele já é personalizado; caso contrário,
    // numa hora — abrir em "1 h" evita que o primeiro toque tenha que desfazer um padrão
    // arbitrário.
    var horas by remember {
        mutableIntStateOf(
            if (ehPersonalizado) WindowFormat.wholeHours(atualEmMinutos) else 1,
        )
    }
    var mostrandoPersonalizado by remember { mutableStateOf(ehPersonalizado) }

    CgOptionSheet(
        title = "Dentro de",
        description = "Janela deslizante: contam só as ligações recebidas neste intervalo. " +
            "Quem para de ligar volta a passar sozinho.",
        onDismiss = onDismiss,
    ) {
        ProtectionSettings.WINDOW_MINUTE_OPTIONS.forEach { minutos ->
            CgOptionRow(
                text = WindowFormat.short(minutos),
                supporting = WindowFormat.long(minutos),
                selected = !mostrandoPersonalizado && minutos == atualEmMinutos,
                onClick = {
                    onSelect(minutos)
                    onDismiss()
                },
            )
        }

        CgGap(CgSpace.sm)
        CgDivider()
        CgGap(CgSpace.lg)

        CgOptionRow(
            text = "Personalizado, em horas",
            supporting = if (ehPersonalizado) {
                "em uso: ${WindowFormat.long(atualEmMinutos)}"
            } else {
                "de 1 a 24 horas"
            },
            selected = mostrandoPersonalizado,
            onClick = { mostrandoPersonalizado = !mostrandoPersonalizado },
        )

        if (mostrandoPersonalizado) {
            CgGap(CgSpace.lg)
            CgStepper(
                value = horas,
                onValueChange = { horas = it },
                range = 1..24,
                format = { if (it == 1) "1 hora" else "$it horas" },
            )
            CgGap(CgSpace.lg)
            Text(
                text = "Alguém precisaria ligar mais que o limite dentro de " +
                    "${WindowFormat.long(horas * 60)} para ser recusado.",
                style = CgType.caption,
                color = CgColor.TextTertiary,
            )
            CgGap(CgSpace.lg)
            CgPrimaryButton(
                text = "Usar ${WindowFormat.short(horas * 60)}",
                onClick = {
                    onSelect(horas * 60)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(CgSpace.sm))
    }
}

/**
 * Hora do dia, para o início e o fim do modo noturno.
 *
 * Era o pior caso da fileira de chips: vinte e quatro botões de dois dígitos ocupando
 * cinco linhas, duas vezes na mesma tela. Numa folha eles viram uma lista que rola, e o
 * campo mostra só a hora escolhida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoraDoDiaSheet(
    titulo: String,
    minutoDoDiaAtual: Int,
    onSelect: (minutoDoDia: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val horaAtual = minutoDoDiaAtual / 60
    CgOptionSheet(title = titulo, onDismiss = onDismiss) {
        (0..23).forEach { hora ->
            CgOptionRow(
                text = "%02d:00".format(hora),
                selected = hora == horaAtual,
                onClick = {
                    onSelect(hora * 60)
                    onDismiss()
                },
            )
        }
    }
}
