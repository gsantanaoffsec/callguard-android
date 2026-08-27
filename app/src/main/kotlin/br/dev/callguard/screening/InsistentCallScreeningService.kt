package br.dev.callguard.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.PhoneAccount
import android.telephony.TelephonyManager
import android.util.Log
import br.dev.callguard.core.AllowReason
import br.dev.callguard.core.IncomingCall
import br.dev.callguard.core.ScreeningDecision
import br.dev.callguard.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A integracao com o Telecom.
 *
 * O Telecom faz bind neste servico quando uma chamada chega e desfaz o bind logo depois
 * de responder -- nao existe processo nosso vivo o tempo todo e nao ha ForegroundService.
 * Esse e o mecanismo oficial e ele e suficiente.
 *
 * Contrato de tempo (documentado em `onScreenCall`): a resposta tem que sair em ate 5 s,
 * senao o framework desfaz o bind e ignora o que dissermos. Por isso o trabalho aqui e
 * so leitura local (DataStore + SQLite indexado), com orcamento proprio menor que o do
 * sistema e falha sempre para o lado de PERMITIR.
 */
class InsistentCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // Chamadas de saida tambem passam por aqui (caller ID). O proprio framework
        // responde por nos nesse caso; `respondToCall` seria ignorado.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        serviceScope.launch {
            val decision = withTimeoutOrNull(DECISION_BUDGET_MILLIS) {
                runCatching { decide(callDetails) }
                    .onFailure { Log.w(TAG, "Falha ao decidir; permitindo a chamada", it) }
                    .getOrDefault(ScreeningDecision.Allow(AllowReason.ERROR_FAILSAFE))
            } ?: ScreeningDecision.Allow(AllowReason.TIMEOUT_FAILSAFE)

            respond(callDetails, decision)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Passos 1 a 11 do fluxo: traduz `Call.Details`, consulta o estado local e decide. */
    private suspend fun decide(callDetails: Call.Details): ScreeningDecision {
        val now = System.currentTimeMillis()
        val locator = ServiceLocator
        val settings = locator.settingsRepository(this).current()

        // So chamadas com handle "tel:" sao entregues para screening; ainda assim
        // conferimos, porque o handle vem nulo quando a apresentacao e restrita.
        val handle = callDetails.handle
        val rawNumber = handle
            ?.takeIf { it.scheme == PhoneAccount.SCHEME_TEL }
            ?.schemeSpecificPart
            ?.takeIf { it.isNotBlank() }

        val normalizedNumber = locator.phoneNumberNormalizer(this).normalize(rawNumber)

        // A protecao do sistema para emergencia ja acontece antes de chegarmos aqui
        // (o Telecom pula a filtragem inteira nesses casos). Reconferimos porque a
        // checagem nao custa permissao nenhuma e o requisito e inegociavel.
        val isEmergency = rawNumber != null && isEmergencyNumber(rawNumber)

        val isAllowlisted = normalizedNumber != null &&
            locator.allowlistRepository(this).contains(normalizedNumber)

        // Consultamos a agenda somente quando a resposta pode mudar a decisao:
        // protecao ligada, numero ainda nao liberado e modo "nunca bloquear contatos".
        val needsContactCheck = settings.protectionEnabled &&
            !settings.applyToContacts &&
            !isAllowlisted &&
            rawNumber != null
        val isSavedContact = needsContactCheck &&
            locator.contactLookup(this).isSavedContact(rawNumber!!)

        val call = IncomingCall(
            normalizedNumber = normalizedNumber,
            timestampMillis = now,
            settings = settings,
            isAllowlisted = isAllowlisted,
            isSavedContact = isSavedContact,
            isEmergencyNumber = isEmergency,
            isIncoming = true,
        )

        val policy = locator.policy()

        // Se da para decidir sem historico, nem abrimos o banco.
        policy.preScreen(call)?.let { return it }

        // A partir daqui `normalizedNumber` nao e nulo -- `preScreen` ja teria retornado.
        val history = locator.callHistoryRepository(this)
        val previousAttempts = history.recordAttemptAndGetPrevious(
            normalizedNumber = normalizedNumber!!,
            nowMillis = now,
            windowMillis = settings.windowMillis,
        )

        val decision = policy.evaluate(call, previousAttempts)

        if (decision is ScreeningDecision.Block) {
            history.recordBlockedCall(
                normalizedNumber = normalizedNumber,
                blockedAtMillis = now,
                attemptsInWindow = decision.attemptsInWindow,
            )
            locator.settingsRepository(this).incrementBlockedTotal()
        }

        return decision
    }

    /** Passo 12: traduz a decisao para `CallResponse` e responde. */
    private fun respond(callDetails: Call.Details, decision: ScreeningDecision) {
        val response = when (decision) {
            is ScreeningDecision.Allow -> CallResponse.Builder().build()

            is ScreeningDecision.Block -> CallResponse.Builder()
                // Impede que a chamada seja apresentada ao usuario. E o pre-requisito:
                // sem ele, `setRejectCall`/`setSkipNotification` lancam IllegalStateException.
                .setDisallowCall(true)
                // Desliga a chamada como se o usuario tivesse tocado em "Recusar".
                // Sem isto a chamada apenas nao apareceria e seguiria ocupando a linha
                // ate cair na caixa postal ou estourar o tempo da operadora.
                .setRejectCall(true)
                // Sem notificacao de chamada perdida: o objetivo do app e nao incomodar.
                .setSkipNotification(true)
                // Deliberadamente false. A documentacao diz que `setSkipCallLog` so vale
                // para apps de operadora/sistema e que a chamada e registrada como
                // BLOCKED_TYPE de qualquer forma. Deixar o registro visivel no historico
                // da Samsung e bom: o usuario ve o que foi barrado.
                .setSkipCallLog(false)
                .build()
        }

        // Log sem numero de telefone -- so a decisao.
        Log.i(TAG, "Screening decidiu: ${decision.describe()}")

        runCatching { respondToCall(callDetails, response) }
            .onFailure { Log.w(TAG, "Nao foi possivel responder ao Telecom", it) }
    }

    private fun isEmergencyNumber(rawNumber: String): Boolean = runCatching {
        getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(rawNumber) ?: false
    }.getOrDefault(false)

    private fun ScreeningDecision.describe(): String = when (this) {
        is ScreeningDecision.Allow -> "ALLOW(${reason.name})"
        is ScreeningDecision.Block -> "BLOCK(${reason.name}, tentativas=$attemptsInWindow)"
    }

    private companion object {
        const val TAG = "CallGuardScreening"

        /**
         * Menor que os 5 s do framework, com folga para gravar o bloqueio e responder.
         * Estourar este orcamento resulta em ALLOW, nunca em BLOCK.
         */
        const val DECISION_BUDGET_MILLIS = 3_000L
    }
}
