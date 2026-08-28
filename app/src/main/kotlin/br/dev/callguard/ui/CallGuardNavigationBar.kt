package br.dev.callguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgMotion
import br.dev.callguard.ui.design.CgSize
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgType

/**
 * Barra de abas do CallGuard.
 *
 * Feita à mão em vez de usar `NavigationBar`. O componente do Material desenha uma
 * cápsula tingida atrás do ícone selecionado e usa a cor primária no rótulo — é o
 * detalhe que faria a navegação continuar parecendo Material enquanto o resto do app
 * foi redesenhado.
 *
 * Aqui o selecionado é branco e o não selecionado é cinza, com um traço de 2 dp acima
 * do item ativo. A distinção não depende apenas de cor: há posição (o traço) e peso
 * tipográfico junto.
 */
@Composable
fun CallGuardNavigationBar(
    currentScreen: CallGuardScreen,
    onScreenSelected: (CallGuardScreen) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CgColor.Background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(CgSize.hairline)
                .background(CgColor.Line),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .defaultMinSize(minHeight = 64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallGuardScreen.entries.filter { it.inNavBar }.forEach { screen ->
                // O diagnóstico não tem aba própria; enquanto ele está aberto, Proteção
                // continua marcada, que é de onde se chega até ele.
                val selecionada = screen == currentScreen ||
                    (currentScreen == CallGuardScreen.DIAGNOSTICS && screen == CallGuardScreen.HOME)

                NavItem(
                    icon = screen.icon,
                    label = screen.label,
                    selected = selecionada,
                    onClick = { onScreenSelected(screen) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cor by animateColorAsState(
        targetValue = if (selected) CgColor.TextPrimary else CgColor.TextTertiary,
        animationSpec = tween(CgMotion.normal, easing = CgMotion.standard),
        label = "cor-aba",
    )
    val marcador by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(CgMotion.normal, easing = CgMotion.standard),
        label = "marcador-aba",
    )

    Column(
        modifier = modifier
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = CgColor.Inverse.copy(alpha = 0.35f)),
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = CgSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .background(CgColor.Inverse.copy(alpha = marcador)),
        )
        Spacer(Modifier.height(CgSpace.sm))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.size(CgSize.iconMd),
        )
        Spacer(Modifier.height(CgSpace.xs))
        Text(
            text = label,
            style = CgType.overline.copy(letterSpacing = 0.4.sp),
            color = cor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Ícones todos da família *outlined*.
 *
 * Misturar preenchidos com contornados é o erro mais comum numa barra de abas: o
 * preenchido pesa mais e passa a impressão de estado, competindo com o marcador que já
 * indica a aba ativa.
 */
private val CallGuardScreen.icon: ImageVector
    get() = when (this) {
        CallGuardScreen.HOME -> Icons.Outlined.Lock
        CallGuardScreen.BLOCKED_CALLS -> Icons.AutoMirrored.Outlined.List
        CallGuardScreen.RULES -> Icons.Outlined.DateRange
        CallGuardScreen.ANONYMOUS_CALL -> Icons.Outlined.Call
        CallGuardScreen.LOGS -> Icons.Outlined.Settings
        CallGuardScreen.DIAGNOSTICS -> Icons.Outlined.Info
    }
