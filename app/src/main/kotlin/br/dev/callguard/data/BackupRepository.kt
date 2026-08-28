package br.dev.callguard.data

import br.dev.callguard.core.BackupNumber
import br.dev.callguard.core.BackupPayload
import br.dev.callguard.core.BackupRule
import br.dev.callguard.data.db.AllowlistDao
import br.dev.callguard.data.db.BlocklistDao
import br.dev.callguard.data.db.CustomRuleDao
import kotlinx.coroutines.flow.first

/**
 * Exportacao e importacao das regras.
 *
 * A importacao SUBSTITUI as listas em vez de mesclar. Mesclar parece mais gentil, mas
 * produz um estado que ninguem escolheu: um numero permitido no aparelho antigo e
 * bloqueado no novo teria que virar um dos dois em silencio. Substituir e previsivel --
 * depois de importar, a configuracao e exatamente a do arquivo -- e a tela pede
 * confirmacao explicita antes, mostrando o que vai entrar.
 */
class BackupRepository(
    private val settingsRepository: SettingsRepository,
    private val allowlistDao: AllowlistDao,
    private val blocklistDao: BlocklistDao,
    private val customRuleDao: CustomRuleDao,
    private val appVersionName: String,
) {

    suspend fun export(nowMillis: Long = System.currentTimeMillis()): BackupPayload =
        BackupPayload(
            exportedAtMillis = nowMillis,
            appVersionName = appVersionName,
            settings = settingsRepository.settings.first(),
            schedule = settingsRepository.schedule.first(),
            allowlist = allowlistDao.all().map { BackupNumber(it.normalizedNumber, it.label) },
            blocklist = blocklistDao.all().map { BackupNumber(it.normalizedNumber, it.label) },
            customRules = customRuleDao.all().map {
                BackupRule(
                    normalizedNumber = it.normalizedNumber,
                    label = it.label,
                    maxAllowedCalls = it.maxAllowedCalls,
                    windowMillis = it.windowMillis,
                    enabled = it.enabled,
                )
            },
        )

    /**
     * Aplica um backup ja validado.
     *
     * Um numero que aparece nas duas listas do arquivo fica apenas na de bloqueados: e o
     * mesmo criterio da tela de Regras, onde bloquear um numero permitido remove a
     * permissao. Duas verdades opostas sobre o mesmo numero nao podem sobreviver a
     * importacao.
     */
    suspend fun import(payload: BackupPayload) {
        val agora = System.currentTimeMillis()
        val bloqueados = payload.blocklist.map { it.normalizedNumber }.toSet()

        allowlistDao.deleteAll()
        blocklistDao.deleteAll()
        customRuleDao.deleteAll()

        payload.allowlist
            .filterNot { it.normalizedNumber in bloqueados }
            .forEach { entrada ->
                allowlistDao.upsert(
                    br.dev.callguard.data.db.AllowlistEntryEntity(
                        normalizedNumber = entrada.normalizedNumber,
                        label = entrada.label.ifBlank { entrada.normalizedNumber },
                        rawNumber = entrada.normalizedNumber,
                        createdAt = agora,
                    ),
                )
            }

        payload.blocklist.forEach { entrada ->
            blocklistDao.upsert(
                br.dev.callguard.data.db.BlocklistEntryEntity(
                    normalizedNumber = entrada.normalizedNumber,
                    label = entrada.label.ifBlank { entrada.normalizedNumber },
                    createdAt = agora,
                ),
            )
        }

        payload.customRules.forEach { regra ->
            customRuleDao.upsert(
                br.dev.callguard.data.db.CustomRuleEntity(
                    normalizedNumber = regra.normalizedNumber,
                    label = regra.label.ifBlank { regra.normalizedNumber },
                    maxAllowedCalls = regra.maxAllowedCalls,
                    windowMillis = regra.windowMillis,
                    enabled = regra.enabled,
                    createdAt = agora,
                    updatedAt = agora,
                ),
            )
        }

        settingsRepository.setProtectionEnabled(payload.settings.protectionEnabled)
        settingsRepository.setMaxAllowedCalls(payload.settings.maxAllowedCalls)
        settingsRepository.setWindowMinutes(payload.settings.windowMinutes)
        settingsRepository.setApplyToContacts(payload.settings.applyToContacts)
        settingsRepository.setNotifyOnBlock(payload.settings.notifyOnBlock)
        settingsRepository.setBiometricLock(payload.settings.biometricLockEnabled)
        settingsRepository.setSchedule(payload.schedule)
    }

    /** Nome sugerido do arquivo. Data no nome porque o usuario vai ter varios. */
    fun suggestedFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val data = java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return "callguard-regras-$data.json"
    }
}
