package br.dev.callguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomRuleDao {

    @Query("SELECT * FROM custom_rules ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<CustomRuleEntity>>

    @Query("SELECT * FROM custom_rules")
    suspend fun all(): List<CustomRuleEntity>

    @Query("SELECT COUNT(*) FROM custom_rules")
    suspend fun count(): Int

    @Query("SELECT * FROM custom_rules WHERE normalized_number = :number")
    suspend fun find(number: String): CustomRuleEntity?

    /**
     * Maior janela entre as regras ativas.
     *
     * A retencao do historico precisa cobrir a maior janela existente no sistema, senao
     * uma regra de 6 horas perderia tentativas apagadas por causa da regra global de 15
     * minutos. `null` quando nao ha nenhuma regra ativa.
     */
    @Query("SELECT MAX(window_millis) FROM custom_rules WHERE enabled = 1")
    suspend fun largestWindowMillis(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CustomRuleEntity)

    @Query("DELETE FROM custom_rules WHERE normalized_number = :number")
    suspend fun delete(number: String)

    @Query("DELETE FROM custom_rules")
    suspend fun deleteAll()
}
