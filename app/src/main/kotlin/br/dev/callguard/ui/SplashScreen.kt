package br.dev.callguard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlinx.coroutines.launch

/** Assinatura do autor, exibida discretamente na abertura. */
private const val AUTHOR_CREDIT = "made w rage by: @dev.lz.mw.zx"

private const val CONTINUE_HINT = "clique em qualquer lugar da tela"

/**
 * Duracao da coreografia.
 *
 * Longa o bastante para os seis tempos existirem como tempos, e nao como um borrao: o
 * telefone chamando, a insistencia, o escudo se fechando, a chamada sendo contida, o
 * escudo assumindo o estado ligado e a marca assentando. Depois disto o relogio para --
 * a cena congela exatamente onde terminou e espera o toque, em vez de sumir sozinha.
 */
private const val ANIMATION_MILLIS = 2600
private const val EXIT_MILLIS = 320

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Faint = Color(0xFF3A3A3A)

/**
 * Curvas com desaceleracao longa -- e o que separa "movimento suave" de "movimento que
 * apenas acontece". Sem bounce nem elasticidade, que aqui soariam brincalhoes demais.
 */
private val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
private val Accelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/**
 * Abertura do app.
 *
 * A narrativa e a do produto, em seis tempos e sem uma unica cor:
 *
 *  1. um ponto pulsa no centro -- a chamada chegando;
 *  2. anel apos anel sai dele, cada vez mais rapido -- a insistencia;
 *  3. um escudo se desenha em volta, do topo para a ponta de baixo, pelos dois lados;
 *  4. os aneis seguintes desaceleram ao encostar no escudo e morrem ali, sem estardalhaco;
 *  5. o escudo se enche de branco de baixo para cima, com menisco, e o telefone dentro
 *     dele inverte para preto -- o estado ligado, sem precisar de um rotulo dizendo isso;
 *  6. a marca e revelada por uma cortina da esquerda para a direita, um fio se desenha
 *     embaixo dela e a assinatura aparece.
 *
 * Preto e branco, alto contraste, sem gradiente, sem neon, sem 3D: cada movimento
 * significa alguma coisa. Terminado o sexto tempo, nada mais se move exceto a respiracao
 * do convite para tocar.
 *
 * Sobre a fluidez: um unico relogio linear comanda a coreografia e cada elemento deriva
 * o proprio tempo dele. O progresso e lido apenas dentro de `Canvas`, de
 * `graphicsLayer { }` e de `drawWithContent { }`, ou seja nas fases de desenho e de
 * layer, nunca na composicao. O quadro e refeito sem recompor nada, e a animacao
 * acompanha a taxa real da tela -- 120 Hz onde ela existe.
 *
 * Nota: nao e possivel animar a tela do INSTALADOR do Android. Esta e a abertura do
 * proprio app.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var congelado by remember { mutableStateOf(false) }
    var saindo by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    // Objetos de desenho reaproveitados entre quadros: alocar Path/PathMeasure a cada
    // frame geraria lixo suficiente para o coletor causar engasgo visivel.
    val cena = remember { CenaCache() }

    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(ANIMATION_MILLIS, easing = LinearEasing))
        // Fim da coreografia: a cena fica exatamente como terminou.
        congelado = true
    }

    val convite = rememberInfiniteTransition(label = "convite")
    val respiracao by convite.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "respiracao",
    )

    val fonteDoToque = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(
                interactionSource = fonteDoToque,
                indication = null,
                enabled = congelado && !saindo,
            ) {
                saindo = true
                escopo.launch {
                    exit.animateTo(1f, tween(EXIT_MILLIS, easing = Accelerate))
                    onFinished()
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // A saida encolhe levemente o conjunto enquanto some: da a sensacao
                    // de que o escudo continua existindo atras da interface que entra.
                    val e = exit.value
                    alpha = 1f - e
                    val escala = 1f - e * 0.07f
                    scaleX = escala
                    scaleY = escala
                },
        ) {
            desenharCena(clock.value, cena)
        }

        // A marca e revelada por cortina, nao por fade: o nome se escreve.
        Text(
            text = "CallGuard",
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = Paper,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.62f)
                .graphicsLayer {
                    alpha = 1f - exit.value
                    // Deslocamento curto: o nome assenta, nao viaja.
                    val p = trecho(clock.value, 0.80f, 0.92f, Decelerate)
                    translationY = (1f - p) * 10.dp.toPx()
                }
                .drawWithContent {
                    val p = trecho(clock.value, 0.80f, 0.92f, Standard)
                    if (p <= 0f) return@drawWithContent
                    clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
                },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp),
        ) {
            Text(
                text = CONTINUE_HINT,
                style = TextStyle(
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = Paper,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.graphicsLayer {
                    // So respira depois que a cena congela; antes disso nao existe.
                    alpha = if (congelado) respiracao * (1f - exit.value) else 0f
                },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = AUTHOR_CREDIT,
                style = TextStyle(fontSize = 10.sp, color = Faint, textAlign = TextAlign.Center),
                modifier = Modifier.graphicsLayer {
                    alpha = trecho(clock.value, 0.92f, 1f, LinearEasing) * (1f - exit.value)
                },
            )
        }
    }
}

/** Recorta um trecho do relogio mestre e aplica a curva daquele elemento. */
private fun trecho(t: Float, inicio: Float, fim: Float, easing: Easing): Float = when {
    t <= inicio -> 0f
    t >= fim -> 1f
    else -> easing.transform((t - inicio) / (fim - inicio))
}

/**
 * Geometria e objetos de desenho reconstruidos so quando o tamanho da tela muda.
 *
 * Existe por um motivo de desempenho concreto: `Path`, `PathMeasure` e a medicao do
 * contorno do escudo sao caros demais para 120 quadros por segundo.
 */
private class CenaCache {
    val escudo = Path()
    val escudoParcial = Path()
    val medidor = PathMeasure()
    val telefone = Path()
    val enchimento = Path()
    var largura = -1f
    var altura = -1f

    /** Espessura do traco do fone, derivada da unidade da cena. */
    var tracoTelefone = 1f
}

private fun DrawScope.desenharCena(t: Float, cache: CenaCache) {
    val unidade = min(size.width, size.height)
    val centro = Offset(size.width / 2f, size.height * 0.42f)
    val lado = unidade * 0.19f

    if (cache.largura != size.width || cache.altura != size.height) {
        construirEscudo(cache.escudo, centro, lado)
        cache.medidor.setPath(cache.escudo, false)
        construirTelefone(cache.telefone, centro, unidade * 0.052f)
        cache.tracoTelefone = unidade * 0.052f * 0.30f
        cache.largura = size.width
        cache.altura = size.height
    }

    desenharPonto(t, centro, unidade)
    desenharAneis(t, centro, lado, unidade)
    desenharContorno(t, cache, lado)
    desenharTelefoneBranco(t, cache)
    desenharEnchimento(t, cache, centro, lado)
    desenharFio(t, centro, unidade, size.height)
}

/**
 * Tempo 1 — o ponto.
 *
 * A chamada existe antes de qualquer coisa: um ponto que cresce e depois recua um
 * pouco, como um toque de telefone que ja comecou.
 */
private fun DrawScope.desenharPonto(t: Float, centro: Offset, unidade: Float) {
    val entrada = trecho(t, 0.03f, 0.13f, Decelerate)
    if (entrada <= 0f) return
    // Some quando o telefone toma o lugar dele dentro do escudo.
    val saida = trecho(t, 0.42f, 0.52f, Standard)
    val alfa = entrada * (1f - saida)
    if (alfa <= 0f) return
    val raio = unidade * 0.016f * (0.6f + 0.4f * entrada) * (1f - saida * 0.4f)
    drawCircle(color = Paper.copy(alpha = alfa), radius = raio, center = centro)
}

/**
 * Tempos 2 e 4 — a insistencia, e a contencao.
 *
 * Os quatro primeiros aneis saem livres e cada um demora menos que o anterior: a
 * chamada nao desiste, ela acelera. Os dois ultimos nascem depois do escudo pronto,
 * desaceleram ao encostar nele e se apagam ali. Sem explosao, sem X, sem vermelho --
 * o bloqueio do produto tambem e silencioso.
 */
private fun DrawScope.desenharAneis(t: Float, centro: Offset, lado: Float, unidade: Float) {
    val raioInicial = unidade * 0.02f
    val raioLivre = unidade * 0.52f
    val raioEscudo = lado * 0.92f

    // Aneis livres: inicio, fim. As janelas encurtam de proposito.
    val livres = arrayOf(
        0.09f to 0.36f,
        0.15f to 0.40f,
        0.21f to 0.44f,
        0.26f to 0.47f,
    )
    for ((inicio, fim) in livres) {
        val p = trecho(t, inicio, fim, Decelerate)
        if (p <= 0f || p >= 1f) continue
        drawCircle(
            color = Paper.copy(alpha = (1f - p) * 0.55f),
            radius = raioInicial + (raioLivre - raioInicial) * p,
            center = centro,
            style = Stroke(width = unidade * 0.0045f),
        )
    }

    // Aneis contidos: param no limite do escudo.
    val contidos = arrayOf(0.56f to 0.72f, 0.62f to 0.78f)
    for ((inicio, fim) in contidos) {
        val p = trecho(t, inicio, fim, Decelerate)
        if (p <= 0f || p >= 1f) continue
        // O avanco satura perto do escudo: a onda desacelera em vez de parar de repente.
        val avanco = 1f - (1f - p) * (1f - p)
        drawCircle(
            color = Paper.copy(alpha = (1f - p) * 0.5f),
            radius = raioInicial + (raioEscudo - raioInicial) * avanco,
            center = centro,
            style = Stroke(width = unidade * 0.0045f * (1f - p * 0.5f)),
        )
    }
}

/**
 * Tempo 3 — o escudo se desenhando.
 *
 * O traco nasce no topo e corre pelos dois lados ao mesmo tempo, encontrando-se na ponta
 * de baixo. Por isso dois segmentos espelhados, e nao um traco unico: um escudo que se
 * fecha le diferente de um escudo que e contornado.
 */
private fun DrawScope.desenharContorno(t: Float, cache: CenaCache, lado: Float) {
    val traco = trecho(t, 0.30f, 0.60f, Standard)
    if (traco <= 0f) return

    val comprimento = cache.medidor.length
    val metade = comprimento / 2f

    cache.escudoParcial.reset()
    cache.medidor.getSegment(0f, metade * traco, cache.escudoParcial, true)
    cache.medidor.getSegment(comprimento - metade * traco, comprimento, cache.escudoParcial, true)

    drawPath(
        path = cache.escudoParcial,
        color = Paper,
        style = Stroke(width = lado * 0.07f, cap = StrokeCap.Round),
    )
}

/** O telefone dentro do escudo, enquanto o fundo ainda e preto. */
private fun DrawScope.desenharTelefoneBranco(t: Float, cache: CenaCache) {
    val p = trecho(t, 0.44f, 0.58f, Decelerate)
    if (p <= 0f) return
    drawPath(
        path = cache.telefone,
        color = Paper.copy(alpha = p),
        style = Stroke(width = cache.tracoTelefone, cap = StrokeCap.Round),
    )
}

/**
 * Tempo 5 — o escudo assume o estado ligado.
 *
 * O branco sobe de baixo para cima dentro do contorno, com um menisco que achata
 * conforme enche: nao e um retangulo subindo, e um volume assentando. O telefone
 * inverte para preto na parte ja preenchida, e o resultado final e uma silhueta solida
 * -- protecao ativa dita por forma, nao por texto.
 */
private fun DrawScope.desenharEnchimento(t: Float, cache: CenaCache, centro: Offset, lado: Float) {
    val p = trecho(t, 0.68f, 0.86f, Standard)
    if (p <= 0f) return

    val topoEscudo = centro.y - lado * 1.12f
    val baseEscudo = centro.y + lado * 1.18f
    val nivel = baseEscudo - (baseEscudo - topoEscudo) * p
    // O menisco desaparece conforme o volume assenta.
    val menisco = lado * 0.16f * (1f - p)

    cache.enchimento.reset()
    cache.enchimento.moveTo(0f, nivel)
    cache.enchimento.quadraticTo(size.width / 2f, nivel - menisco * 2f, size.width, nivel)
    cache.enchimento.lineTo(size.width, size.height)
    cache.enchimento.lineTo(0f, size.height)
    cache.enchimento.close()

    clipPath(cache.escudo) {
        drawPath(path = cache.enchimento, color = Paper)
    }

    // O telefone reaparece em preto exatamente sobre a area ja preenchida.
    clipPath(cache.enchimento) {
        drawPath(
            path = cache.telefone,
            color = Ink,
            style = Stroke(width = cache.tracoTelefone, cap = StrokeCap.Round),
        )
    }
}

/**
 * Tempo 6 — o fio sob a marca.
 *
 * Um unico traco horizontal que se abre do centro para os lados. Fecha a composicao sem
 * acrescentar mais um elemento para o olho processar.
 */
private fun DrawScope.desenharFio(t: Float, centro: Offset, unidade: Float, alturaTela: Float) {
    val p = trecho(t, 0.88f, 1f, Standard)
    if (p <= 0f) return
    val meia = unidade * 0.16f * p
    val y = alturaTela * 0.62f + unidade * 0.10f
    drawLine(
        color = Paper.copy(alpha = 0.35f),
        start = Offset(centro.x - meia, y),
        end = Offset(centro.x + meia, y),
        strokeWidth = unidade * 0.0035f,
    )
}

/** Silhueta do escudo, centrada em `centro`. */
private fun construirEscudo(path: Path, centro: Offset, lado: Float) {
    val l = lado
    path.reset()
    path.moveTo(centro.x, centro.y - l * 1.10f)
    path.lineTo(centro.x + l * 0.92f, centro.y - l * 0.66f)
    path.lineTo(centro.x + l * 0.92f, centro.y + l * 0.10f)
    path.cubicTo(
        centro.x + l * 0.92f, centro.y + l * 0.70f,
        centro.x + l * 0.54f, centro.y + l * 1.02f,
        centro.x, centro.y + l * 1.16f,
    )
    path.cubicTo(
        centro.x - l * 0.54f, centro.y + l * 1.02f,
        centro.x - l * 0.92f, centro.y + l * 0.70f,
        centro.x - l * 0.92f, centro.y + l * 0.10f,
    )
    path.lineTo(centro.x - l * 0.92f, centro.y - l * 0.66f)
    path.close()
}

/** Silhueta de fone, desenhada como traco unico para casar com o peso do escudo. */
private fun construirTelefone(path: Path, centro: Offset, s: Float) {
    path.reset()
    path.moveTo(centro.x - s * 0.72f, centro.y - s * 0.62f)
    path.cubicTo(
        centro.x - s * 0.72f, centro.y - s * 0.95f,
        centro.x - s * 0.32f, centro.y - s * 0.98f,
        centro.x - s * 0.20f, centro.y - s * 0.70f,
    )
    path.lineTo(centro.x - s * 0.02f, centro.y - s * 0.26f)
    path.cubicTo(
        centro.x + s * 0.06f, centro.y - s * 0.06f,
        centro.x - s * 0.02f, centro.y + s * 0.06f,
        centro.x - s * 0.18f, centro.y + s * 0.14f,
    )
    path.cubicTo(
        centro.x - s * 0.06f, centro.y + s * 0.52f,
        centro.x + s * 0.24f, centro.y + s * 0.76f,
        centro.x + s * 0.60f, centro.y + s * 0.84f,
    )
    path.cubicTo(
        centro.x + s * 0.74f, centro.y + s * 0.70f,
        centro.x + s * 0.88f, centro.y + s * 0.66f,
        centro.x + s * 1.02f, centro.y + s * 0.76f,
    )
}
