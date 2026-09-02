package br.dev.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternRuleDao {

    @Query("SELECT * FROM pattern_rules ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PatternRuleEntity>>

    @Query("SELECT * FROM pattern_rules ORDER BY created_at DESC")
    suspend fun all(): List<PatternRuleEntity>

    @Query("SELECT COUNT(*) FROM pattern_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: PatternRuleEntity)

    @Query("DELETE FROM pattern_rules WHERE digits = :digits AND match_kind = :matchKind")
    suspend fun delete(digits: String, matchKind: String)

    @Query("DELETE FROM pattern_rules")
    suspend fun deleteAll()
}
