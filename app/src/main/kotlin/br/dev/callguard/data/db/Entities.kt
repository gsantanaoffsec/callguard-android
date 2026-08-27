package br.dev.callguard.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma tentativa de chamada recebida, ja normalizada.
 *
 * E o dado minimo necessario para reconstruir a janela deslizante depois que o processo
 * do app for morto ou o aparelho reiniciado -- por isso guardamos o instante, e nao um
 * contador. Um contador nao sabe envelhecer.
 */
@Entity(
    tableName = "call_attempts",
    indices = [Index(value = ["normalized_number", "timestamp_millis"])],
)
data class CallAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "normalized_number") val normalizedNumber: String,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
)

/** Numero que nunca deve ser bloqueado, independentemente da quantidade de ligacoes. */
@Entity(tableName = "allowlist_entries")
data class AllowlistEntryEntity(
    @PrimaryKey @ColumnInfo(name = "normalized_number") val normalizedNumber: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "raw_number") val rawNumber: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Registro exibido na tela "Chamadas bloqueadas recentemente". */
@Entity(
    tableName = "blocked_calls",
    indices = [Index(value = ["blocked_at"])],
)
data class BlockedCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "normalized_number") val normalizedNumber: String,
    @ColumnInfo(name = "blocked_at") val blockedAt: Long,
    @ColumnInfo(name = "attempts_in_window") val attemptsInWindow: Int,
)
