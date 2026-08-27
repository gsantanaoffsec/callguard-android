package br.dev.callguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.dev.callguard.data.AllowlistRepository
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
            screeningLogRepository.observeEvents().collect { eventos ->
                _uiState.update { it.copy(screeningEvents = eventos) }
            }
        }
        _uiState.update { it.copy(logFilePath = screeningLogRepository.friendlyLogPath()) }
        refreshSystemState()
    }

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
                )
            }
        }
    }
}
