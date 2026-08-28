package br.dev.callguard.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.telephony.TelephonyManager
import br.dev.callguard.core.CallPolicy
import br.dev.callguard.core.CallScreeningPolicy
import br.dev.callguard.core.DiagnosticsAssembler
import br.dev.callguard.core.DiagnosticsInput
import br.dev.callguard.core.DiagnosticsReport
import br.dev.callguard.core.IncomingCall
import br.dev.callguard.core.NumberSimulation
import br.dev.callguard.core.PhoneNumberNormalizer
import br.dev.callguard.core.PhoneOrigin
import br.dev.callguard.core.StorageStats
import br.dev.callguard.core.PolicyResolution
import br.dev.callguard.core.ScreeningDecision
import br.dev.callguard.data.db.CallGuardDatabase
import br.dev.callguard.phone.ContactLookup
import br.dev.callguard.screening.BlockedCallNotifier
import br.dev.callguard.screening.CallScreeningRoleController
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Coleta os fatos do sistema e entrega o laudo montado por [DiagnosticsAssembler].
 *
 * Toda a decisao "isto e um problema?" ficou do lado puro; aqui so ha leitura de estado
 * do Android. A divisao existe porque a parte que julga e a que precisa de teste, e ela
 * nao deveria exigir um aparelho para rodar.
 */
class DiagnosticsRepository(
    context: Context,
    private val database: CallGuardDatabase,
    private val settingsRepository: SettingsRepository,
    private val allowlistRepository: AllowlistRepository,
    private val blocklistRepository: BlocklistRepository,
    private val customRuleRepository: CustomRuleRepository,
    private val roleController: CallScreeningRoleController,
    private val contactLookup: ContactLookup,
    private val notifier: BlockedCallNotifier,
    private val normalizer: PhoneNumberNormalizer,
    private val policy: CallScreeningPolicy,
) {

    private val appContext = context.applicationContext

    suspend fun report(nowMillis: Long = System.currentTimeMillis()): DiagnosticsReport {
        val settings = settingsRepository.current()
        val schedule = settingsRepository.currentSchedule()
        val agora = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val agendaAtiva = schedule.isActiveAt(agora)

        val regraAtiva: CallPolicy =
            if (agendaAtiva) schedule.toPolicy() else settings.globalPolicy()

        val entrada = DiagnosticsInput(
            roleAvailable = roleController.isRoleAvailable(),
            roleHeld = roleController.isRoleHeld(),
            protectionEnabled = settings.protectionEnabled,
            applyToContacts = settings.applyToContacts,
            hasContactsPermission = contactLookup.hasReadContactsPermission(),
            notifyOnBlock = settings.notifyOnBlock,
            canPostNotifications = notifier.canNotify(),
            ignoringBatteryOptimizations = ignorandoOtimizacaoDeBateria(),
            hasInternetPermission = declaraPermissaoDeInternet(),
            activePolicy = regraAtiva,
            scheduleActiveNow = agendaAtiva,
            customRuleCount = database.customRuleDao().count(),
            blocklistCount = database.blocklistDao().count(),
            allowlistCount = database.allowlistDao().count(),
        )

        return DiagnosticsReport(
            checks = DiagnosticsAssembler.build(entrada),
            storage = StorageStats(
                attempts = database.callAttemptDao().count(),
                distinctNumbers = database.callAttemptDao().distinctNumberCount(),
                allowlist = entrada.allowlistCount,
                blocklist = entrada.blocklistCount,
                customRules = entrada.customRuleCount,
                blockedCalls = database.blockedCallDao().count(),
                screeningEvents = database.screeningEventDao().count(),
                databaseVersion = CallGuardDatabase.VERSION,
            ),
            activePolicy = regraAtiva,
        )
    }

    /**
     * Roda a decisao real sobre um numero digitado, sem gravar nada.
     *
     * Usa exatamente o mesmo [CallScreeningPolicy] do servico -- se usasse uma copia da
     * regra, a tela poderia dizer uma coisa e a ligacao fazer outra, que e o pior
     * resultado possivel para uma ferramenta de diagnostico. A unica diferenca em
     * relacao a uma ligacao de verdade e que a tentativa nao e registrada: consultar o
     * proprio app nao pode contar como alguem ter ligado.
     */
    suspend fun simulate(
        rawNumber: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): NumberSimulation {
        val normalizado = normalizer.normalize(rawNumber)
        val settings = settingsRepository.current()
        val schedule = settingsRepository.currentSchedule()
        val agora = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())

        val emergencia = ehEmergencia(rawNumber.trim())
        val permitido = normalizado?.let { allowlistRepository.contains(it) } ?: false
        val bloqueado = normalizado?.let { blocklistRepository.contains(it) } ?: false
        val contato = contactLookup.isSavedContact(rawNumber.trim())
        val regraPropria = normalizado?.let { customRuleRepository.find(it) }

        val chamada = IncomingCall(
            normalizedNumber = normalizado,
            timestampMillis = nowMillis,
            localDateTime = agora,
            settings = settings,
            globalPolicy = settings.globalPolicy(),
            isAllowlisted = permitido,
            isBlocklisted = bloqueado,
            isSavedContact = contato,
            isEmergencyNumber = emergencia,
            isIncoming = true,
            customRule = regraPropria,
            schedule = schedule,
        )

        val resolucao = policy.resolve(chamada)
        val regraAplicada = (resolucao as? PolicyResolution.UseWindow)?.policy

        // Leitura pura da janela: nenhuma escrita, ao contrario do caminho do screening.
        val anteriores = if (normalizado != null && regraAplicada != null) {
            database.callAttemptDao().attemptsInWindow(
                number = normalizado,
                windowStart = nowMillis - regraAplicada.windowMillis,
                now = nowMillis,
            )
        } else {
            emptyList()
        }

        val decisao: ScreeningDecision = when (resolucao) {
            is PolicyResolution.Immediate -> resolucao.decision
            is PolicyResolution.UseWindow -> policy.evaluate(chamada, anteriores)
        }

        val naJanela = regraAplicada?.let {
            policy.countInWindow(anteriores, nowMillis, it.windowMillis)
        } ?: 0

        return NumberSimulation(
            rawInput = rawNumber.trim(),
            normalizedNumber = normalizado,
            origin = PhoneOrigin.of(normalizado),
            isEmergency = emergencia,
            isAllowlisted = permitido,
            isBlocklisted = bloqueado,
            isSavedContact = contato,
            hasCustomRule = regraPropria != null,
            appliedPolicy = regraAplicada,
            // A proxima ligacao seria a de numero (janela + 1); e isso que interessa.
            attemptsInWindow = if (decisao is ScreeningDecision.Block) naJanela + 1 else naJanela,
            decision = decisao,
        )
    }

    /** Apaga o historico de tentativas -- a base da janela deslizante -- sem tocar nas regras. */
    suspend fun clearAttemptHistory() {
        database.callAttemptDao().deleteAll()
    }

    private fun ehEmergencia(numero: String): Boolean {
        if (numero.isEmpty()) return false
        return runCatching {
            appContext.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(numero)
                ?: false
        }.getOrDefault(false)
    }

    /**
     * `isIgnoringBatteryOptimizations` e consulta publica e nao exige permissao -- o que
     * exige e o pedido para MUDAR o estado, que o app nao faz.
     */
    private fun ignorandoOtimizacaoDeBateria(): Boolean? = runCatching {
        appContext.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(appContext.packageName)
    }.getOrNull()

    /** Le a lista real de permissoes do pacote instalado, em vez de confiar no manifesto do repo. */
    private fun declaraPermissaoDeInternet(): Boolean = runCatching {
        val info = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
    }.getOrDefault(false)
}
