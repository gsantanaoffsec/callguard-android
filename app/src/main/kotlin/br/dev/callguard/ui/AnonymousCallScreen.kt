package br.dev.callguard.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.dev.callguard.core.CallerIdCodes
import br.dev.callguard.core.PhoneOrigin
import br.dev.callguard.ui.design.CgCallout
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgGap
import br.dev.callguard.ui.design.CgNotice
import br.dev.callguard.ui.design.CgNoticeTone
import br.dev.callguard.ui.design.CgPrimaryButton
import br.dev.callguard.ui.design.CgScreen
import br.dev.callguard.ui.design.CgSectionHeader
import br.dev.callguard.ui.design.CgSize
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgTextAction
import br.dev.callguard.ui.design.CgTextField
import br.dev.callguard.ui.design.CgType

/**
 * Aba para ligar com o próprio número oculto.
 *
 * A tela mostra apenas o número que será chamado. O código de serviço usado para pedir a
 * ocultação não aparece em lugar nenhum da interface — é detalhe de implementação, não
 * informação útil para quem está ligando.
 */
@Composable
fun AnonymousCallScreen(
    isEmergencyNumber: (String) -> Boolean,
    onPlaceCall: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var numeroDigitado by remember { mutableStateOf("") }
    var ligandoPara by remember { mutableStateOf<String?>(null) }

    val numeroLimpo = CallerIdCodes.sanitizeDialNumber(numeroDigitado)
    val ehEmergencia = remember(numeroLimpo) {
        numeroLimpo.isNotEmpty() && isEmergencyNumber(numeroLimpo)
    }
    val podeLigar = numeroLimpo.isNotEmpty() && !ehEmergencia

    CgScreen(
        title = "Ligar oculto",
        subtitle = "A pessoa vê a chamada como número privado.",
        bottomBar = bottomBar,
    ) {
        item("campo") {
            Column(Modifier.fillMaxWidth()) {
                CgTextField(
                    value = numeroDigitado,
                    onValueChange = { numeroDigitado = it },
                    label = "Número",
                    placeholder = "(11) 99999-8888",
                    isError = ehEmergencia,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )

                if (numeroLimpo.isNotEmpty() && !ehEmergencia) {
                    val origem = PhoneOrigin.of(numeroLimpo)
                    if (origem.region != null) {
                        CgGap(CgSpace.md)
                        Text(
                            text = origem.describe(),
                            style = CgType.caption,
                            color = CgColor.TextTertiary,
                        )
                    }
                }

                if (ehEmergencia) {
                    CgNotice(
                        text = "Número de emergência. Chamadas de emergência sempre " +
                            "transmitem sua identidade — ligue normalmente pelo telefone.",
                        tone = CgNoticeTone.ERROR,
                    )
                }

                CgGap(CgSpace.xl)
                CgPrimaryButton(
                    text = "Ligar",
                    icon = Icons.Default.Call,
                    onClick = {
                        ligandoPara = numeroLimpo
                        onPlaceCall(numeroLimpo)
                    },
                    enabled = podeLigar,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item("aviso") {
            Column {
                CgSectionHeader("Antes de ligar")
                CgCallout(
                    text = "Depende de a operadora ter a ocultação habilitada na linha. " +
                        "Muita gente não atende número privado, e vários aparelhos bloqueiam " +
                        "— a ligação pode não completar.",
                )
            }
        }
    }

    ligandoPara?.let { numero ->
        TelaDeChamada(number = numero, onDismiss = { ligandoPara = null })
    }
}

/**
 * Tela de "ligando", exibida enquanto o sistema assume a chamada.
 *
 * O app não substitui a tela de chamada do Android: para desenhar a interface real de
 * uma ligação em curso (mudo, viva-voz, desligar) seria preciso ser o discador padrão do
 * aparelho, o que faria este app assumir TODAS as chamadas. Esta tela cobre a transição.
 *
 * Os anéis são desenhados em `Canvas` e leem o progresso só na fase de desenho — mesma
 * técnica da abertura, para que a animação não recomponha nada a 120 Hz.
 */
@Composable
private fun TelaDeChamada(number: String, onDismiss: () -> Unit) {
    val transicao = rememberInfiniteTransition(label = "chamando")
    val pulso by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulso",
    )
    val entrada by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(240, easing = br.dev.callguard.ui.design.CgMotion.decelerate),
        label = "entrada",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CgColor.Background)
            .graphicsLayer {
                alpha = entrada
                val escala = 0.98f + 0.02f * entrada
                scaleX = escala
                scaleY = escala
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CgSpace.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    // Três anéis defasados saindo do centro: a chamada indo embora.
                    repeat(3) { indice ->
                        val avanco = (pulso + indice / 3f) % 1f
                        drawCircle(
                            color = CgColor.TextPrimary.copy(alpha = (1f - avanco) * 0.25f),
                            radius = size.minDimension / 2f * avanco,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = CgColor.TextPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.height(CgSpace.section))
            Text(
                text = formatarParaLeitura(number),
                style = CgType.headline,
                color = CgColor.TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(CgSpace.md))
            Text(
                text = "Ligando…",
                style = CgType.body,
                color = CgColor.TextSecondary,
            )

            val origem = PhoneOrigin.of(number)
            if (origem.region != null) {
                Spacer(Modifier.height(CgSpace.xs))
                Text(
                    text = origem.describe(),
                    style = CgType.caption,
                    color = CgColor.TextTertiary,
                )
            }

            Spacer(Modifier.height(CgSpace.section))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = CgColor.TextSecondary,
                    modifier = Modifier.size(CgSize.iconSm),
                )
                Spacer(Modifier.width(CgSpace.sm))
                Text(
                    text = "Seu número não será mostrado",
                    style = CgType.caption,
                    color = CgColor.TextSecondary,
                )
            }
        }

        CgTextAction(
            text = "Voltar",
            onClick = onDismiss,
            color = CgColor.TextSecondary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CgSpace.section),
        )
    }
}

/** Formatação leve só para leitura: (11) 99999-8888. */
private fun formatarParaLeitura(number: String): String {
    val digitos = number.filter { it.isDigit() }
    val nacional = if (number.startsWith("+55")) digitos.removePrefix("55") else digitos
    return when (nacional.length) {
        11 -> "(${nacional.take(2)}) ${nacional.drop(2).take(5)}-${nacional.takeLast(4)}"
        10 -> "(${nacional.take(2)}) ${nacional.drop(2).take(4)}-${nacional.takeLast(4)}"
        else -> number
    }
}
