package br.dev.callguard.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import br.dev.callguard.ui.design.CgColor
import br.dev.callguard.ui.design.CgPrimaryButton
import br.dev.callguard.ui.design.CgSpace
import br.dev.callguard.ui.design.CgType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Bloqueio do app por biometria ou senha do aparelho.
 *
 * Duas decisoes que importam:
 *
 * 1. **Senha do aparelho e sempre aceita** (`DEVICE_CREDENTIAL` junto de
 *    `BIOMETRIC_WEAK`). Sem isso, um dedo machucado ou um sensor com defeito trancariam
 *    o usuario para fora das proprias regras, sem nenhuma forma de desligar o recurso.
 * 2. **Falha na disponibilidade libera, nao tranca.** Se o aparelho deixar de ter
 *    qualquer forma de autenticacao cadastrada, o app abre e avisa. Uma tranca cuja
 *    chave deixou de existir nao esta protegendo nada -- so impedindo o dono de entrar.
 */
enum class BiometricAvailability {
    /** Da para autenticar agora. */
    AVAILABLE,

    /** O aparelho tem como autenticar, mas nao ha nada cadastrado. */
    NONE_ENROLLED,

    /** Nao ha hardware nem bloqueio de tela: o recurso nao pode ser oferecido. */
    UNSUPPORTED,
}

object BiometricSupport {

    /**
     * `BIOMETRIC_WEAK` e nao `BIOMETRIC_STRONG`: aqui nao ha chave criptografica presa a
     * autenticacao, entao exigir o nivel forte so reduziria a quantidade de aparelhos
     * onde o recurso funciona, sem ganho real de garantia.
     */
    private const val AUTHENTICATORS =
        Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNSUPPORTED
        }

    /**
     * Pede a autenticacao.
     *
     * @param onFailure recebe `true` quando o usuario desistiu (botao voltar, cancelar) e
     *   `false` quando o sistema recusou por conta propria -- casos que a tela trata de
     *   formas diferentes.
     */
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (userCancelled: Boolean) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val desistiu = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onFailure(desistiu)
                }
                // onAuthenticationFailed (dedo nao reconhecido) nao e tratado de proposito:
                // o proprio dialogo ja avisa e deixa o usuario tentar de novo.
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear o CallGuard")
            .setSubtitle("Suas regras e o histórico de chamadas estão protegidos.")
            // Sem setNegativeButtonText: a API proibe combina-lo com DEVICE_CREDENTIAL,
            // que ja oferece o proprio caminho de saida.
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        runCatching { prompt.authenticate(info) }.onFailure { onFailure(false) }
    }
}

/**
 * Tela mostrada enquanto o app está trancado.
 *
 * Composição deliberadamente pobre: um cadeado pequeno, o nome do app, uma frase e uma
 * ação na base, onde o polegar alcança. Nada mais tem função aqui, e qualquer coisa a
 * mais atrasaria quem só quer entrar.
 */
@Composable
fun LockedScreen(
    mensagemDeErro: String?,
    onUnlock: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CgColor.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CgSpace.section),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = CgColor.TextTertiary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(CgSpace.xxl))
            Text(
                text = "CallGuard está bloqueado",
                style = CgType.title,
                color = CgColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(CgSpace.md))
            Text(
                text = mensagemDeErro
                    ?: "Use a biometria ou a senha do aparelho para continuar.",
                style = CgType.caption,
                color = CgColor.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        CgPrimaryButton(
            text = "Desbloquear",
            onClick = onUnlock,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = CgSpace.lg)
                .padding(bottom = CgSpace.section),
        )
    }
}
