package br.dev.callguard.data

import br.dev.callguard.data.db.BlocklistDao
import br.dev.callguard.data.db.BlocklistEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Numeros que o usuario mandou nunca aceitar.
 *
 * Cache em memoria porque e consultado em toda ligacao; a fonte de verdade continua
 * sendo o banco, e toda leitura tem fallback para ele quando o cache esta frio.
 */
class BlocklistRepository(private val dao: BlocklistDao) {

    @Volatile
    private var cachedNumbers: Set<String>? = null

    fun observeEntries(): Flow<List<BlocklistEntryEntity>> = dao.observeAll()

    suspend fun warmUp() {
        cachedNumbers = dao.allNumbers().toSet()
    }

    fun onEntriesChanged(entries: List<BlocklistEntryEntity>) {
        cachedNumbers = entries.map { it.normalizedNumber }.toSet()
    }

    suspend fun contains(normalizedNumber: String): Boolean =
        cachedNumbers?.contains(normalizedNumber) ?: dao.contains(normalizedNumber)

    suspend fun add(normalizedNumber: String, label: String) {
        dao.upsert(
            BlocklistEntryEntity(
                normalizedNumber = normalizedNumber,
                label = label.ifBlank { normalizedNumber },
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
