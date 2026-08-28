package br.dev.callguard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.dev.callguard.core.CallerIdCodes
import br.dev.callguard.core.PhoneOrigin

/**
 * Aba para ligar com o proprio numero oculto.
 *
 * A tela mostra apenas o numero que sera chamado. O codigo de servico usado para pedir a
 * ocultacao nao aparece em lugar nenhum da interface -- e detalhe de implementacao, nao
 * informacao util para quem esta ligando.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ligar com número oculto") }) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = numeroDigitado,
                        onValueChange = { numeroDigitado = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Número") },
                        placeholder = { Text("(11) 99999-8888") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )

                    if (numeroLimpo.isNotEmpty() && !ehEmergencia) {
                        val origem = PhoneOrigin.of(numeroLimpo)
                        if (origem.region != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = origem.describe(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (ehEmergencia) {
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Número de emergência. Chamadas de emergência sempre " +
                                    "transmitem sua identidade — ligue normalmente pelo telefone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            ligandoPara = numeroLimpo
                            onPlaceCall(numeroLimpo)
                        },
                        enabled = podeLigar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Ligar", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Depende da operadora ter a ocultação habilitada na linha. " +
                            "Muita gente não atende número privado, e vários aparelhos " +
                            "bloqueiam — a ligação pode não completar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    ligandoPara?.let { numero ->
        CallingOverlay(
            number = numero,
            onDismiss = { ligandoPara = null },
        )
    }
}

/**
 * Tela de "ligando", exibida enquanto o sistema assume a chamada.
 *
 * O app nao substitui a tela de chamada do Android: para desenhar a interface real de
 * uma ligacao em curso (mudo, viva-voz, desligar) seria preciso ser o discador padrao do
 * aparelho, o que faria este app assumir TODAS as chamadas. Esta tela cobre a transicao.
 */
@Composable
private fun CallingOverlay(number: String, onDismiss: () -> Unit) {
    val transicao = rememberInfiniteTransition(label = "chamando")
    val pulso by transicao.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulso",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .alpha(pulso),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = formatForDisplay(number),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Ligando…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val origem = PhoneOrigin.of(number)
            if (origem.region != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = origem.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Seu número não será mostrado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(48.dp))
            TextButton(onClick = onDismiss) { Text("Voltar") }
        }
    }
}

/** Formatacao leve so para leitura: (11) 99999-8888. */
private fun formatForDisplay(number: String): String {
    val digitos = number.filter { it.isDigit() }
    val nacional = if (number.startsWith("+55")) digitos.removePrefix("55") else digitos
    return when (nacional.length) {
        11 -> "(${nacional.take(2)}) ${nacional.drop(2).take(5)}-${nacional.takeLast(4)}"
        10 -> "(${nacional.take(2)}) ${nacional.drop(2).take(4)}-${nacional.takeLast(4)}"
        else -> number
    }
}
