package br.dev.callguard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.dev.callguard.ui.theme.CallGuardTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduz interacoes da tela inicial renderizando Compose de verdade na JVM.
 *
 * Existe por um motivo especifico: um crash que so aparece ao tocar num controle nao e
 * detectavel por compilacao nem por teste de dominio. Sem aparelho, esta e a unica forma
 * de transformar "acho que e isto" em uma reproducao.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun montar(
        estadoInicial: CallGuardUiState = CallGuardUiState(roleHeld = true),
        onApplyToContacts: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            CallGuardTheme {
                HomeScreen(
                    uiState = estadoInicial,
                    onOpenPermissions = {},
                    onProtectionChange = {},
                    onMaxCallsChange = {},
                    onWindowMinutesChange = {},
                    onApplyToContactsChange = onApplyToContacts,
                    onNotifyOnBlockChange = {},
                    onAddAllowlistEntry = { _, _ -> true },
                    onRemoveAllowlistEntry = {},
                    onOpenBlockedCalls = {},
                    onOpenDiagnostics = {},
                    onBiometricLockChange = {},
                    biometricAvailability = BiometricAvailability.AVAILABLE,
                    bottomBar = {},
                )
            }
        }
    }

    @Test
    fun `a tela inicial renderiza sem estourar`() {
        montar()
        compose.onNodeWithText("CallGuard").assertExists()
    }

    @Test
    fun `tocar em aplicar aos contatos salvos nao derruba a tela`() {
        var recebido: Boolean? = null
        montar(onApplyToContacts = { recebido = it })

        compose.onNodeWithText("Aplicar aos contatos salvos").performScrollTo().performClick()
        compose.waitForIdle()

        assertTrue("o callback deveria ter recebido true", recebido == true)
    }

    @Test
    fun `o estado de falta de permissao renderiza o aviso sem estourar`() {
        // Exatamente o estado em que a tela entra logo apos o toque: modo 2 pedido,
        // permissao ainda nao concedida.
        montar(
            estadoInicial = CallGuardUiState(
                roleHeld = true,
                hasReadContactsPermission = false,
                settings = br.dev.callguard.core.ProtectionSettings(applyToContacts = true),
            ),
        )
        compose.onNodeWithText("Permissão de contatos não concedida. Contatos salvos continuam passando.")
            .performScrollTo()
            .assertExists()
    }

    /**
     * A TRANSICAO de estado, nao o estado final.
     *
     * Renderizar a tela ja com `applyToContacts = true` prova pouco: o que muda no toque
     * e o aviso de permissao aparecendo dentro de um item que ja estava composto. E ali,
     * na mudanca, que uma arvore mal formada estoura -- nao na primeira composicao.
     */
    @Test
    fun `ligar contatos sem permissao troca o estado e faz o aviso aparecer`() {
        var estado = CallGuardUiState(roleHeld = true, hasReadContactsPermission = false)

        compose.setContent {
            var atual by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(estado)
            }
            CallGuardTheme {
                HomeScreen(
                    uiState = atual,
                    onOpenPermissions = {},
                    onProtectionChange = {},
                    onMaxCallsChange = {},
                    onWindowMinutesChange = {},
                    onApplyToContactsChange = { ligado ->
                        atual = atual.copy(
                            settings = atual.settings.copy(applyToContacts = ligado),
                        )
                        estado = atual
                    },
                    onNotifyOnBlockChange = {},
                    onAddAllowlistEntry = { _, _ -> true },
                    onRemoveAllowlistEntry = {},
                    onOpenBlockedCalls = {},
                    onOpenDiagnostics = {},
                    onBiometricLockChange = {},
                    biometricAvailability = BiometricAvailability.AVAILABLE,
                    bottomBar = {},
                )
            }
        }

        compose.onNodeWithText("Aplicar aos contatos salvos").performScrollTo().performClick()
        compose.waitForIdle()

        assertTrue("o estado deveria ter ligado", estado.settings.applyToContacts)
        compose.onNodeWithText(
            "Permissão de contatos não concedida. Contatos salvos continuam passando.",
        ).performScrollTo().assertExists()
    }

    /**
     * Percorre os outros controles da tela, procurando o mesmo tipo de falha.
     *
     * Usa `performScrollToNode` e nao `performScrollTo`: numa `LazyColumn` o item fora da
     * tela nem chegou a ser composto, entao nao existe na arvore de semantica e o segundo
     * nao tem o que procurar. Foi assim que este teste falhou na primeira tentativa --
     * limitacao do teste, nao defeito do app.
     */
    @Test
    fun `os demais controles da tela inicial respondem ao toque`() {
        montar()
        // Na ordem em que aparecem na tela: `performScrollToNode` so rola para frente,
        // entao procurar um item acima depois de ter descido nao encontra nada.
        listOf(
            "Bloquear chamadas insistentes",
            "Avisar quando bloquear",
            "Exigir biometria para abrir",
            "Chamadas bloqueadas",
            "Diagnóstico e backup",
        ).forEach { rotulo ->
            tocarEm(hasText(rotulo))
        }
    }

    /**
     * O campo de janela abre a folha, e a folha oferece o valor em horas.
     *
     * Cobre a troca da fileira de chips pelo campo: se o campo parar de abrir a folha, a
     * pessoa fica sem nenhuma forma de mudar a regra -- uma falha silenciosa, porque a
     * tela continua desenhando normalmente.
     */
    @Test
    fun `o campo de janela abre a folha com a opcao personalizada em horas`() {
        montar()

        tocarEm(hasText("Dentro de", ignoreCase = true) and hasClickAction())

        compose.onNodeWithText("Personalizado, em horas").assertExists()
        compose.onNodeWithText("de 1 a 24 horas").assertExists()
    }

    /** O campo de limite tambem abre, e mostra o que cada opcao significa. */
    @Test
    fun `o campo de limite abre a folha explicando cada opcao`() {
        montar()

        tocarEm(hasText("Chamadas permitidas", ignoreCase = true) and hasClickAction())

        compose.onNodeWithText("a 4ª é recusada").assertExists()
    }

    private fun tocarEm(condicao: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(condicao)
        compose.onAllNodes(condicao).onFirst().performClick()
        compose.waitForIdle()
    }

    /**
     * O sinal de "protegido" tem uma animacao INFINITA (as ondas verdes).
     *
     * Isso e uma armadilha conhecida em teste de Compose: uma animacao que nunca termina
     * pode deixar a arvore permanentemente ocupada e travar qualquer espera automatica.
     * Este teste existe para que, se alguem trocar a implementacao do sinal por uma que
     * prenda o relogio, a suite acuse em vez de a tela ficar sem resposta no aparelho.
     */
    @Test
    fun `a tela continua respondendo com o sinal de protegido animando`() {
        montar(estadoInicial = CallGuardUiState(roleHeld = true))

        compose.onNodeWithText("Chamadas insistentes são recusadas.").assertExists()
        // Interagir DEPOIS da animacao ter comecado: e o que provaria o travamento.
        compose.onNodeWithText("Bloquear chamadas insistentes").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("CallGuard").assertExists()
    }

    /** Sem protecao o sinal fica parado; a tela precisa montar igualmente. */
    @Test
    fun `a tela monta com o sinal parado quando nao ha protecao`() {
        montar(estadoInicial = CallGuardUiState(roleHeld = false))
        compose.onNodeWithText("Falta a sua autorização.").assertExists()
        compose.onNodeWithText("Configurar permissões").assertExists()
    }
}
