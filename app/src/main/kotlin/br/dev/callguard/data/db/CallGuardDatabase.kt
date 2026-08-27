package br.dev.callguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        ScreeningEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CallGuardDatabase : RoomDatabase() {

    abstract fun callAttemptDao(): CallAttemptDao

    abstract fun allowlistDao(): AllowlistDao

    abstract fun blockedCallDao(): BlockedCallDao

    abstract fun screeningEventDao(): ScreeningEventDao

    companion object {
        private const val DATABASE_NAME = "callguard.db"

        /**
         * v1 -> v2: tabela do log legivel de decisoes.
         *
         * Migracao de verdade, e nao `fallbackToDestructiveMigration`: quem ja tem o app
         * instalado perderia a lista de excecoes e as configuracoes.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `screening_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`occurred_at` INTEGER NOT NULL, " +
                        "`normalized_number` TEXT, " +
                        "`blocked` INTEGER NOT NULL, " +
                        "`reason` TEXT NOT NULL, " +
                        "`attempts_in_window` INTEGER NOT NULL, " +
                        "`verification_status` INTEGER)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_screening_events_occurred_at` " +
                        "ON `screening_events` (`occurred_at`)",
                )
            }
        }

        fun build(context: Context): CallGuardDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CallGuardDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2).build()
    }
}
