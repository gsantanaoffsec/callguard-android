package br.dev.callguard.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/** Tela mostrada enquanto o app esta trancado. */
@Composable
fun LockedScreen(
    mensagemDeErro: String?,
    onUnlock: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "CallGuard está bloqueado",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = mensagemDeErro
                    ?: "Use a biometria ou a senha do aparelho para continuar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text("Desbloquear") }
        }
    }
}
