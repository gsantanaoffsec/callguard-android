package br.dev.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScreeningEventDao {

    @Query("SELECT * FROM screening_events ORDER BY occurred_at DESC")
    abstract fun observeAll(): Flow<List<ScreeningEventEntity>>

    @Query("SELECT * FROM screening_events ORDER BY occurred_at DESC LIMIT :limit")
    abstract suspend fun recent(limit: Int): List<ScreeningEventEntity>

    @Insert
    abstract suspend fun insert(event: ScreeningEventEntity): Long

    @Query(
        """
        DELETE FROM screening_events WHERE id NOT IN (
            SELECT id FROM screening_events ORDER BY occurred_at DESC LIMIT :keep
        )
        """,
    )
    abstract suspend fun trimTo(keep: Int)

    @Query("DELETE FROM screening_events")
    abstract suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM screening_events")
    abstract suspend fun count(): Int

    /** Grava o evento e mantem a tabela limitada, numa transacao so. */
    @Transaction
    open suspend fun record(event: ScreeningEventEntity, keep: Int) {
        insert(event)
        trimTo(keep)
    }
}
