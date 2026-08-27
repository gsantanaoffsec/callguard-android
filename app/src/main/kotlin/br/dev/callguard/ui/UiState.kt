package br.dev.callguard.ui

import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.data.db.AllowlistEntryEntity
import br.dev.callguard.data.db.BlockedCallEntity

/** Estado unico da tela principal. */
data class CallGuardUiState(
    val settings: ProtectionSettings = ProtectionSettings(),
    val roleAvailable: Boolean = true,
    val roleHeld: Boolean = false,
    val hasReadContactsPermission: Boolean = false,
    val canPostNotifications: Boolean = false,
    val allowlist: List<AllowlistEntryEntity> = emptyList(),
    val blockedCalls: List<BlockedCallEntity> = emptyList(),
    val blockedCallsTotal: Int = 0,
) {
    /** A regra so tem efeito real quando o papel foi concedido e a protecao esta ligada. */
    val isActuallyProtecting: Boolean get() = roleHeld && settings.protectionEnabled

    /** Modo 2 pedido pelo usuario mas ainda sem a permissao que o torna possivel. */
    val contactsModeNeedsPermission: Boolean
        get() = settings.applyToContacts && !hasReadContactsPermission

    /** Avisos ligados, mas sem permissao para notificar: os bloqueios passariam despercebidos. */
    val notificationsNeedPermission: Boolean
        get() = settings.notifyOnBlock && !canPostNotifications

    /** Numeros ja liberados, para a tela de bloqueios saber o que ja foi tratado. */
    val allowlistedNumbers: Set<String>
        get() = allowlist.map { it.normalizedNumber }.toSet()
}

/** Telas do app. Um `when` simples basta; nao ha grafo de navegacao para justificar. */
enum class CallGuardScreen { HOME, BLOCKED_CALLS }
