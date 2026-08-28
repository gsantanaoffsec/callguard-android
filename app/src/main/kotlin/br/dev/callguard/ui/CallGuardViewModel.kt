package br.dev.callguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.net.Uri
import br.dev.callguard.core.BackupPayload
import br.dev.callguard.core.DiagnosticFix
import br.dev.callguard.core.SchedulePolicy
import br.dev.callguard.data.BackupCodec
import br.dev.callguard.data.BackupException
import br.dev.callguard.data.BackupRepository
import br.dev.callguard.data.DiagnosticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import br.dev.callguard.data.AllowlistRepository
import br.dev.callguard.data.BlocklistRepository
import br.dev.callguard.data.CustomRuleRepository
import br.dev.callguard.data.CallHistoryRepository
import br.dev.callguard.data.ScreeningLogRepository
import br.dev.callguard.data.ServiceLocator
import br.dev.callguard.data.SettingsRepository
import br.dev.callguard.phone.ContactLookup
import br.dev.callguard.screening.BlockedCallNotifier
import br.dev.callguard.screening.CallScreeningRoleController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cola entre a UI e os repositorios.
 *
 * Estado do papel e da permissao nao sao observaveis por `Flow` -- eles mudam em telas
 * do sistema. Por isso a Activity chama `refreshSystemState()` em cada `ON_RESUME`.
 */
class CallGuardViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val allowlistRepository: AllowlistRepository,
    private val historyRepository: CallHistoryRepository,
    private val roleController: CallScreeningRoleController,
    private val contactLookup: ContactLookup,
    private val blockedCallNotifier: BlockedCallNotifier,
    private val screeningLogRepository: ScreeningLogRepository,
    private val blocklistRepository: BlocklistRepository,
    private val customRuleRepository: CustomRuleRepository,
    private val backupRepository: BackupRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val crashReporter: br.dev.callguard.data.CrashReporter,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CallGuardUiState())
    val uiState: StateFlow<CallGuardUiState> = _uiState.asStateFlow()

    private val normalizer = ServiceLocator.phoneNumberNormalizer(application)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            settingsRepository.blockedCallsTotal.collect { total ->
                _uiState.update { it.copy(blockedCallsTotal = total) }
            }
        }
        viewModelScope.launch {
            allowlistRepository.observeEntries().collect { entries ->
                allowlistRepository.onEntriesChanged(entries)
                _uiState.update { it.copy(allowlist = entries) }
            }
        }
        viewModelScope.launch {
            historyRepository.observeBlockedCalls().collect { blocked ->
                _uiState.update { it.copy(blockedCalls = blocked) }
            }
        }
        viewModelScope.launch {
            settingsRepository.schedule.collect { s -> _uiState.update { it.copy(schedule = s) } }
        }
        viewModelScope.launch {
            blocklistRepository.observeEntries().collect { entries ->
                blocklistRepository.onEntriesChanged(entries)
                _uiState.update { it.copy(blocklist = entries) }
            }
        }
        viewModelScope.launch {
            customRuleRepository.observeRules().collect { regras ->
                customRuleRepository.onRulesChanged(regras)
                _uiState.update { it.copy(customRules = regras) }
            }
        }
        viewModelScope.launch {
            screeningLogRepository.observeEvents().collect { eventos ->
                _uiState.update { it.copy(screeningEvents = eventos) }
            }
        }
        _uiState.update { it.copy(logFilePath = screeningLogRepository.friendlyLogPath()) }
        refreshCrashReport()
        refreshSystemState()
    }

    /** Reconsulta a existencia do relatorio de falha. Barato: e um `length()` em disco. */
    fun refreshCrashReport() {
        _uiState.update {
            it.copy(
                hasCrashReport = crashReporter.hasReport(),
                crashReportPath = crashReporter.friendlyPath(),
            )
        }
    }

    fun clearCrashReport() {
        crashReporter.clear()
        refreshCrashReport()
    }

    /** URI do arquivo de falhas, para abrir ou enviar pelo seletor do sistema. */
    fun crashReportUri(): android.net.Uri? = runCatching {
        androidx.core.content.FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            crashReporter.file(),
        )
    }.getOrNull()

    fun refreshSystemState() {
        _uiState.update {
            it.copy(
                roleAvailable = roleController.isRoleAvailable(),
                roleHeld = roleController.isRoleHeld(),
                hasReadContactsPermission = contactLookup.hasReadContactsPermission(),
                canPostNotifications = blockedCallNotifier.canNotify(),
            )
        }
    }

    fun createRoleRequestIntent() = roleController.createRequestRoleIntent()

    fun setProtectionEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setProtectionEnabled(enabled)
    }

    fun setMaxAllowedCalls(value: Int) = viewModelScope.launch {
        settingsRepository.setMaxAllowedCalls(value)
    }

    fun setWindowMinutes(value: Int) = viewModelScope.launch {
        settingsRepository.setWindowMinutes(value)
    }

    fun setApplyToContacts(value: Boolean) = viewModelScope.launch {
        settingsRepository.setApplyToContacts(value)
    }

    fun setNotifyOnBlock(value: Boolean) = viewModelScope.launch {
        settingsRepository.setNotifyOnBlock(value)
    }

    fun setBiometricLock(value: Boolean) = viewModelScope.launch {
        settingsRepository.setBiometricLock(value)
    }

    // --- Central de diagnostico -------------------------------------------------

    /**
     * Remonta o laudo.
     *
     * Sempre sob demanda, nunca observado por `Flow`: metade do que ele mede (papel,
     * permissoes, bateria) so muda em telas do sistema, e a outra metade sao contagens
     * que nao valem uma consulta por recomposicao.
     */
    fun refreshDiagnostics() = viewModelScope.launch {
        val laudo = runCatching { diagnosticsRepository.report() }.getOrNull()
        _uiState.update { it.copy(diagnostics = laudo) }
    }

    fun simulateNumber(rawNumber: String) = viewModelScope.launch {
        val resultado = runCatching { diagnosticsRepository.simulate(rawNumber) }.getOrNull()
        _uiState.update { it.copy(simulation = resultado) }
    }

    fun clearSimulation() {
        if (_uiState.value.simulation != null) {
            _uiState.update { it.copy(simulation = null) }
        }
    }

    fun clearAttemptHistory() = viewModelScope.launch {
        diagnosticsRepository.clearAttemptHistory()
        _uiState.update { it.copy(simulation = null) }
        refreshDiagnostics()
    }

    /** Correcoes que o proprio ViewModel resolve; as que abrem telas do sistema ficam na Activity. */
    fun applyFix(fix: DiagnosticFix): Boolean = when (fix) {
        DiagnosticFix.ENABLE_PROTECTION -> {
            setProtectionEnabled(true)
            refreshDiagnostics()
            true
        }

        else -> false
    }

    // --- Backup -----------------------------------------------------------------

    fun suggestedBackupFileName(): String = backupRepository.suggestedFileName()

    /**
     * Grava o backup na URI escolhida pelo usuario no seletor do sistema.
     *
     * SAF, e nao uma pasta fixa: assim o app nao precisa de nenhuma permissao de
     * armazenamento e o arquivo nasce onde o dono mandou.
     */
    fun exportBackupTo(uri: Uri) = viewModelScope.launch {
        val resultado = runCatching {
            val texto = BackupCodec.encode(backupRepository.export())
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?.use { saida -> saida.write(texto.toByteArray(Charsets.UTF_8)) }
                    ?: error("não foi possível abrir o arquivo para escrita")
            }
            texto.length
        }
        _uiState.update {
            it.copy(
                backupMessage = resultado.fold(
                    onSuccess = { bytes -> "Backup salvo ($bytes caracteres)." },
                    onFailure = { erro -> "Não foi possível salvar: ${erro.message}" },
                ),
            )
        }
    }

    /**
     * Le e valida o arquivo, mas NAO aplica.
     *
     * A separacao entre ler e aplicar existe para que o usuario veja o que esta
     * entrando antes de perder o que tem: a importacao substitui as listas.
     */
    fun stageImportFrom(uri: Uri) = viewModelScope.launch {
        val leitura = runCatching {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { entrada -> entrada.readBytes().toString(Charsets.UTF_8) }
                    ?: error("não foi possível abrir o arquivo")
            }
        }
        val texto = leitura.getOrElse { erro ->
            _uiState.update { it.copy(backupMessage = "Falha ao ler: ${erro.message}") }
            return@launch
        }

        BackupCodec.decode(texto)
            .onSuccess { payload ->
                _uiState.update {
                    it.copy(
                        pendingImport = payload,
                        backupMessage = null,
                    )
                }
            }
            .onFailure { erro ->
                val mensagem = (erro as? BackupException)?.error?.message ?: erro.message
                _uiState.update { it.copy(pendingImport = null, backupMessage = mensagem) }
            }
    }

    fun confirmImport() = viewModelScope.launch {
        val payload: BackupPayload = _uiState.value.pendingImport ?: return@launch
        val resultado = runCatching { backupRepository.import(payload) }
        _uiState.update {
            it.copy(
                pendingImport = null,
                backupMessage = resultado.fold(
                    onSuccess = { "Regras importadas: ${payload.summary()}." },
                    onFailure = { erro -> "Falha ao importar: ${erro.message}" },
                ),
            )
        }
        refreshDiagnostics()
    }

    fun cancelImport() {
        _uiState.update { it.copy(pendingImport = null, backupMessage = "Importação cancelada.") }
    }

    /**
     * Libera um numero direto da tela de bloqueios.
     *
     * O numero ali ja esta normalizado -- foi essa a chave usada para bloquear --, entao
     * nao passa pelo normalizador de novo: reprocessar poderia gerar uma chave diferente
     * e a excecao nao pegaria.
     */
    fun allowlistBlockedNumber(normalizedNumber: String) = viewModelScope.launch {
        allowlistRepository.add(
            normalizedNumber = normalizedNumber,
            rawNumber = normalizedNumber,
            label = normalizedNumber,
        )
    }

    /**
     * Guarda o numero na allowlist ja normalizado, para que a comparacao no screening
     * use exatamente a mesma chave produzida a partir de `Call.Details`.
     *
     * @return `false` quando o texto digitado nao produz um numero utilizavel.
     */
    fun addToAllowlist(rawNumber: String, label: String): Boolean {
        val normalized = normalizer.normalize(rawNumber) ?: return false
        viewModelScope.launch {
            allowlistRepository.add(
                normalizedNumber = normalized,
                rawNumber = rawNumber.trim(),
                label = label.trim(),
            )
        }
        return true
    }

    fun removeFromAllowlist(normalizedNumber: String) = viewModelScope.launch {
        allowlistRepository.remove(normalizedNumber)
    }

    /**
     * Regenera o arquivo e devolve a URI de conteudo para abrir ou compartilhar.
     *
     * Uma URI do FileProvider, e nao um caminho "file://": desde o Android 7 entregar um
     * caminho direto a outro aplicativo lanca FileUriExposedException.
     */
    fun prepareLogFile(onReady: (android.net.Uri) -> Unit) = viewModelScope.launch {
        val resultado = runCatching {
            val arquivo = screeningLogRepository.writeLogFile()
            androidx.core.content.FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.fileprovider",
                arquivo,
            )
        }
        resultado.onSuccess { uri ->
            _uiState.update { it.copy(logStatusMessage = "Arquivo atualizado.") }
            onReady(uri)
        }.onFailure { erro ->
            _uiState.update {
                it.copy(logStatusMessage = "Não foi possível gerar o arquivo: ${erro.message}")
            }
        }
    }

    fun refreshLogFile() = viewModelScope.launch {
        val resultado = runCatching { screeningLogRepository.writeLogFile() }
        _uiState.update {
            it.copy(
                logStatusMessage = resultado.fold(
                    onSuccess = { arquivo -> "Arquivo atualizado (${arquivo.length()} bytes)." },
                    onFailure = { erro -> "Falha ao gerar: ${erro.message}" },
                ),
            )
        }
    }

    fun setLogStatusMessage(mensagem: String?) {
        _uiState.update { it.copy(logStatusMessage = mensagem) }
    }

    fun clearLogs() = viewModelScope.launch {
        screeningLogRepository.clear()
        _uiState.update { it.copy(logStatusMessage = "Registros apagados.") }
    }

    fun setSchedule(policy: SchedulePolicy) = viewModelScope.launch {
        settingsRepository.setSchedule(policy)
    }

    /**
     * Adiciona a blocklist, avisando quando o numero ja tem outra excecao.
     *
     * Nada e trocado em silencio: sem `force`, o conflito volta para a tela e o usuario
     * precisa confirmar de novo para que a excecao antiga seja removida.
     */
    fun addToBlocklist(rawNumber: String, label: String, force: Boolean): RuleConflict {
        val normalizado = normalizer.normalize(rawNumber) ?: return RuleConflict.INVALID_NUMBER
        val estado = _uiState.value
        if (!force && normalizado in estado.allowlistedNumbers) return RuleConflict.IN_ALLOWLIST
        viewModelScope.launch {
            if (normalizado in estado.allowlistedNumbers) allowlistRepository.remove(normalizado)
            blocklistRepository.add(normalizado, label.trim())
        }
        return RuleConflict.NONE
    }

    fun removeFromBlocklist(normalizedNumber: String) = viewModelScope.launch {
        blocklistRepository.remove(normalizedNumber)
    }

    fun addCustomRule(
        rawNumber: String,
        label: String,
        maxAllowedCalls: Int,
        windowMinutes: Int,
        force: Boolean,
    ): RuleConflict {
        val normalizado = normalizer.normalize(rawNumber) ?: return RuleConflict.INVALID_NUMBER
        val estado = _uiState.value
        if (!force && normalizado in estado.allowlistedNumbers) return RuleConflict.IN_ALLOWLIST
        if (!force && normalizado in estado.blocklistedNumbers) return RuleConflict.IN_BLOCKLIST
        viewModelScope.launch {
            if (normalizado in estado.allowlistedNumbers) allowlistRepository.remove(normalizado)
            if (normalizado in estado.blocklistedNumbers) blocklistRepository.remove(normalizado)
            customRuleRepository.upsert(
                normalizedNumber = normalizado,
                label = label.trim(),
                maxAllowedCalls = maxAllowedCalls,
                windowMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(
                    windowMinutes.toLong(),
                ),
            )
        }
        return RuleConflict.NONE
    }

    fun removeCustomRule(normalizedNumber: String) = viewModelScope.launch {
        customRuleRepository.remove(normalizedNumber)
    }

    fun clearBlockedCalls() = viewModelScope.launch {
        historyRepository.clearBlockedCalls()
        settingsRepository.resetBlockedTotal()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CallGuardViewModel(
                    application = application,
                    settingsRepository = ServiceLocator.settingsRepository(application),
                    allowlistRepository = ServiceLocator.allowlistRepository(application),
                    historyRepository = ServiceLocator.callHistoryRepository(application),
                    roleController = ServiceLocator.roleController(application),
                    contactLookup = ServiceLocator.contactLookup(application),
                    blockedCallNotifier = ServiceLocator.blockedCallNotifier(application),
                    screeningLogRepository = ServiceLocator.screeningLogRepository(application),
                    blocklistRepository = ServiceLocator.blocklistRepository(application),
                    customRuleRepository = ServiceLocator.customRuleRepository(application),
                    backupRepository = ServiceLocator.backupRepository(application),
                    diagnosticsRepository = ServiceLocator.diagnosticsRepository(application),
                    crashReporter = ServiceLocator.crashReporter(application),
                )
            }
        }
    }
}
