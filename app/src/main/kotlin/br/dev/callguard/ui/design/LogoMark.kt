package br.dev.callguard.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A marca do CallGuard, desenhada.
 *
 * Mesma forma do ícone do launcher e do quadro final da abertura: escudo sólido com o
 * telefone recortado em preto dentro dele. Repetir a mesma silhueta em três lugares é o
 * que faz o app ser reconhecido antes de o nome ser lido.
 *
 * Desenhada em `Canvas`, não importada como recurso, por dois motivos: acompanha a cor do
 * tema sem precisar de uma segunda cópia do arquivo, e pode ser **animada por progresso**
 * — que é o que o cabeçalho usa para a marca se montar quando a tela aparece.
 *
 * @param progress lambda, não valor: assim o progresso é lido na fase de desenho e a
 *   animação não recompõe nada a cada quadro.
 */
@Composable
fun CgLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    progress: () -> Float = { 1f },
) {
    // Reaproveitados entre quadros: alocar Path e PathMeasure a 120 Hz geraria lixo
    // suficiente para o coletor causar engasgo visível.
    val cache = remember { MarcaCache() }

    Canvas(modifier.size(size)) {
        desenharMarca(progress().coerceIn(0f, 1f), cache)
    }
}

private class MarcaCache {
    val escudo = Path()
    val escudoParcial = Path()
    val telefone = Path()
    val enchimento = Path()
    val medidor = PathMeasure()
    var largura = -1f
    var altura = -1f
    var traco = 1f
}

/**
 * Dois tempos, os mesmos da abertura:
 *
 *  1. o contorno se desenha do topo para a ponta de baixo, pelos dois lados ao mesmo
 *     tempo — um escudo que se fecha lê diferente de um escudo que é contornado;
 *  2. o branco sobe de baixo para cima e o telefone dentro inverte para preto.
 */
private fun DrawScope.desenharMarca(t: Float, cache: MarcaCache) {
    val unidade = kotlin.math.min(size.width, size.height)
    val lado = unidade / 2.42f
    val centro = Offset(size.width / 2f, size.height / 2f)

    if (cache.largura != size.width || cache.altura != size.height) {
        construirEscudo(cache.escudo, centro, lado)
        cache.medidor.setPath(cache.escudo, false)
        construirTelefone(cache.telefone, centro, lado * 0.30f)
        cache.traco = lado * 0.16f
        cache.largura = size.width
        cache.altura = size.height
    }

    // ---- contorno ----
    val contorno = trechoDaMarca(t, 0f, 0.55f)
    if (contorno > 0f) {
        val comprimento = cache.medidor.length
        val metade = comprimento / 2f
        cache.escudoParcial.reset()
        cache.medidor.getSegment(0f, metade * contorno, cache.escudoParcial, true)
        cache.medidor.getSegment(
            comprimento - metade * contorno,
            comprimento,
            cache.escudoParcial,
            true,
        )
        drawPath(
            path = cache.escudoParcial,
            color = CgColor.Inverse,
            style = Stroke(width = cache.traco, cap = StrokeCap.Round),
        )
    }

    // ---- enchimento e telefone invertido ----
    val enchimento = trechoDaMarca(t, 0.42f, 1f)
    if (enchimento <= 0f) return

    val topo = centro.y - lado * 1.14f
    val base = centro.y + lado * 1.20f
    val nivel = base - (base - topo) * enchimento

    cache.enchimento.reset()
    cache.enchimento.moveTo(0f, nivel)
    cache.enchimento.lineTo(size.width, nivel)
    cache.enchimento.lineTo(size.width, size.height)
    cache.enchimento.lineTo(0f, size.height)
    cache.enchimento.close()

    clipPath(cache.escudo) { drawPath(cache.enchimento, CgColor.Inverse) }
    clipPath(cache.enchimento) {
        drawPath(
            path = cache.telefone,
            color = CgColor.OnInverse,
            style = Stroke(width = cache.traco * 0.85f, cap = StrokeCap.Round),
        )
    }
}

/** Recorta uma faixa do progresso e normaliza para 0..1. */
private fun trechoDaMarca(t: Float, inicio: Float, fim: Float): Float = when {
    t <= inicio -> 0f
    t >= fim -> 1f
    else -> {
        val bruto = (t - inicio) / (fim - inicio)
        CgMotion.standard.transform(bruto)
    }
}

private fun construirEscudo(path: Path, centro: Offset, l: Float) {
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
