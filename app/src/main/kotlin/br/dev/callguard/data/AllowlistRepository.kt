package br.dev.callguard.data

import br.dev.callguard.data.db.AllowlistDao
import br.dev.callguard.data.db.AllowlistEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Lista de excecoes, so no aparelho.
 *
 * Mantem um cache em memoria da lista de numeros para que a consulta feita durante o
 * screening seja imediata; o cache e realimentado pelo `Flow` do Room e tem fallback
 * para consulta direta quando ainda esta frio.
 */
class AllowlistRepository(private val dao: AllowlistDao) {

    @Volatile
    private var cachedNumbers: Set<String>? = null

    fun observeEntries(): Flow<List<AllowlistEntryEntity>> = dao.observeAll()

    suspend fun warmUp() {
        cachedNumbers = dao.allNumbers().toSet()
    }

    /** Atualiza o cache a partir do `Flow` observado pela camada de aplicacao. */
    fun onEntriesChanged(entries: List<AllowlistEntryEntity>) {
        cachedNumbers = entries.map { it.normalizedNumber }.toSet()
    }

    suspend fun contains(normalizedNumber: String): Boolean =
        cachedNumbers?.contains(normalizedNumber) ?: dao.contains(normalizedNumber)

    suspend fun add(normalizedNumber: String, rawNumber: String, label: String) {
        dao.upsert(
            AllowlistEntryEntity(
                normalizedNumber = normalizedNumber,
                label = label.ifBlank { rawNumber },
                rawNumber = rawNumber,
                createdAt = System.currentTimeMillis(),
            ),
        )
        cachedNumbers = (cachedNumbers ?: emptySet()) + normalizedNumber
    }

    suspend fun remove(normalizedNumber: String) {
        dao.delete(normalizedNumber)
        cachedNumbers = cachedNumbers?.minus(normalizedNumber)
    }
}
