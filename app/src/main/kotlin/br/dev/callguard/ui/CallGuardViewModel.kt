package br.dev.callguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.dev.callguard.data.AllowlistRepository
import br.dev.callguard.data.CallHistoryRepository
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
                )
            }
        }
    }
}
