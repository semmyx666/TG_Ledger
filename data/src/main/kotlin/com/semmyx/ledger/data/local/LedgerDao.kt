package com.semmyx.ledger.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    // Bound LIKE patterns escape wildcard characters; no query-string SQL interpolation.
    @Query("""SELECT * FROM entries WHERE deletedAtMillis IS NULL AND currency = :currency
        AND (:type IS NULL OR type = :type)
        AND (:category IS NULL OR category = :category)
        AND (:from IS NULL OR epochDay >= :from) AND (:through IS NULL OR epochDay <= :through)
        AND searchText LIKE :pattern ESCAPE '\'
        ORDER BY epochDay DESC, createdAtMillis DESC, id DESC LIMIT :limit""")
    fun observeEntries(currency: String, pattern: String, type: String?, limit: Int, category: String? = null, from: Long? = null, through: Long? = null): Flow<List<EntryEntity>>

    @Query("""SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amountMinor ELSE 0 END),0) AS incomeMinor,
        COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amountMinor ELSE 0 END),0) AS expenseMinor,
        COUNT(*) AS entryCount FROM entries WHERE currency=:currency AND deletedAtMillis IS NULL""")
    fun observeBalance(currency: String): Flow<BalanceRow>

    @Query("""SELECT epochDay, SUM(CASE WHEN type='INCOME' THEN amountMinor ELSE 0 END) AS incomeMinor,
        SUM(CASE WHEN type='EXPENSE' THEN amountMinor ELSE 0 END) AS expenseMinor
        FROM entries WHERE currency=:currency AND deletedAtMillis IS NULL
        AND epochDay BETWEEN :from AND :through GROUP BY epochDay ORDER BY epochDay""")
    fun observeActivity(currency: String, from: Long, through: Long): Flow<List<ActivityRow>>

    @Query("""SELECT category, COALESCE(SUM(CASE WHEN type='INCOME' THEN amountMinor ELSE 0 END),0) AS incomeMinor,
        COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amountMinor ELSE 0 END),0) AS expenseMinor, COUNT(*) AS count,
        SUM(CASE WHEN type='INCOME' THEN 1 ELSE 0 END) AS incomeCount,
        SUM(CASE WHEN type='EXPENSE' THEN 1 ELSE 0 END) AS expenseCount
        FROM entries WHERE currency=:currency AND deletedAtMillis IS NULL AND epochDay BETWEEN :from AND :through
        GROUP BY category ORDER BY expenseMinor DESC""")
    fun observeCategoryTotals(currency: String, from: Long, through: Long): Flow<List<CategoryTotalRow>>

    @Query("""SELECT COALESCE(SUM(amountMinor),0) FROM entries WHERE deletedAtMillis IS NULL
        AND currency=:currency AND type=:type AND epochDay BETWEEN :from AND :through
        AND (:category IS NULL OR category=:category)""")
    suspend fun statistics(currency: String, from: Long, through: Long, type: String, category: String?): Long

    @Query("SELECT * FROM entries WHERE id=:id AND deletedAtMillis IS NULL")
    suspend fun find(id: String): EntryEntity?
    @Query("SELECT * FROM entries WHERE id=:id")
    suspend fun findIncludingDeleted(id: String): EntryEntity?
    @Insert suspend fun insert(entry: EntryEntity)
    @Update suspend fun update(entry: EntryEntity): Int
    @Transaction
    suspend fun save(entry: EntryEntity) {
        val existing = findIncludingDeleted(entry.id)
        if (existing == null) insert(entry)
        else {
            check(existing.deletedAtMillis == null) { "Cannot edit a deleted entry" }
            check(update(entry.copy(createdAtMillis = existing.createdAtMillis)) == 1)
        }
    }
    @Query("UPDATE entries SET deletedAtMillis=:now WHERE id=:id AND deletedAtMillis IS NULL")
    suspend fun delete(id: String, now: Long): Int
    @Query("UPDATE entries SET deletedAtMillis=NULL WHERE id=:id AND deletedAtMillis=:stamp")
    suspend fun undo(id: String, stamp: Long): Int
    @Query("DELETE FROM entries WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :before")
    suspend fun purge(before: Long)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates ORDER BY currency") fun observe(): Flow<List<RateEntity>>
    @Upsert suspend fun upsert(rates: List<RateEntity>)
}
