package br.dev.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BlockedCallDao {

    @Query("SELECT * FROM blocked_calls ORDER BY blocked_at DESC")
    abstract fun observeAll(): Flow<List<BlockedCallEntity>>

    @Insert
    abstract suspend fun insert(entry: BlockedCallEntity): Long

    @Query(
        """
        DELETE FROM blocked_calls WHERE id NOT IN (
            SELECT id FROM blocked_calls ORDER BY blocked_at DESC LIMIT :keep
        )
        """,
    )
    abstract suspend fun trimTo(keep: Int)

    @Query("DELETE FROM blocked_calls")
    abstract suspend fun deleteAll()

    /** Grava o bloqueio e mantem a tabela limitada, tudo em uma transacao. */
    @Transaction
    open suspend fun record(entry: BlockedCallEntity, keep: Int) {
        insert(entry)
        trimTo(keep)
    }
}
