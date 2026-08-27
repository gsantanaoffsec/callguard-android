package br.dev.callguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Banco local unico do app. Nunca sai do aparelho.
 *
 * Room foi escolhido para estes tres conjuntos porque todos precisam de consulta
 * indexada por chave, remocao por faixa de tempo e transacao -- coisas que um
 * arquivo de preferencias faria mal.
 */
@Database(
    entities = [
        CallAttemptEntity::class,
        AllowlistEntryEntity::class,
        BlockedCallEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CallGuardDatabase : RoomDatabase() {

    abstract fun callAttemptDao(): CallAttemptDao

    abstract fun allowlistDao(): AllowlistDao

    abstract fun blockedCallDao(): BlockedCallDao

    companion object {
        private const val DATABASE_NAME = "callguard.db"

        fun build(context: Context): CallGuardDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CallGuardDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
