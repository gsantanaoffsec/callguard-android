package br.dev.callguard.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Um campo que mostra o valor atual e abre as opções ao ser tocado.
 *
 * Substitui as fileiras de chips. O motivo não é só estético: cinco chips de "intervalo"
 * ocupavam duas linhas, empurravam o resto da tela para baixo e mostravam quatro valores
 * que a pessoa não escolheu para exibir o único que ela escolheu. Um campo mostra a
 * escolha e esconde o resto até ser perguntado — que é a proporção certa entre o estado e
 * as alternativas.
 *
 * Também é o que torna possível oferecer um valor **personalizado**: uma fileira de chips
 * não tem onde encaixar "qualquer número de horas".
 */
@Composable
fun CgPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        // Rotulo DENTRO da superficie tocavel, e nao acima dela. Duas razoes:
        //
        //  - acessibilidade: com o rotulo fora, o no clicavel carregava so o valor, e o
        //    leitor de tela anunciava "3 chamadas, botao" -- a resposta sem a pergunta;
        //  - alvo: a linha inteira passa a ser tocavel, em vez de so a caixa de baixo.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CgShape.medium)
                .background(CgColor.Surface)
                .border(CgSize.hairline, CgColor.LineStrong, CgShape.medium)
                .cgClickable(enabled = enabled, onClick = onClick)
                .defaultMinSize(minHeight = CgSize.fieldHeight)
                .padding(horizontal = CgSpace.lg, vertical = CgSpace.md),
        ) {
            Text(
                text = label.uppercase(),
                style = CgType.overline,
                color = CgColor.TextTertiary,
            )
            Spacer(Modifier.height(CgSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = CgType.subtitle,
                    color = if (enabled) CgColor.TextPrimary else CgColor.TextDisabled,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = CgColor.TextTertiary,
                    modifier = Modifier.size(CgSize.iconMd),
                )
            }
        }

        if (description != null) {
            Spacer(Modifier.height(CgSpace.sm))
            Text(text = description, style = CgType.caption, color = CgColor.TextTertiary)
        }
    }
}

/**
 * A folha que sobe com as opções.
 *
 * `ModalBottomSheet` do Material com tudo recolorido. Mantido em vez de um diálogo
 * próprio porque o comportamento de arrastar, o recuo pelo gesto e o respeito às barras
 * do sistema já vêm resolvidos e corretos — reimplementar isso à mão seria refazer pior.
 * O que veio pronto e destoava (o container tonal, a alça cinza-claro) foi trocado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgOptionSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = CgColor.SurfaceRaised,
        contentColor = CgColor.TextPrimary,
        scrimColor = CgColor.Background.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            // Um traço fino em vez da alça larga do Material, que puxa atenção demais
            // para um elemento que só existe para dizer "isto arrasta".
            Box(Modifier.fillMaxWidth().padding(vertical = CgSpace.md)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CgShape.pill)
                        .background(CgColor.LineStrong),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Rola porque as listas longas existem: 24 horas do dia nao cabem numa
                // tela, e sem isto as ultimas opcoes ficariam inalcancaveis.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CgSpace.xl)
                .padding(bottom = CgSpace.xxl),
        ) {
            Text(text = title, style = CgType.title, color = CgColor.TextPrimary)
            if (description != null) {
                Spacer(Modifier.height(CgSpace.sm))
                Text(text = description, style = CgType.caption, color = CgColor.TextSecondary)
            }
            Spacer(Modifier.height(CgSpace.lg))
            content()
            // As barras do sistema por baixo da folha continuam sendo espaço ocupado.
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/**
 * Uma opção dentro da folha.
 *
 * A marca de seleção fica à direita e é um símbolo, não só uma cor de fundo: quem não
 * distingue matiz continua vendo qual está escolhida.
 */
@Composable
fun CgOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CgShape.small)
            .cgClickable(role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = CgSize.touchMin)
            .padding(vertical = CgSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                style = CgType.subtitle,
                color = if (selected) CgColor.TextPrimary else CgColor.TextSecondary,
            )
            if (supporting != null) {
                Spacer(Modifier.height(CgSpace.xxs))
                Text(text = supporting, style = CgType.caption, color = CgColor.TextTertiary)
            }
        }
        if (selected) {
            Spacer(Modifier.width(CgSpace.md))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selecionado",
                tint = CgColor.TextPrimary,
                modifier = Modifier.size(CgSize.iconMd),
            )
        }
    }
}

/**
 * Contador de − e +.
 *
 * Escolhido em vez de um campo numérico porque o valor é pequeno e limitado: abrir o
 * teclado para digitar "3" e ainda ter que fechá-lo é mais trabalho do que dois toques,
 * e um campo aceita "999" que depois precisa ser corrigido em silêncio.
 */
@Composable
fun CgStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BotaoDePasso(
            texto = "−",
            enabled = value > range.first,
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            descricao = "Diminuir",
        )
        Text(
            text = format(value),
            style = CgType.title,
            color = CgColor.TextPrimary,
        )
        BotaoDePasso(
            texto = "+",
            enabled = value < range.last,
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            descricao = "Aumentar",
        )
    }
}

@Composable
private fun BotaoDePasso(
    texto: String,
    enabled: Boolean,
    onClick: () -> Unit,
    descricao: String,
) {
    Box(
        modifier = Modifier
            .size(CgSize.touchMin)
            .clip(CgShape.pill)
            .border(
                CgSize.hairline,
                if (enabled) CgColor.LineStrong else CgColor.Line,
                CgShape.pill,
            )
            .cgClickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = CgType.title,
            color = if (enabled) CgColor.TextPrimary else CgColor.TextDisabled,
        )
    }
}
