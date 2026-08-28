package br.dev.callguard.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgShape
import br.dev.callguard.ui.design.CgType

/**
 * O tema do CallGuard.
 *
 * **Sem cor dinâmica e sem tema claro, de propósito.** A versão anterior usava
 * `dynamicDarkColorScheme`, o que fazia o app assumir a cor do papel de parede do
 * usuário — é exatamente o que dá a um aplicativo a aparência de template do Android.
 * Aqui a identidade é fixa: preto, branco, e cor apenas onde ela significa alguma coisa.
 *
 * O `ColorScheme` do Material continua definido porque componentes do framework leem
 * dele. Ele não é a fonte de verdade do design — [CgColor] é — mas precisa concordar
 * com ela, senão qualquer componente Material que sobre destoaria.
 */
private val CallGuardColorScheme = darkColorScheme(
    primary = CgColor.Inverse,
    onPrimary = CgColor.OnInverse,
    primaryContainer = CgColor.SurfaceHigh,
    onPrimaryContainer = CgColor.TextPrimary,
    secondary = CgColor.TextSecondary,
    onSecondary = CgColor.OnInverse,
    secondaryContainer = CgColor.SurfaceRaised,
    onSecondaryContainer = CgColor.TextPrimary,
    tertiary = CgColor.Warning,
    onTertiary = CgColor.OnInverse,
    tertiaryContainer = CgColor.WarningDim,
    onTertiaryContainer = CgColor.Warning,
    background = CgColor.Background,
    onBackground = CgColor.TextPrimary,
    surface = CgColor.Background,
    onSurface = CgColor.TextPrimary,
    surfaceVariant = CgColor.SurfaceRaised,
    onSurfaceVariant = CgColor.TextSecondary,
    surfaceContainer = CgColor.Surface,
    surfaceContainerHigh = CgColor.SurfaceRaised,
    surfaceContainerHighest = CgColor.SurfaceHigh,
    error = CgColor.Negative,
    onError = CgColor.OnInverse,
    errorContainer = CgColor.NegativeDim,
    onErrorContainer = CgColor.Negative,
    outline = CgColor.LineStrong,
    outlineVariant = CgColor.Line,
    scrim = CgColor.Background,
)

private val CallGuardShapes = androidx.compose.material3.Shapes().copy(
    extraSmall = CgShape.small,
    small = CgShape.small,
    medium = CgShape.medium,
    large = CgShape.large,
    extraLarge = CgShape.large,
)

/**
 * A cor das barras do sistema NÃO é definida aqui.
 *
 * Em Android 15 `window.statusBarColor` e `window.navigationBarColor` viraram no-op e
 * estão depreciadas: sob edge-to-edge as barras são transparentes e quem pinta atrás
 * delas é o próprio conteúdo. Quem declara o estilo é `enableEdgeToEdge` na Activity,
 * com o estilo escuro fixo. Aqui ficamos só com a aparência dos ícones, que continua
 * sendo responsabilidade do controlador de insets.
 */
@Composable
fun CallGuardTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val janela = (LocalContext.current as? Activity)?.window
        if (janela != null) {
            // Ícones claros: a tela é preta em todas as telas do app.
            WindowCompat.getInsetsController(janela, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = CallGuardColorScheme,
        typography = CgType.materialTypography,
        shapes = CallGuardShapes,
    ) {
        // Defaults do app, para que um Text sem estilo não caia no 16sp genérico.
        CompositionLocalProvider(
            LocalContentColor provides CgColor.TextPrimary,
            LocalTextStyle provides CgType.body.merge(TextStyle(color = CgColor.TextPrimary)),
        ) {
            Box(Modifier.fillMaxSize().background(CgColor.Background)) { content() }
        }
    }
}
