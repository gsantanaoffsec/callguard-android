package br.dev.callguard.data

import br.dev.callguard.data.db.BlockedCallDao
import br.dev.callguard.data.db.BlockedCallEntity
import br.dev.callguard.data.db.CallAttemptDao
import kotlinx.coroutines.flow.Flow

/**
 * Historico de tentativas e de bloqueios.
 *
 * Toda a atomicidade mora nos `@Transaction` dos DAOs; este repositorio so traduz
 * vocabulario do dominio para o banco.
 */
class CallHistoryRepository(
    private val attemptDao: CallAttemptDao,
    private val blockedCallDao: BlockedCallDao,
) {

    /**
     * Registra a tentativa atual e devolve as anteriores da janela, atomicamente.
     *
     * Registramos SEMPRE que chegamos ate aqui, inclusive quando a chamada acaba
     * bloqueada: quem insiste durante o bloqueio continua alimentando a janela, entao a
     * protecao so relaxa depois de um silencio real de `windowMillis`.
     */
    suspend fun recordAttemptAndGetPrevious(
        normalizedNumber: String,
        nowMillis: Long,
        windowMillis: Long,
        largestWindowMillis: Long = windowMillis,
    ): List<Long> = attemptDao.recordAttemptAndGetPrevious(
        number = normalizedNumber,
        now = nowMillis,
        windowMillis = windowMillis,
        // A retencao precisa cobrir a MAIOR janela existente no sistema, e nao apenas a
        // desta chamada: com uma regra personalizada de 6 horas convivendo com a global
        // de 15 minutos, podar pela global apagaria tentativas que a outra ainda precisa.
        retentionMillis = maxOf(
            SettingsRepository.ATTEMPT_RETENTION_MILLIS,
            maxOf(windowMillis, largestWindowMillis) * 2,
        ),
    )

    suspend fun recordBlockedCall(
        normalizedNumber: String,
        blockedAtMillis: Long,
        attemptsInWindow: Int,
    ) = blockedCallDao.record(
        entry = BlockedCallEntity(
            normalizedNumber = normalizedNumber,
            blockedAt = blockedAtMillis,
            attemptsInWindow = attemptsInWindow,
        ),
        keep = MAX_BLOCKED_CALLS_KEPT,
    )

    fun observeBlockedCalls(): Flow<List<BlockedCallEntity>> = blockedCallDao.observeAll()

    suspend fun clearBlockedCalls() = blockedCallDao.deleteAll()

    suspend fun clearAttempts() = attemptDao.deleteAll()

    private companion object {
        /** A tela mostra "recentes"; nao ha motivo para guardar telefones indefinidamente. */
        const val MAX_BLOCKED_CALLS_KEPT = 100
    }
}
