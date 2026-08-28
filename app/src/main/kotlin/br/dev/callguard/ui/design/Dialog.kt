package br.dev.callguard.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Diálogo do CallGuard.
 *
 * Construído sobre `Dialog` puro, e não sobre `AlertDialog`. O `AlertDialog` do Material
 * traz o próprio container tonal, o próprio raio de canto e botões de texto na cor
 * primária — três coisas que destoariam de tudo o que foi definido aqui. Fazendo à mão,
 * o diálogo passa a ser a mesma superfície elevada do resto do app.
 *
 * A ação de confirmar usa o botão primário cheio: num diálogo, saber qual é a ação
 * pretendida sem ler as duas opções é metade do trabalho.
 */
@Composable
fun CgDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    dismissText: String = "Cancelar",
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = CgSpace.xxl)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(CgShape.large)
                .background(CgColor.SurfaceRaised)
                .padding(CgSpace.xxl),
        ) {
            Text(text = title, style = CgType.title, color = CgColor.TextPrimary)

            if (description != null) {
                Spacer(Modifier.height(CgSpace.md))
                Text(text = description, style = CgType.caption, color = CgColor.TextSecondary)
            }

            if (content != null) {
                Spacer(Modifier.height(CgSpace.xl))
                content()
            }

            Spacer(Modifier.height(CgSpace.xxl))

            // Empilhados e não lado a lado: em português os rótulos são longos, e dois
            // botões apertados numa linha viram dois alvos pequenos e mal alinhados.
            if (destructive) {
                CgDestructiveButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CgPrimaryButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(CgSpace.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CgTextAction(text = dismissText, onClick = onDismiss, color = CgColor.TextSecondary)
            }
        }
    }
}

/** Caixa de destaque dentro de um diálogo ou de uma tela, sem virar mais um cartão. */
@Composable
fun CgCallout(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CgColor.TextSecondary,
    background: Color = CgColor.SurfaceHigh,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CgShape.medium)
            .background(background)
            .padding(CgSpace.lg),
    ) {
        Text(text = text, style = CgType.caption, color = color)
    }
}
