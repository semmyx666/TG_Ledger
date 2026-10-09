package com.semmyx.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntryEntity::class, RateEntity::class, CustomCategoryEntity::class, BankInboxEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun rateDao(): RateDao
    abstract fun bankDao(): BankDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE entries ADD COLUMN bankId TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN cardLast4 TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN eventAtMillis INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN customCategoryId TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN origin TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,colorHex TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS bank_inbox (id TEXT NOT NULL PRIMARY KEY,eventKey TEXT NOT NULL,bankId TEXT NOT NULL,amountMinor INTEGER NOT NULL,currency TEXT NOT NULL,type TEXT NOT NULL,merchant TEXT NOT NULL,cardLast4 TEXT,eventAtMillis INTEGER NOT NULL,receivedAtMillis INTEGER NOT NULL,timeFromMessage INTEGER NOT NULL,transfer INTEGER NOT NULL,category TEXT NOT NULL,origin TEXT NOT NULL,status TEXT NOT NULL,entryId TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bank_inbox_eventKey ON bank_inbox(eventKey)")
            }
        }

        /**
         * v2 → v3: adds [groupKey] column and its index to bank_inbox.
         * groupKey links wallet-app + issuing-bank notifications for the same card tap
         * to support cross-source deduplication. NULL for existing rows is safe.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bank_inbox ADD COLUMN groupKey TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_inbox_groupKey ON bank_inbox(groupKey)")
            }
        }

        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(
            context.applicationContext, LedgerDatabase::class.java, "ledger.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        // No fallbackToDestructiveMigration — financial records must never be erased automatically.
    }
}
