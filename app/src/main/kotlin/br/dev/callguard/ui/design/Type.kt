package br.dev.callguard.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Tipografia do CallGuard.
 *
 * A hierarquia carrega o peso que antes era pedido aos cartões: quando existe um título
 * grande de verdade e um rótulo pequeno de verdade, a moldura em volta do conteúdo deixa
 * de ser necessária para separar as coisas.
 *
 * Sete estilos, e não os quinze slots do Material. Cada um tem um trabalho; se um texto
 * não se encaixa em nenhum, o problema é o texto.
 *
 * `letterSpacing` negativo nos tamanhos grandes e positivo nos pequenos: títulos grandes
 * com o espaçamento padrão parecem soltos, e rótulos pequenos em caixa alta ficam
 * ilegíveis sem respiro entre as letras.
 */
object CgType {

    private val recorte = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    /** O número ou o estado que domina a tela. Um por tela, no máximo. */
    val display = TextStyle(
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.2).sp,
        lineHeightStyle = recorte,
    )

    /** Título da tela, escrito no conteúdo em vez de numa barra. */
    val headline = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        lineHeightStyle = recorte,
    )

    /** Título de bloco importante. */
    val title = TextStyle(
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = recorte,
    )

    /** Linha principal de um item de lista. */
    val subtitle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = recorte,
    )

    /** Texto corrido. */
    val body = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = recorte,
    )

    /** Apoio, explicação, segunda linha de um item. */
    val caption = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = recorte,
    )

    /** Cabeçalho de seção. Sempre em caixa alta, sempre cinza. */
    val overline = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        lineHeightStyle = recorte,
    )

    /** Texto de botão e de chip. */
    val action = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
        lineHeightStyle = recorte,
    )

    /** Número de telefone, caminho de arquivo, valor técnico. */
    val mono = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = recorte,
    )

    /** Telefone em destaque, na linha principal de um item de histórico. */
    val monoStrong = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = recorte,
    )

    /**
     * Mapeamento para os slots do Material.
     *
     * Nenhuma tela deveria depender disto — elas usam os estilos nomeados acima. Existe
     * como rede de segurança: se algum componente Material sobrar em algum canto, ele
     * cai na tipografia certa em vez de na do framework.
     */
    val materialTypography = Typography(
        displayLarge = display, displayMedium = display, displaySmall = headline,
        headlineLarge = headline, headlineMedium = headline, headlineSmall = title,
        titleLarge = title, titleMedium = subtitle, titleSmall = subtitle,
        bodyLarge = body, bodyMedium = body, bodySmall = caption,
        labelLarge = action, labelMedium = action, labelSmall = overline,
    )
}
