package br.dev.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Historico de tentativas.
 *
 * Classe abstrata (e nao interface) porque `@Transaction` sobre um metodo com corpo so
 * e suportado por Room em `@Dao abstract class`.
 */
@Dao
abstract class CallAttemptDao {

    @Query(
        """
        SELECT timestamp_millis FROM call_attempts
        WHERE normalized_number = :number
          AND timestamp_millis > :windowStart
          AND timestamp_millis <= :now
        ORDER BY timestamp_millis ASC
        """,
    )
    abstract suspend fun attemptsInWindow(number: String, windowStart: Long, now: Long): List<Long>

    @Insert
    abstract suspend fun insert(attempt: CallAttemptEntity): Long

    @Query("DELETE FROM call_attempts WHERE timestamp_millis < :cutoff")
    abstract suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM call_attempts")
    abstract suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM call_attempts")
    abstract suspend fun count(): Int

    /**
     * Le a janela e grava a nova tentativa em UMA transacao.
     *
     * Este e o ponto onde a race condition apareceria: duas chamadas quase simultaneas
     * poderiam ler "2 anteriores" cada uma e ambas serem liberadas. Fazendo leitura e
     * escrita no mesmo `@Transaction`, o SQLite serializa as escritas e a segunda
     * chamada enxerga a tentativa registrada pela primeira.
     *
     * @return instantes das tentativas anteriores dentro da janela (sem a atual).
     */
    @Transaction
    open suspend fun recordAttemptAndGetPrevious(
        number: String,
        now: Long,
        windowMillis: Long,
        retentionMillis: Long,
    ): List<Long> {
        deleteOlderThan(now - retentionMillis)
        val previous = attemptsInWindow(
            number = number,
            windowStart = now - windowMillis,
            now = now,
        )
        insert(CallAttemptEntity(normalizedNumber = number, timestampMillis = now))
        return previous
    }
}
