package br.dev.callguard.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Item de lista do CallGuard.
 *
 * Substitui os cartões individuais que o app usava para cada número, cada regra e cada
 * chamada bloqueada. Uma lista de cartões empilhados cria uma borda a cada 60 dp e
 * transforma a rolagem numa escada; uma lista de linhas com divisor de 1 dp é lida de
 * cima a baixo sem esforço — que é o que se espera de um histórico.
 *
 * Estrutura fixa: informação principal, informação secundária, e estado ou ação à
 * direita.
 */
@Composable
fun CgListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    titleStyle: TextStyle = CgType.subtitle,
    titleColor: Color = CgColor.TextPrimary,
    metaColor: Color = CgColor.TextTertiary,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.cgClickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = CgSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(CgSpace.lg))
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = titleStyle, color = titleColor)
            if (subtitle != null) {
                Spacer(Modifier.height(CgSpace.xs))
                Text(text = subtitle, style = CgType.caption, color = CgColor.TextSecondary)
            }
            if (meta != null) {
                Spacer(Modifier.height(CgSpace.xxs))
                Text(text = meta, style = CgType.caption, color = metaColor)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(CgSpace.md))
            trailing()
        }
    }
}

/** Linha que leva a outra tela. Chevron discreto, sem cartão em volta. */
@Composable
fun CgNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
) {
    CgListItem(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(text = value, style = CgType.body, color = CgColor.TextSecondary)
                    Spacer(Modifier.width(CgSpace.sm))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CgColor.TextTertiary,
                    modifier = Modifier.size(CgSize.iconMd),
                )
            }
        },
    )
}

/**
 * Par rótulo/valor.
 *
 * Para dados técnicos — procedência, versão do banco, contagens. O valor em monoespaçada
 * porque números alinhados verticalmente são comparáveis de relance; em fonte
 * proporcional, não são.
 */
@Composable
fun CgDataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = CgColor.TextPrimary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CgSpace.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = CgType.caption,
            color = CgColor.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(CgSpace.lg))
        Text(
            text = value,
            style = CgType.mono,
            color = valueColor,
            modifier = Modifier.weight(1.3f),
        )
    }
}

/**
 * Etiqueta de estado.
 *
 * Texto curto em caixa alta sobre fundo esmaecido. Carrega significado sem virar o
 * "carnaval de cores" que uma lista de histórico com blocos coloridos vira.
 */
@Composable
fun CgTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CgColor.TextSecondary,
    background: Color = CgColor.SurfaceRaised,
) {
    Box(
        modifier = modifier
            .clip(CgShape.small)
            .background(background)
            .padding(horizontal = CgSpace.sm, vertical = 5.dp),
    ) {
        Text(text = text.uppercase(), style = CgType.overline, color = color)
    }
}

/** Ponto colorido de 8 dp, para diferenciar estado sem depender de texto colorido. */
@Composable
fun CgDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Ícone pequeno usado como marcador de item, com alvo visual consistente. */
@Composable
fun CgLeadingIcon(icon: ImageVector, tint: Color = CgColor.TextSecondary) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(CgSize.iconMd),
    )
}
