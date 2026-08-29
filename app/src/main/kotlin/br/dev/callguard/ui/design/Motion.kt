package br.dev.callguard.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Movimento reutilizável do CallGuard.
 *
 * Duas regras valem para tudo que está aqui:
 *
 * **Toda animação explica alguma coisa.** Entrada de tela diz de onde o conteúdo veio;
 * o número contando diz que ele mudou; a linha que desliza ao apagar diz que a lista se
 * fechou. Movimento que não responde a "o que isto está me dizendo?" é decoração, e
 * decoração num app que se abre para resolver um incômodo é atrito.
 *
 * **O progresso é lido na fase de desenho**, dentro de `graphicsLayer { }`, nunca na
 * composição. O quadro é refeito sem recompor nada — é o que permite manter 120 Hz numa
 * lista longa em vez de recompor cada linha a cada quadro.
 */

/**
 * Progresso da entrada da tela atual, de 0 a 1.
 *
 * Roda **uma vez** quando a tela aparece. Isso importa: um item que só é composto depois,
 * ao rolar a lista, encontra o valor já em 1 e aparece instantaneamente — em vez de cada
 * linha piscar durante a rolagem, que é o defeito da entrada escalonada ingênua.
 */
val LocalScreenEntrance = compositionLocalOf<State<Float>> {
    // Fora de uma tela do app não há entrada a animar: tudo já está presente.
    mutableFloatStateOf(1f)
}

/** Duração da entrada de um bloco de conteúdo. */
private const val ENTRANCE_MILLIS = 460

/** Deslocamento por posição na lista. Limitado para a última linha não chegar tarde. */
private const val MAX_STAGGERED_ITEMS = 7
private const val STAGGER_STEP = 0.07f

@Composable
fun rememberScreenEntrance(key: Any): State<Float> {
    val progresso = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progresso.snapTo(0f)
        progresso.animateTo(1f, tween(ENTRANCE_MILLIS, easing = CgMotion.decelerate))
    }
    return progresso.asState()
}

/**
 * Entrada de um bloco: sobe alguns dp enquanto aparece.
 *
 * @param index posição na lista, para escalonar. Blocos únicos usam 0.
 */
@Composable
fun Modifier.cgEnter(index: Int = 0): Modifier {
    val entrada = LocalScreenEntrance.current
    val atraso = (index.coerceAtMost(MAX_STAGGERED_ITEMS)) * STAGGER_STEP
    return this.graphicsLayer {
        val bruto = ((entrada.value - atraso) / (1f - atraso)).coerceIn(0f, 1f)
        val suave = CgMotion.decelerate.transform(bruto)
        alpha = suave
        translationY = (1f - suave) * 16.dp.toPx()
    }
}

/**
 * Resposta ao toque: o elemento cede um pouco sob o dedo.
 *
 * Some junto com o ripple, mas diz outra coisa — o ripple confirma que o toque foi
 * registrado, a escala confirma que o alvo é aquele. Em botões grandes, sem isso o toque
 * parece acontecer no vazio.
 */
@Composable
fun Modifier.cgPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressionado by interactionSource.collectIsPressedAsState()
    val escala = remember { Animatable(1f) }
    LaunchedEffect(pressionado, enabled) {
        escala.animateTo(
            targetValue = if (pressionado && enabled) 0.975f else 1f,
            animationSpec = tween(CgMotion.fast, easing = CgMotion.standard),
        )
    }
    return this.graphicsLayer {
        scaleX = escala.value
        scaleY = escala.value
    }
}

/**
 * Um número que anda até o novo valor em vez de trocar de uma vez.
 *
 * Usado no total de chamadas bloqueadas. A troca seca é indistinguível de um erro de
 * leitura; a contagem torna a mudança um evento, que é o que ela é.
 */
@Composable
fun cgAnimatedCount(value: Int): Int {
    val animado by animateIntAsState(
        targetValue = value,
        animationSpec = tween(CgMotion.slow, easing = CgMotion.decelerate),
        label = "contagem",
    )
    return animado
}
