package br.dev.callguard.data

import br.dev.callguard.core.CustomRule
import br.dev.callguard.data.db.CustomRuleDao
import br.dev.callguard.data.db.CustomRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Limites proprios por numero.
 *
 * O cache guarda o mapa inteiro porque a consulta acontece dentro do orcamento de tempo
 * do screening, e o volume esperado e de dezenas de regras, nao milhares.
 */
class CustomRuleRepository(private val dao: CustomRuleDao) {

    @Volatile
    private var cachedRules: Map<String, CustomRule>? = null

    fun observeRules(): Flow<List<CustomRuleEntity>> = dao.observeAll()

    suspend fun warmUp() {
        cachedRules = dao.all().associate { it.normalizedNumber to it.toDomain() }
    }

    fun onRulesChanged(entities: List<CustomRuleEntity>) {
        cachedRules = entities.associate { it.normalizedNumber to it.toDomain() }
    }

    suspend fun find(normalizedNumber: String): CustomRule? =
        cachedRules?.get(normalizedNumber) ?: dao.find(normalizedNumber)?.toDomain()

    /** Maior janela entre as regras ativas, para dimensionar a retencao do historico. */
    suspend fun largestWindowMillis(): Long =
        cachedRules?.values?.filter { it.enabled }?.maxOfOrNull { it.windowMillis }
            ?: dao.largestWindowMillis()
            ?: 0L

    suspend fun upsert(
        normalizedNumber: String,
        label: String,
        maxAllowedCalls: Int,
        windowMillis: Long,
        enabled: Boolean = true,
    ) {
        val agora = System.currentTimeMillis()
        val anterior = dao.find(normalizedNumber)
        dao.upsert(
            CustomRuleEntity(
                normalizedNumber = normalizedNumber,
                label = label.ifBlank { normalizedNumber },
                maxAllowedCalls = maxAllowedCalls.coerceIn(1, 50),
                windowMillis = windowMillis.coerceAtLeast(60_000L),
                enabled = enabled,
                createdAt = anterior?.createdAt ?: agora,
                updatedAt = agora,
            ),
        )
        warmUp()
    }

    suspend fun remove(normalizedNumber: String) {
        dao.delete(normalizedNumber)
        warmUp()
    }

    private fun CustomRuleEntity.toDomain() = CustomRule(
        normalizedNumber = normalizedNumber,
        maxAllowedCalls = maxAllowedCalls,
        windowMillis = windowMillis,
        enabled = enabled,
    )
}
