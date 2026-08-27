package br.dev.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowlistDao {

    @Query("SELECT * FROM allowlist_entries ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AllowlistEntryEntity>>

    @Query("SELECT normalized_number FROM allowlist_entries")
    suspend fun allNumbers(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM allowlist_entries WHERE normalized_number = :number)")
    suspend fun contains(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AllowlistEntryEntity)

    @Query("DELETE FROM allowlist_entries WHERE normalized_number = :number")
    suspend fun delete(number: String)
}
