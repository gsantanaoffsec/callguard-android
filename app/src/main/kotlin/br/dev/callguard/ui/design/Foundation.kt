package br.dev.callguard.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Casca de tela do CallGuard.
 *
 * Não usa `TopAppBar`. O título vive **dentro do conteúdo**, grande, e rola junto — é o
 * que dá à tela a hierarquia de um produto em vez da de um formulário Android. A barra
 * superior fica reduzida a uma faixa fina que só existe quando há voltar ou ações, e
 * mesmo aí sem cor, sem sombra e sem título duplicado.
 */
@Composable
fun CgScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /**
     * Desenho ao lado do titulo. So a tela inicial usa: a marca identifica o APP, e
     * repeti-la ao lado de "Regras" ou "Bloqueadas" a transformaria em enfeite de
     * cabecalho.
     */
    leading: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    // A entrada roda uma vez por tela, identificada pelo titulo: trocar de aba refaz o
    // movimento, rolar a lista nao.
    val entrada = rememberScreenEntrance(title)

    Scaffold(
        modifier = modifier,
        containerColor = CgColor.Background,
        contentColor = CgColor.TextPrimary,
        topBar = { CgTopStrip(onBack = onBack, actions = actions) },
        bottomBar = bottomBar,
    ) { padding ->
        CompositionLocalProvider(LocalScreenEntrance provides entrada) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CgSpace.lg),
            contentPadding = PaddingValues(bottom = CgSpace.bottom),
        ) {
            item(key = "cg-title") {
                Column(
                    Modifier
                        .cgEnter()
                        .padding(top = CgSpace.md, bottom = CgSpace.xxl),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (leading != null) {
                            leading()
                            Spacer(Modifier.width(CgSpace.md))
                        }
                        Text(text = title, style = CgType.headline, color = CgColor.TextPrimary)
                    }
                    if (subtitle != null) {
                        Spacer(Modifier.height(CgSpace.sm))
                        Text(
                            text = subtitle,
                            style = CgType.body,
                            color = CgColor.TextSecondary,
                        )
                    }
                }
            }
            content()
        }
        }
    }
}

/**
 * Faixa superior mínima.
 *
 * Consome as barras do sistema por conta própria: o `Scaffold` aplica os insets ao
 * conteúdo, não ao slot da barra superior, então sem isto a faixa seria desenhada por
 * baixo do relógio e da bateria.
 */
@Composable
private fun CgTopStrip(onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .defaultMinSize(minHeight = CgSize.touchMin)
            .padding(horizontal = CgSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CgIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/**
 * Cabeçalho de seção.
 *
 * Substitui o cartão que antes envolvia cada grupo. Um rótulo pequeno em caixa alta
 * sobre o fundo preto separa os assuntos melhor do que uma moldura cinza — e não gasta
 * 32 dp de altura para dizer a mesma coisa.
 */
@Composable
fun CgSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    top: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (top) 0.dp else CgSpace.section, bottom = CgSpace.md),
    ) {
        Text(
            text = label.uppercase(),
            style = CgType.overline,
            color = CgColor.TextTertiary,
        )
        if (description != null) {
            Spacer(Modifier.height(CgSpace.sm))
            Text(text = description, style = CgType.caption, color = CgColor.TextSecondary)
        }
    }
}

/** Divisor de um pixel. Discreto o bastante para organizar sem desenhar grade. */
@Composable
fun CgDivider(modifier: Modifier = Modifier, inset: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = if (inset) CgSpace.xxl else 0.dp)
            .height(CgSize.hairline)
            .background(CgColor.Line),
    )
}

/** Ícone tocável com alvo de 48 dp, sem o ripple circular gigante do Material. */
@Composable
fun CgIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CgColor.TextPrimary,
) {
    Box(
        modifier = modifier
            .size(CgSize.touchMin)
            .clip(CircleShape)
            .cgClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else CgColor.TextDisabled,
            modifier = Modifier.size(CgSize.iconLg),
        )
    }
}

/** Intenção de um aviso em linha. Define a cor sem espalhar `Color` pelas telas. */
enum class CgNoticeTone { INFO, WARNING, ERROR, POSITIVE }

/**
 * Aviso em linha.
 *
 * Um traço vertical colorido e o texto — sem cartão, sem fundo preenchido, sem ícone
 * grande. Em tela preta, um bloco colorido inteiro rouba a atenção de tudo o que está
 * em volta; uma barra de 2 dp diz a mesma coisa e devolve a hierarquia.
 */
@Composable
fun CgNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: CgNoticeTone = CgNoticeTone.INFO,
) {
    val cor = when (tone) {
        CgNoticeTone.INFO -> CgColor.TextTertiary
        CgNoticeTone.WARNING -> CgColor.Warning
        CgNoticeTone.ERROR -> CgColor.Negative
        CgNoticeTone.POSITIVE -> CgColor.Positive
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = CgSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(28.dp)
                .background(cor),
        )
        Spacer(Modifier.width(CgSpace.md))
        Text(
            text = text,
            style = CgType.caption,
            color = if (tone == CgNoticeTone.INFO) CgColor.TextSecondary else cor,
        )
    }
}

/**
 * Estado vazio.
 *
 * Ícone pequeno, uma frase, uma explicação curta. Nada de ilustração ocupando meia tela:
 * "ainda não aconteceu nada" não é um evento que mereça celebração gráfica, e uma tela
 * vazia decorada demais é lida como erro.
 */
@Composable
fun CgEmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CgColor.TextDisabled,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(CgSpace.lg))
        Text(
            text = title,
            style = CgType.subtitle,
            color = CgColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(CgSpace.sm))
            Text(
                text = description,
                style = CgType.caption,
                color = CgColor.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Número grande com rótulo pequeno.
 *
 * Onde antes existia um cartão inteiro para dizer "3 no total", agora existe o 3.
 */
@Composable
fun CgMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = CgColor.TextPrimary,
    valueStyle: androidx.compose.ui.text.TextStyle = CgType.display,
) {
    Column(modifier) {
        Text(text = value, style = valueStyle, color = tone)
        Spacer(Modifier.height(CgSpace.xs))
        Text(text = label.uppercase(), style = CgType.overline, color = CgColor.TextTertiary)
    }
}

/**
 * O estado da proteção — o elemento mais importante da tela inicial.
 *
 * Um ponto, uma frase grande e uma linha de apoio. Reconhecível em menos de um segundo
 * sem depender de banner colorido: o contraste do branco sobre preto e o tamanho do
 * texto fazem o trabalho, e a cor entra só no ponto, que é onde ela significa algo.
 *
 * O ponto pulsa devagar quando está protegendo. É a única animação contínua do app, e
 * existe porque "ativo" é um estado vivo — parado, ele seria indistinguível de um ícone.
 */
@Composable
fun CgStatusBlock(
    active: Boolean,
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    val cor = if (active) CgColor.Positive else CgColor.Negative

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SinalDeEstado(active = active, color = cor)
            Spacer(Modifier.width(CgSpace.md))
            Text(
                text = if (active) "PROTEGIDO" else "SEM PROTEÇÃO",
                style = CgType.overline,
                color = cor,
            )
        }
        Spacer(Modifier.height(CgSpace.lg))
        Text(text = headline, style = CgType.display, color = CgColor.TextPrimary)
        Spacer(Modifier.height(CgSpace.md))
        Text(text = supporting, style = CgType.body, color = CgColor.TextSecondary)
    }
}

/**
 * O sinal de "protegido": um ponto que respira e emite ondas.
 *
 * A metáfora é a do produto — o app existe porque ondas saem de um telefone insistindo.
 * Aqui elas saem do sinal em ritmo calmo, o oposto da insistência: é assim que se mostra
 * que algo está vivo e em guarda sem escrever "vivo e em guarda".
 *
 * Só pulsa quando está protegendo. Sem proteção o ponto fica **parado**, e isso é
 * proposital: a ausência de movimento é a diferença mais rápida de perceber com o
 * canto do olho, e não depende de distinguir verde de vermelho.
 *
 * As ondas passam de 5 dp para ~21 dp de raio, extrapolando os limites do `Canvas` de
 * 10 dp. Isso é intencional e não é um erro de layout: o Compose não recorta o desenho
 * às bordas a menos que se peça, então as ondas transbordam sem que a linha inteira
 * precise reservar 42 dp de largura e empurrar o texto para o lado.
 */
@Composable
private fun SinalDeEstado(active: Boolean, color: Color) {
    if (!active) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        return
    }

    val transicao = rememberInfiniteTransition(label = "sinal")
    val fase by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fase-sinal",
    )

    Canvas(Modifier.size(10.dp)) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val raioPonto = size.minDimension / 2f

        // Três ondas defasadas: sempre há uma nascendo enquanto outra morre, o que
        // torna o ciclo contínuo em vez de pulsar em blocos.
        repeat(3) { indice ->
            val p = (fase + indice / 3f) % 1f
            // Desacelera ao se afastar, como onda perdendo energia.
            val avanco = 1f - (1f - p) * (1f - p)
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.5f),
                radius = raioPonto + raioPonto * 3.2f * avanco,
                center = centro,
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }

        // O ponto respira junto, discretamente.
        val respiro = 1f + 0.09f * sin(fase * 2f * PI.toFloat())
        drawCircle(color = color, radius = raioPonto * respiro, center = centro)
    }
}

/**
 * Superfície elevada.
 *
 * Só onde um bloco precisa mesmo se destacar do fundo — resultado de simulação, caixa de
 * destaque. Não é para agrupar seções: isso é trabalho do [CgSectionHeader].
 */
@Composable
fun CgSurface(
    modifier: Modifier = Modifier,
    color: Color = CgColor.Surface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(CgShape.medium)
            .background(color)
            .padding(CgSpace.lg),
    ) { content() }
}

/**
 * Linha de "mostrar números completos".
 *
 * Aparece no histórico e no registro. Vive aqui porque um mesmo controle repetido em
 * duas telas com dois códigos diferentes é como as interfaces começam a divergir.
 */
@Composable
fun CgRevealRow(
    revealed: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = revealed,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onChange,
            )
            .padding(vertical = CgSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Mostrar números completos", style = CgType.body, color = CgColor.TextPrimary)
            Spacer(Modifier.height(CgSpace.xxs))
            Text(
                text = "Fora desta tela eles seguem mascarados.",
                style = CgType.caption,
                color = CgColor.TextTertiary,
            )
        }
        Spacer(Modifier.width(CgSpace.lg))
        CgSwitch(checked = revealed, onCheckedChange = null)
    }
}

/** Espaço vertical nomeado, para as telas não escreverem `Spacer(Modifier.height(...))`. */
@Composable
fun CgGap(size: androidx.compose.ui.unit.Dp = CgSpace.lg) {
    Spacer(Modifier.height(size))
}

/** Agrupa itens de lista com um divisor entre eles, nunca depois do último. */
@Composable
fun CgGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) { content() }
}
