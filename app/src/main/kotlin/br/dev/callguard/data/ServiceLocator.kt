package br.dev.callguard.data

import android.content.Context
import br.dev.callguard.core.CallScreeningPolicy
import br.dev.callguard.core.PhoneNumberNormalizer
import br.dev.callguard.data.db.CallGuardDatabase
import br.dev.callguard.phone.ContactLookup
import br.dev.callguard.phone.TelephonyPhoneNumberNormalizer
import br.dev.callguard.screening.BlockedCallNotifier
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
    @Volatile private var blockedCallNotifier: BlockedCallNotifier? = null
    @Volatile private var screeningLogRepository: ScreeningLogRepository? = null
    @Volatile private var blocklistRepository: BlocklistRepository? = null
    @Volatile private var customRuleRepository: CustomRuleRepository? = null
    @Volatile private var backupRepository: BackupRepository? = null
    @Volatile private var diagnosticsRepository: DiagnosticsRepository? = null
    @Volatile private var crashReporter: CrashReporter? = null
    @Volatile private var patternRuleRepository: PatternRuleRepository? = null

    private val policy = CallScreeningPolicy()

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

    fun blockedCallNotifier(context: Context): BlockedCallNotifier =
        blockedCallNotifier ?: synchronized(lock) {
            blockedCallNotifier ?: BlockedCallNotifier(context).also { blockedCallNotifier = it }
        }

    fun screeningLogRepository(context: Context): ScreeningLogRepository =
        screeningLogRepository ?: synchronized(lock) {
            screeningLogRepository ?: ScreeningLogRepository(
                context = context,
                dao = database(context).screeningEventDao(),
            ).also { screeningLogRepository = it }
        }

    fun blocklistRepository(context: Context): BlocklistRepository =
        blocklistRepository ?: synchronized(lock) {
            blocklistRepository ?: BlocklistRepository(database(context).blocklistDao())
                .also { blocklistRepository = it }
        }

    fun customRuleRepository(context: Context): CustomRuleRepository =
        customRuleRepository ?: synchronized(lock) {
            customRuleRepository ?: CustomRuleRepository(database(context).customRuleDao())
                .also { customRuleRepository = it }
        }

    fun backupRepository(context: Context): BackupRepository =
        backupRepository ?: synchronized(lock) {
            backupRepository ?: BackupRepository(
                settingsRepository = settingsRepository(context),
                allowlistDao = database(context).allowlistDao(),
                blocklistDao = database(context).blocklistDao(),
                customRuleDao = database(context).customRuleDao(),
                appVersionName = versionName(context),
            ).also { backupRepository = it }
        }

    fun diagnosticsRepository(context: Context): DiagnosticsRepository =
        diagnosticsRepository ?: synchronized(lock) {
            diagnosticsRepository ?: DiagnosticsRepository(
                context = context,
                database = database(context),
                settingsRepository = settingsRepository(context),
                allowlistRepository = allowlistRepository(context),
                blocklistRepository = blocklistRepository(context),
                customRuleRepository = customRuleRepository(context),
                roleController = roleController(context),
                contactLookup = contactLookup(context),
                notifier = blockedCallNotifier(context),
                normalizer = phoneNumberNormalizer(context),
                policy = policy,
            ).also { diagnosticsRepository = it }
        }

    fun patternRuleRepository(context: Context): PatternRuleRepository =
        patternRuleRepository ?: synchronized(lock) {
            patternRuleRepository ?: PatternRuleRepository(database(context).patternRuleDao())
                .also { patternRuleRepository = it }
        }

    fun crashReporter(context: Context): CrashReporter =
        crashReporter ?: synchronized(lock) {
            crashReporter ?: CrashReporter(context).also { crashReporter = it }
        }

    fun policy(): CallScreeningPolicy = policy

    /** Nome da versao instalada, gravado no cabecalho do backup. */
    private fun versionName(context: Context): String = runCatching {
        val ctx = context.applicationContext
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "desconhecida"
}
