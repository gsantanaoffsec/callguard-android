package br.dev.callguard.data

import android.content.Context
import br.dev.callguard.core.InsistentCallPolicy
import br.dev.callguard.core.PhoneNumberNormalizer
import br.dev.callguard.data.db.CallGuardDatabase
import br.dev.callguard.phone.ContactLookup
import br.dev.callguard.phone.TelephonyPhoneNumberNormalizer
import br.dev.callguard.screening.CallScreeningRoleController

/**
 * Injecao de dependencia manual.
 *
 * O projeto tem tres repositorios e uma politica; Hilt aqui seria mais cerimonia do que
 * beneficio. O que importa e a garantia de instancia unica por processo -- especialmente
 * do DataStore, que quebra se for aberto duas vezes sobre o mesmo arquivo.
 *
 * O `CallScreeningService` e a UI vivem no mesmo processo, entao ambos enxergam estes
 * mesmos objetos (e os mesmos caches).
 */
object ServiceLocator {

    @Volatile private var database: CallGuardDatabase? = null
    @Volatile private var settingsRepository: SettingsRepository? = null
    @Volatile private var callHistoryRepository: CallHistoryRepository? = null
    @Volatile private var allowlistRepository: AllowlistRepository? = null
    @Volatile private var normalizer: PhoneNumberNormalizer? = null
    @Volatile private var contactLookup: ContactLookup? = null
    @Volatile private var roleController: CallScreeningRoleController? = null

    private val policy = InsistentCallPolicy()

    private val lock = Any()

    fun database(context: Context): CallGuardDatabase =
        database ?: synchronized(lock) {
            database ?: CallGuardDatabase.build(context).also { database = it }
        }

    fun settingsRepository(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(lock) {
            settingsRepository ?: SettingsRepository(context).also { settingsRepository = it }
        }

    fun callHistoryRepository(context: Context): CallHistoryRepository =
        callHistoryRepository ?: synchronized(lock) {
            callHistoryRepository ?: CallHistoryRepository(
                attemptDao = database(context).callAttemptDao(),
                blockedCallDao = database(context).blockedCallDao(),
            ).also { callHistoryRepository = it }
        }

    fun allowlistRepository(context: Context): AllowlistRepository =
        allowlistRepository ?: synchronized(lock) {
            allowlistRepository ?: AllowlistRepository(database(context).allowlistDao())
                .also { allowlistRepository = it }
        }

    fun phoneNumberNormalizer(context: Context): PhoneNumberNormalizer =
        normalizer ?: synchronized(lock) {
            normalizer ?: TelephonyPhoneNumberNormalizer(context).also { normalizer = it }
        }

    fun contactLookup(context: Context): ContactLookup =
        contactLookup ?: synchronized(lock) {
            contactLookup ?: ContactLookup(context).also { contactLookup = it }
        }

    fun roleController(context: Context): CallScreeningRoleController =
        roleController ?: synchronized(lock) {
            roleController ?: CallScreeningRoleController(context).also { roleController = it }
        }

    fun policy(): InsistentCallPolicy = policy
}
