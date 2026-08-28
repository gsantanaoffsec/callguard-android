package br.dev.callguard.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens visuais do CallGuard.
 *
 * Uma fonte única para cor, espaço, forma e tempo. Existe para impedir o que o app tinha
 * antes: cada tela decidindo o próprio padding e puxando cor direto do `colorScheme`, o
 * que fazia seis telas parecerem seis produtos.
 *
 * São `object`s e não `CompositionLocal` de propósito. O app é preto, sempre: não há
 * tema claro para trocar nem cor dinâmica para herdar do papel de parede. Um
 * `CompositionLocal` aqui seria cerimônia para uma troca que nunca acontece.
 */
object CgColor {

    // ---- Superfícies -------------------------------------------------------
    /** O fundo. Preto de verdade, não cinza-escuro: é ele que dá o peso da identidade. */
    val Background = Color(0xFF000000)

    /**
     * Níveis acima do fundo. Usados com parcimônia — quando tudo vira cartão cinza, o
     * preto deixa de ser protagonista e a tela volta a parecer um template.
     */
    val Surface = Color(0xFF0E0E0E)
    val SurfaceRaised = Color(0xFF161616)
    val SurfaceHigh = Color(0xFF1F1F1F)

    // ---- Traços ------------------------------------------------------------
    /** Divisor entre itens de lista. Precisa organizar sem chamar atenção. */
    val Line = Color(0xFF232323)

    /** Borda de componente interativo. */
    val LineStrong = Color(0xFF333333)

    // ---- Texto -------------------------------------------------------------
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9E9E9E)
    val TextTertiary = Color(0xFF6B6B6B)
    val TextDisabled = Color(0xFF474747)

    // ---- Inversão (botão primário, chip selecionado) ------------------------
    val Inverse = Color(0xFFFFFFFF)
    val OnInverse = Color(0xFF000000)

    // ---- Semânticas --------------------------------------------------------
    // Existem só onde a cor carrega significado que a tipografia não carregaria.
    // Dessaturadas de propósito: em tela preta, cor saturada grita.
    val Positive = Color(0xFF4ADE80)
    val Negative = Color(0xFFFF6B6B)
    val Warning = Color(0xFFF0C05A)

    /** Fundos de estado, para faixas discretas em vez de banners coloridos. */
    val PositiveDim = Color(0xFF0F2418)
    val NegativeDim = Color(0xFF2A1212)
    val WarningDim = Color(0xFF241D0C)
}

/**
 * Escala de espaçamento.
 *
 * Passos deliberadamente poucos. O app antigo usava 2, 4, 6, 8, 10, 12, 16, 20, 24 e 32
 * sem critério; o resultado era denso e desalinhado. Aqui cada passo tem um papel.
 */
object CgSpace {
    /** Colado: rótulo e valor da mesma informação. */
    val xxs = 2.dp

    /** Entre título e subtítulo de um mesmo item. */
    val xs = 4.dp

    /** Entre elementos irmãos dentro de um bloco. */
    val sm = 8.dp
    val md = 12.dp

    /** Margem lateral da tela e padding interno padrão. */
    val lg = 16.dp
    val xl = 20.dp

    /** Entre grupos relacionados. */
    val xxl = 24.dp

    /** Entre seções distintas — o respiro que define a identidade. */
    val section = 36.dp

    /** Antes do fim da lista, para o conteúdo não morrer colado na navegação. */
    val bottom = 56.dp
}

/**
 * Formas.
 *
 * Três raios e um pill. Cantos discretos: a interface é sóbria, não amigável-arredondada.
 */
object CgShape {
    // Tipados como RoundedCornerShape (e nao Shape) porque o Material3 exige
    // CornerBasedShape ao montar o seu proprio conjunto de formas.
    val small: RoundedCornerShape = RoundedCornerShape(8.dp)
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp)
    val large: RoundedCornerShape = RoundedCornerShape(16.dp)
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50)
}

/** Alturas e tamanhos fixos que precisam ser iguais em todo lugar. */
object CgSize {
    /** Alvo de toque confortável, acima do mínimo de 48 dp da acessibilidade. */
    val buttonHeight = 56.dp
    val fieldHeight = 56.dp
    val chipHeight = 40.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val hairline = 1.dp
    val touchMin = 48.dp
}

/**
 * Movimento.
 *
 * Curto e sem elasticidade. Microinteração que se faz notar deixa de ser microinteração.
 */
object CgMotion {
    const val fast = 120
    const val normal = 200
    const val slow = 320

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
}
