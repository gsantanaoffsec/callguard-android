package br.dev.callguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.dev.callguard.core.ProtectionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "callguard_settings",
)

/**
 * Preferencias do usuario.
 *
 * DataStore (e nao Room) porque sao meia duzia de valores escalares sem relacionamento
 * nem consulta -- Room aqui seria peso morto. DataStore ainda entrega um `Flow` que a
 * UI observa de graca.
 *
 * A leitura do DataStore e assincrona e a primeira delas toca o disco. Como o
 * CallScreeningService tem 5 s de orcamento, mantemos um cache em memoria alimentado
 * pelo proprio `Flow`; `current()` usa o cache quando ele ja esta quente e cai para uma
 * leitura direta quando nao esta.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    @Volatile
    private var cachedSettings: ProtectionSettings? = null

    // O cache e preenchido em warmUp() e reescrito em cada gravacao.
    val settings: Flow<ProtectionSettings> = dataStore.data.map { it.toProtectionSettings() }

    val blockedCallsTotal: Flow<Int> = dataStore.data.map { it[KEY_BLOCKED_TOTAL] ?: 0 }

    /** Leitura rapida usada pelo servico de screening. */
    suspend fun current(): ProtectionSettings =
        cachedSettings ?: settings.first().also { cachedSettings = it }

    /** Chamado no `onCreate` da Application para deixar o cache quente antes da 1a chamada. */
    suspend fun warmUp() {
        cachedSettings = settings.first()
    }

    suspend fun setProtectionEnabled(enabled: Boolean) = update { it[KEY_ENABLED] = enabled }

    suspend fun setMaxAllowedCalls(value: Int) = update { it[KEY_MAX_CALLS] = value }

    suspend fun setWindowMinutes(value: Int) = update { it[KEY_WINDOW_MINUTES] = value }

    suspend fun setApplyToContacts(value: Boolean) = update { it[KEY_APPLY_TO_CONTACTS] = value }

    suspend fun incrementBlockedTotal() {
        dataStore.edit { it[KEY_BLOCKED_TOTAL] = (it[KEY_BLOCKED_TOTAL] ?: 0) + 1 }
    }

    suspend fun resetBlockedTotal() {
        dataStore.edit { it[KEY_BLOCKED_TOTAL] = 0 }
    }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val updated = dataStore.edit(block)
        cachedSettings = updated.toProtectionSettings()
    }

    private fun Preferences.toProtectionSettings(): ProtectionSettings =
        ProtectionSettings.sanitized(
            protectionEnabled = this[KEY_ENABLED] ?: ProtectionSettings.DEFAULT_PROTECTION_ENABLED,
            maxAllowedCalls = this[KEY_MAX_CALLS] ?: ProtectionSettings.DEFAULT_MAX_ALLOWED_CALLS,
            windowMinutes = this[KEY_WINDOW_MINUTES] ?: ProtectionSettings.DEFAULT_WINDOW_MINUTES,
            applyToContacts = this[KEY_APPLY_TO_CONTACTS]
                ?: ProtectionSettings.DEFAULT_APPLY_TO_CONTACTS,
        )

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("protection_enabled")
        private val KEY_MAX_CALLS = intPreferencesKey("max_allowed_calls")
        private val KEY_WINDOW_MINUTES = intPreferencesKey("window_minutes")
        private val KEY_APPLY_TO_CONTACTS = booleanPreferencesKey("apply_to_contacts")
        private val KEY_BLOCKED_TOTAL = intPreferencesKey("blocked_calls_total")

        /** Quanto tempo de historico de tentativas guardamos alem da maior janela oferecida. */
        val ATTEMPT_RETENTION_MILLIS: Long = TimeUnit.HOURS.toMillis(6)
    }
}
