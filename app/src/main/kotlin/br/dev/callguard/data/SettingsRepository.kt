package br.dev.callguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.dev.callguard.core.ProtectionSettings
import br.dev.callguard.core.SchedulePolicy
import java.time.DayOfWeek
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

    @Volatile
    private var cachedSchedule: SchedulePolicy? = null

    // O cache e preenchido em warmUp() e reescrito em cada gravacao.
    val settings: Flow<ProtectionSettings> = dataStore.data.map { it.toProtectionSettings() }

    val blockedCallsTotal: Flow<Int> = dataStore.data.map { it[KEY_BLOCKED_TOTAL] ?: 0 }

    /**
     * Perfil por horario. Fica no DataStore, e nao no Room, porque e configuracao
     * escalar do usuario -- nao ha consulta nem relacionamento a fazer sobre ele.
     */
    val schedule: Flow<SchedulePolicy> = dataStore.data.map { it.toSchedulePolicy() }

    /** Leitura rapida usada pelo servico de screening. */
    suspend fun current(): ProtectionSettings =
        cachedSettings ?: settings.first().also { cachedSettings = it }

    suspend fun currentSchedule(): SchedulePolicy =
        cachedSchedule ?: schedule.first().also { cachedSchedule = it }

    /** Chamado no `onCreate` da Application para deixar o cache quente antes da 1a chamada. */
    suspend fun warmUp() {
        cachedSettings = settings.first()
        cachedSchedule = schedule.first()
    }

    suspend fun setProtectionEnabled(enabled: Boolean) = update { it[KEY_ENABLED] = enabled }

    suspend fun setMaxAllowedCalls(value: Int) = update { it[KEY_MAX_CALLS] = value }

    suspend fun setWindowMinutes(value: Int) = update { it[KEY_WINDOW_MINUTES] = value }

    suspend fun setApplyToContacts(value: Boolean) = update { it[KEY_APPLY_TO_CONTACTS] = value }

    suspend fun setNotifyOnBlock(value: Boolean) = update { it[KEY_NOTIFY_ON_BLOCK] = value }

    suspend fun setBiometricLock(value: Boolean) = update { it[KEY_BIOMETRIC_LOCK] = value }

    suspend fun setSchedule(policy: SchedulePolicy) {
        dataStore.edit {
            it[KEY_SCHEDULE_ENABLED] = policy.enabled
            it[KEY_SCHEDULE_START] = policy.startMinuteOfDay
            it[KEY_SCHEDULE_END] = policy.endMinuteOfDay
            it[KEY_SCHEDULE_DAYS] = policy.activeDays.map { d -> d.name }.toSet()
            it[KEY_SCHEDULE_MAX_CALLS] = policy.maxAllowedCalls
            it[KEY_SCHEDULE_WINDOW] = policy.windowMillis
        }
        cachedSchedule = policy
    }

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

    private fun Preferences.toSchedulePolicy(): SchedulePolicy {
        val dias = this[KEY_SCHEDULE_DAYS]
            ?.mapNotNull { nome -> runCatching { DayOfWeek.valueOf(nome) }.getOrNull() }
            ?.toSet()
            ?: DayOfWeek.entries.toSet()
        return SchedulePolicy(
            enabled = this[KEY_SCHEDULE_ENABLED] ?: false,
            startMinuteOfDay = (this[KEY_SCHEDULE_START] ?: SchedulePolicy.DEFAULT_START_MINUTE)
                .coerceIn(0, 24 * 60 - 1),
            endMinuteOfDay = (this[KEY_SCHEDULE_END] ?: SchedulePolicy.DEFAULT_END_MINUTE)
                .coerceIn(0, 24 * 60 - 1),
            activeDays = dias,
            maxAllowedCalls = (this[KEY_SCHEDULE_MAX_CALLS] ?: 1).coerceIn(1, 50),
            windowMillis = (this[KEY_SCHEDULE_WINDOW] ?: 30 * 60_000L).coerceAtLeast(60_000L),
        )
    }

    private fun Preferences.toProtectionSettings(): ProtectionSettings =
        ProtectionSettings.sanitized(
            protectionEnabled = this[KEY_ENABLED] ?: ProtectionSettings.DEFAULT_PROTECTION_ENABLED,
            maxAllowedCalls = this[KEY_MAX_CALLS] ?: ProtectionSettings.DEFAULT_MAX_ALLOWED_CALLS,
            windowMinutes = this[KEY_WINDOW_MINUTES] ?: ProtectionSettings.DEFAULT_WINDOW_MINUTES,
            applyToContacts = this[KEY_APPLY_TO_CONTACTS]
                ?: ProtectionSettings.DEFAULT_APPLY_TO_CONTACTS,
            notifyOnBlock = this[KEY_NOTIFY_ON_BLOCK]
                ?: ProtectionSettings.DEFAULT_NOTIFY_ON_BLOCK,
            biometricLockEnabled = this[KEY_BIOMETRIC_LOCK]
                ?: ProtectionSettings.DEFAULT_BIOMETRIC_LOCK,
        )

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("protection_enabled")
        private val KEY_MAX_CALLS = intPreferencesKey("max_allowed_calls")
        private val KEY_WINDOW_MINUTES = intPreferencesKey("window_minutes")
        private val KEY_APPLY_TO_CONTACTS = booleanPreferencesKey("apply_to_contacts")
        private val KEY_NOTIFY_ON_BLOCK = booleanPreferencesKey("notify_on_block")
        private val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        private val KEY_BLOCKED_TOTAL = intPreferencesKey("blocked_calls_total")
        private val KEY_SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        private val KEY_SCHEDULE_START = intPreferencesKey("schedule_start_minute")
        private val KEY_SCHEDULE_END = intPreferencesKey("schedule_end_minute")
        private val KEY_SCHEDULE_DAYS = stringSetPreferencesKey("schedule_days")
        private val KEY_SCHEDULE_MAX_CALLS = intPreferencesKey("schedule_max_calls")
        private val KEY_SCHEDULE_WINDOW = longPreferencesKey("schedule_window_millis")

        /** Quanto tempo de historico de tentativas guardamos alem da maior janela oferecida. */
        val ATTEMPT_RETENTION_MILLIS: Long = TimeUnit.HOURS.toMillis(6)
    }
}
