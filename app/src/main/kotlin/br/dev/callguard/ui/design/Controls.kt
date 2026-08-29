package br.dev.callguard.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Toque padrão do app.
 *
 * Um brilho branco muito fraco em vez do ripple colorido do Material — em tela preta,
 * o ripple tingido pelo `primary` é o detalhe que mais denuncia "app Compose com tema
 * escuro". Este ainda é um ripple de verdade, então a resposta ao toque continua
 * acessível e previsível.
 */
@Composable
fun Modifier.cgClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    // Recebida de fora quando quem chama tambem precisa reagir ao toque -- e o caso dos
    // botoes, que encolhem sob o dedo. Duas fontes separadas dariam dois estados de
    // pressao para o mesmo toque.
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val interacao = interactionSource ?: remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interacao,
        indication = ripple(color = CgColor.Inverse.copy(alpha = 0.35f)),
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

// ---------------------------------------------------------------- botões

/**
 * Ação principal: branco sólido sobre preto.
 *
 * A inversão total de contraste é o que faz a ação principal ser encontrada sem precisar
 * de cor de marca. É o botão mais forte do app — no máximo um por tela.
 */
@Composable
fun CgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val fundo = if (enabled) CgColor.Inverse else CgColor.SurfaceHigh
    val conteudo = if (enabled) CgColor.OnInverse else CgColor.TextDisabled
    CgButtonSurface(
        modifier = modifier,
        background = fundo,
        border = null,
        enabled = enabled,
        onClick = onClick,
    ) {
        CgButtonContent(text = text, icon = icon, color = conteudo)
    }
}

/** Ação secundária: só contorno. Presente, sem competir com a principal. */
@Composable
fun CgSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val conteudo = if (enabled) CgColor.TextPrimary else CgColor.TextDisabled
    CgButtonSurface(
        modifier = modifier,
        background = Color.Transparent,
        border = BorderStroke(CgSize.hairline, if (enabled) CgColor.LineStrong else CgColor.Line),
        enabled = enabled,
        onClick = onClick,
    ) {
        CgButtonContent(text = text, icon = icon, color = conteudo)
    }
}

/**
 * Ação destrutiva.
 *
 * Texto vermelho sobre contorno vermelho apagado — não um retângulo vermelho sólido.
 * Apagar histórico é reversível o bastante para não merecer o peso visual de um alarme.
 */
@Composable
fun CgDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CgButtonSurface(
        modifier = modifier,
        background = Color.Transparent,
        border = BorderStroke(
            CgSize.hairline,
            if (enabled) CgColor.Negative.copy(alpha = 0.45f) else CgColor.Line,
        ),
        enabled = enabled,
        onClick = onClick,
    ) {
        CgButtonContent(
            text = text,
            icon = null,
            color = if (enabled) CgColor.Negative else CgColor.TextDisabled,
        )
    }
}

/** Ação terciária, em linha com o texto. Sem caixa em volta. */
@Composable
fun CgTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = CgColor.TextPrimary,
) {
    Row(
        modifier = modifier
            .clip(CgShape.small)
            .cgClickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = CgSize.touchMin)
            .padding(vertical = CgSpace.sm, horizontal = CgSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else CgColor.TextDisabled,
                modifier = Modifier.size(CgSize.iconMd),
            )
            Spacer(Modifier.width(CgSpace.sm))
        }
        Text(
            text = text,
            style = CgType.action,
            color = if (enabled) color else CgColor.TextDisabled,
        )
    }
}

@Composable
private fun CgButtonSurface(
    modifier: Modifier,
    background: Color,
    border: BorderStroke?,
    enabled: Boolean,
    shape: Shape = CgShape.medium,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interacao = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            // A escala vem antes do recorte para que a superficie inteira ceda junto,
            // e nao so o conteudo dentro dela.
            .cgPressScale(interacao, enabled)
            // Altura MINIMA, nao fixa: com a fonte do sistema ampliada, uma altura fixa
            // corta o rotulo do botao. Crescer e a resposta certa.
            .defaultMinSize(minHeight = CgSize.buttonHeight)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .cgClickable(enabled = enabled, interactionSource = interacao, onClick = onClick)
            .padding(horizontal = CgSpace.xl),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CgButtonContent(text: String, icon: ImageVector?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(CgSize.iconMd),
            )
            Spacer(Modifier.width(CgSpace.sm))
        }
        Text(text = text, style = CgType.action, color = color, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- switch

/**
 * Interruptor desenhado do zero.
 *
 * O `Switch` do Material sinaliza o estado ligado com a cor primária e um ícone de
 * confirmação dentro do polegar. Aqui o estado é dito por **contraste e posição**:
 * ligado é trilho branco com polegar preto; desligado é trilho vazio com contorno e
 * polegar cinza. Funciona em preto e branco e continua legível para quem não distingue
 * cores — a diferença nunca depende só de matiz.
 */
@Composable
fun CgSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val duracao = tween<Color>(CgMotion.normal, easing = CgMotion.standard)

    val trilho by animateColorAsState(
        targetValue = when {
            !enabled -> CgColor.SurfaceHigh
            checked -> CgColor.Inverse
            else -> Color.Transparent
        },
        animationSpec = duracao,
        label = "trilho",
    )
    val contorno by animateColorAsState(
        targetValue = when {
            !enabled -> CgColor.Line
            checked -> CgColor.Inverse
            else -> CgColor.LineStrong
        },
        animationSpec = duracao,
        label = "contorno",
    )
    val polegar by animateColorAsState(
        targetValue = when {
            !enabled -> CgColor.TextDisabled
            checked -> CgColor.OnInverse
            else -> CgColor.TextSecondary
        },
        animationSpec = duracao,
        label = "polegar",
    )
    val deslocamento by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(CgMotion.normal, easing = CgMotion.standard),
        label = "deslocamento",
    )

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CgShape.pill)
            .background(trilho)
            .border(CgSize.hairline, contorno, CgShape.pill)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = CgColor.Inverse.copy(alpha = 0.35f)),
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = deslocamento)
                .size(20.dp)
                .clip(CircleShape)
                .background(polegar),
        )
    }
}

/**
 * Linha com rótulo, explicação e interruptor.
 *
 * O padrão mais repetido do app. A linha inteira é tocável: obrigar o usuário a acertar
 * um alvo de 44 dp na borda da tela quando existe uma linha de 56 dp disponível é
 * desenho preguiçoso.
 */
@Composable
fun CgSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // `toggleable` carrega o estado ligado/desligado para a acessibilidade; um
            // `clickable` com Role.Switch anuncia o controle mas nao diz como ele esta.
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = CgColor.Inverse.copy(alpha = 0.35f)),
                onValueChange = onCheckedChange,
            )
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = CgSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = CgType.subtitle,
                color = if (enabled) CgColor.TextPrimary else CgColor.TextDisabled,
            )
            if (description != null) {
                Spacer(Modifier.height(CgSpace.xs))
                Text(
                    text = description,
                    style = CgType.caption,
                    color = if (enabled) CgColor.TextSecondary else CgColor.TextDisabled,
                )
            }
        }
        Spacer(Modifier.width(CgSpace.lg))
        // Sem callback próprio: quem trata o toque é a linha, para não haver dois alvos.
        CgSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

// ---------------------------------------------------------------- escolhas

/**
 * Grupo de escolha única.
 *
 * Substitui o `FilterChip`, que vinha com o ícone de confirmação e o contêiner tingido
 * do Material. Aqui o selecionado é simplesmente o inverso do fundo — mesma lógica do
 * botão primário, o que mantém uma linguagem só para "isto está ativo".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> CgChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.sm),
        verticalArrangement = Arrangement.spacedBy(CgSpace.sm),
    ) {
        options.forEach { opcao ->
            CgChoiceChip(
                text = label(opcao),
                selected = opcao == selected,
                onClick = { onSelected(opcao) },
            )
        }
    }
}

@Composable
fun CgChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animacao = tween<Color>(CgMotion.fast, easing = CgMotion.standard)
    val fundo by animateColorAsState(
        targetValue = if (selected) CgColor.Inverse else Color.Transparent,
        animationSpec = animacao,
        label = "chip-fundo",
    )
    val texto by animateColorAsState(
        targetValue = if (selected) CgColor.OnInverse else CgColor.TextSecondary,
        animationSpec = animacao,
        label = "chip-texto",
    )
    val borda by animateColorAsState(
        targetValue = if (selected) CgColor.Inverse else CgColor.LineStrong,
        animationSpec = animacao,
        label = "chip-borda",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = CgSize.chipHeight)
            .clip(CgShape.pill)
            .background(fundo)
            .border(CgSize.hairline, borda, CgShape.pill)
            // `selectable` e nao `clickable`: e o que faz o leitor de tela anunciar
            // "selecionado" em vez de apenas "botao".
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = CgColor.Inverse.copy(alpha = 0.35f)),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = CgSpace.lg, vertical = CgSpace.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = CgType.action, color = texto, maxLines = 1)
    }
}

// ---------------------------------------------------------------- campos

/**
 * Campo de texto.
 *
 * `OutlinedTextField` com todas as cores substituídas. O contorno do Material acende no
 * `primary` ao focar; aqui ele acende em branco, que é o mesmo vocabulário de foco do
 * resto do app.
 */
@Composable
fun CgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = CgType.caption) },
        placeholder = placeholder?.let { { Text(it, style = CgType.body, color = CgColor.TextDisabled) } },
        singleLine = true,
        isError = isError,
        shape = CgShape.medium,
        textStyle = LocalTextStyle.current.merge(CgType.body),
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CgColor.TextPrimary,
            unfocusedTextColor = CgColor.TextPrimary,
            focusedContainerColor = CgColor.Surface,
            unfocusedContainerColor = CgColor.Surface,
            errorContainerColor = CgColor.Surface,
            cursorColor = CgColor.Inverse,
            focusedBorderColor = CgColor.Inverse,
            unfocusedBorderColor = CgColor.LineStrong,
            errorBorderColor = CgColor.Negative,
            focusedLabelColor = CgColor.TextSecondary,
            unfocusedLabelColor = CgColor.TextTertiary,
            errorLabelColor = CgColor.Negative,
        ),
    )
}
