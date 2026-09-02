package br.dev.callguard.data

import br.dev.callguard.core.NumberPattern
import br.dev.callguard.core.firstMatching
import br.dev.callguard.data.db.PatternRuleDao
import br.dev.callguard.data.db.PatternRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Regras de faixa de números.
 *
 * O cache guarda a lista inteira porque a consulta acontece dentro do orçamento de tempo
 * do screening e, ao contrário da allowlist, não dá para responder com um `EXISTS` no
 * SQLite: casar prefixo exigiria um `LIKE` por regra, e o número precisa ser testado nas
 * duas formas (com e sem código do país). Em memória isso é uma varredura sobre dezenas
 * de itens — barato e previsível.
 */
class PatternRuleRepository(private val dao: PatternRuleDao) {

    @Volatile
    private var cached: List<NumberPattern>? = null

    fun observeRules(): Flow<List<PatternRuleEntity>> = dao.observeAll()

    suspend fun warmUp() {
        cached = dao.all().map { it.toDomain() }
    }

    fun onRulesChanged(entities: List<PatternRuleEntity>) {
        cached = entities.map { it.toDomain() }
    }

    /** O primeiro padrão que pega o número, ou `null`. Fallback para o banco a frio. */
    suspend fun matching(normalizedNumber: String?): NumberPattern? {
        val lista = cached ?: dao.all().map { it.toDomain() }.also { cached = it }
        return lista.firstMatching(normalizedNumber)
    }

    suspend fun upsert(pattern: NumberPattern) {
        dao.upsert(
            PatternRuleEntity(
                digits = pattern.digits,
                matchKind = pattern.kind.name,
                label = pattern.label,
                enabled = pattern.enabled,
                createdAt = System.currentTimeMillis(),
            ),
        )
        warmUp()
    }

    suspend fun remove(digits: String, kind: NumberPattern.MatchKind) {
        dao.delete(digits, kind.name)
        warmUp()
    }

    private fun PatternRuleEntity.toDomain() = NumberPattern(
        digits = digits,
        label = label,
        // Um valor desconhecido no banco vira o modo mais restrito em vez de estourar:
        // uma regra que a pessoa criou nao deveria sumir por causa de um enum renomeado.
        kind = runCatching { NumberPattern.MatchKind.valueOf(matchKind) }
            .getOrDefault(NumberPattern.MatchKind.STARTS_WITH),
        enabled = enabled,
    )
}
