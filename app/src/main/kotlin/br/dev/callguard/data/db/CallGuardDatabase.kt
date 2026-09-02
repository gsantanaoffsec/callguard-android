package br.dev.callguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versao do esquema.
 *
 * Constante de arquivo para que a anotacao `@Database` e a tela de diagnostico leiam
 * exatamente o mesmo numero -- um laudo que informa a versao errada do banco e pior do
 * que um que nao informa nada.
 */
const val DATABASE_VERSION = 4

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
        BlocklistEntryEntity::class,
        CustomRuleEntity::class,
        PatternRuleEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
abstract class CallGuardDatabase : RoomDatabase() {

    abstract fun callAttemptDao(): CallAttemptDao

    abstract fun allowlistDao(): AllowlistDao

    abstract fun blockedCallDao(): BlockedCallDao

    abstract fun screeningEventDao(): ScreeningEventDao

    abstract fun blocklistDao(): BlocklistDao

    abstract fun customRuleDao(): CustomRuleDao

    abstract fun patternRuleDao(): PatternRuleDao

    companion object {
        private const val DATABASE_NAME = "callguard.db"

        /** Reexposta aqui para quem so tem a referencia da classe. */
        const val VERSION = DATABASE_VERSION

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

        /**
         * v2 -> v3: blocklist e regras personalizadas.
         *
         * Migracao de verdade. `fallbackToDestructiveMigration` apagaria a lista de
         * excecoes e o historico de quem ja usa o app -- dado real do usuario nao se
         * descarta para encurtar caminho.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocklist_entries` (" +
                        "`normalized_number` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`normalized_number`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_rules` (" +
                        "`normalized_number` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`max_allowed_calls` INTEGER NOT NULL, " +
                        "`window_millis` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`normalized_number`))",
                )
            }
        }

        /**
         * v3 -> v4: bloqueio por faixa de numeros.
         *
         * Escrita a mao e conferida contra o schema que o KSP exporta. Chave composta:
         * "comeca com 11" e "contem 11" sao regras distintas.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pattern_rules` (" +
                        "`digits` TEXT NOT NULL, " +
                        "`match_kind` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`digits`, `match_kind`))",
                )
            }
        }

        fun build(context: Context): CallGuardDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CallGuardDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
