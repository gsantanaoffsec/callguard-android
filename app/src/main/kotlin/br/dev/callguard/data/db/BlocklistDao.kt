package br.dev.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {

    @Query("SELECT * FROM blocklist_entries ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BlocklistEntryEntity>>

    @Query("SELECT normalized_number FROM blocklist_entries")
    suspend fun allNumbers(): List<String>

    @Query("SELECT * FROM blocklist_entries ORDER BY created_at DESC")
    suspend fun all(): List<BlocklistEntryEntity>

    @Query("SELECT COUNT(*) FROM blocklist_entries")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM blocklist_entries WHERE normalized_number = :number)")
    suspend fun contains(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BlocklistEntryEntity)

    @Query("DELETE FROM blocklist_entries WHERE normalized_number = :number")
    suspend fun delete(number: String)

    @Query("DELETE FROM blocklist_entries")
    suspend fun deleteAll()
}
