package br.dev.callguard.ui

import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.dev.callguard.core.CallerIdCodes

/**
 * Aba para ligar com o proprio numero oculto.
 *
 * Usa `Intent.ACTION_DIAL`, que a documentacao do Android recomenda para aplicativos em
 * geral ("most applications should use the ACTION_DIAL") e que **nao exige permissao
 * nenhuma**: abre o discador com o numero preenchido e quem inicia a ligacao e o usuario.
 *
 * A alternativa, `ACTION_CALL`, discaria sozinha mas exigiria `CALL_PHONE` -- uma
 * permissao perigosa, que permite a um app ligar sem o usuario ver. Nao vale um toque
 * a menos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnonymousCallScreen(
    bottomBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var numeroDigitado by remember { mutableStateOf("") }

    val numeroLimpo = CallerIdCodes.sanitizeDialNumber(numeroDigitado)
    val stringDiscada = CallerIdCodes.buildHiddenCallerIdNumber(numeroDigitado)

    // Numeros de emergencia sempre transmitem sua identidade -- a rede ignora o CLIR.
    // Em vez de oferecer um caminho que nao funciona, o app diz isso claramente.
    val ehEmergencia = remember(numeroLimpo) {
        numeroLimpo.isNotEmpty() && runCatching {
            context.getSystemService(TelephonyManager::class.java)
                ?.isEmergencyNumber(numeroLimpo) ?: false
        }.getOrDefault(false)
    }

    val podeDiscar = stringDiscada != null && !ehEmergencia

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
                    Text("Número", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = numeroDigitado,
                        onValueChange = { numeroDigitado = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Para quem ligar") },
                        placeholder = { Text("(11) 99999-8888") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )

                    if (stringDiscada != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Será discado:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringDiscada,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    if (ehEmergencia) {
                        Spacer(Modifier.height(12.dp))
                        AvisoLinha(
                            "Número de emergência. Chamadas de emergência sempre transmitem " +
                                "sua identidade — a rede ignora a ocultação. Ligue normalmente " +
                                "pelo telefone.",
                            erro = true,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val uri = Uri.parse("tel:" + Uri.encode(stringDiscada))
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, uri))
                            }
                        },
                        enabled = podeDiscar,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir discador com número oculto")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "O discador abre com o número preenchido. Você toca no botão " +
                            "verde para ligar — o app não liga sozinho e não pede permissão " +
                            "para fazer chamadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Como funciona", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "O prefixo ${CallerIdCodes.HIDE_CALLER_ID_PREFIX} é um código " +
                            "padrão de telefonia (3GPP TS 22.030). Ele não é enviado como " +
                            "dígitos: o Android reconhece o código e pede à operadora para não " +
                            "apresentar o seu número naquela chamada.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Isso oculta o SEU número. Não altera o número de origem para " +
                            "outro — isso não é possível e não é o que este app faz.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Antes de contar com isso", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    AvisoLinha(
                        "Depende da operadora. Nem toda linha tem a ocultação por chamada " +
                            "habilitada; em algumas é preciso pedir a ativação.",
                    )
                    Spacer(Modifier.height(8.dp))
                    AvisoLinha(
                        "Muita gente não atende número privado, e vários aparelhos têm " +
                            "\"bloquear números desconhecidos\" ligado. A ligação pode nem chegar.",
                    )
                    Spacer(Modifier.height(8.dp))
                    AvisoLinha(
                        "O próprio CallGuard não consegue filtrar chamadas ocultas: sem número, " +
                            "não há como contar tentativas. Quem liga oculto passa por qualquer " +
                            "app de filtragem — inclusive este.",
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ocultar sempre", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Se quiser que todas as suas ligações saiam ocultas, não precisa " +
                            "deste app: use o ajuste do sistema, em Telefone → ⋮ → " +
                            "Configurações → Serviços suplementares → Mostrar meu ID de " +
                            "chamada → Ocultar número.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Com a ocultação permanente ligada, discar " +
                            "${CallerIdCodes.SHOW_CALLER_ID_PREFIX} antes do número mostra o seu " +
                            "número em uma chamada específica.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AvisoLinha(texto: String, erro: Boolean = false) {
    val cor = if (erro) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = cor,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = cor)
    }
}
