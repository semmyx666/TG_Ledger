package com.semmyx.ledger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(@PrimaryKey val id: String, val name: String, val colorHex: String)

/**
 * [groupKey] — SHA-256(amountMinor + currency + type + 5-minute epoch bucket).
 * Two rows with the same groupKey represent the same physical card tap arriving
 * from different source apps (e.g. wallet widget + issuing bank). The second row
 * is flagged CROSS_DUPLICATE_REVIEW instead of REVIEW.
 */
@Entity(
    tableName = "bank_inbox",
    indices = [
        Index(value = ["eventKey"], unique = true),
        Index(value = ["groupKey"]),
    ],
)
data class BankInboxEntity(
    @PrimaryKey val id: String,
    val eventKey: String,
    val bankId: String,
    val amountMinor: Long,
    val currency: String,
    val type: String,
    val merchant: String,
    val cardLast4: String?,
    val eventAtMillis: Long,
    val receivedAtMillis: Long,
    val timeFromMessage: Boolean,
    val transfer: Boolean,
    val category: String,
    val origin: String,
    val status: String,
    val entryId: String? = null,
    /** nullable so existing rows (DB v2) are compatible without migration breakage. */
    val groupKey: String? = null,
)

data class SourceSpendRow(val sourceType: String, val bankId: String?, val totalMinor: Long)
data class DetailedCategoryRow(
    val category: String,
    val customCategoryId: String?,
    val customName: String?,
    val customColor: String?,
    val totalMinor: Long,
)

@Dao
interface BankDao {
    @Query("SELECT * FROM custom_categories ORDER BY name")
    fun categories(): Flow<List<CustomCategoryEntity>>

    @Insert
    suspend fun addCategory(category: CustomCategoryEntity)

    @Query(
        "SELECT * FROM bank_inbox WHERE status IN ('REVIEW','DUPLICATE_REVIEW','CROSS_DUPLICATE_REVIEW')" +
            " ORDER BY receivedAtMillis DESC LIMIT 100",
    )
    fun inbox(): Flow<List<BankInboxEntity>>

    @Query("SELECT * FROM bank_inbox WHERE id=:id")
    suspend fun inboxItem(id: String): BankInboxEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInbox(item: BankInboxEntity): Long

    @Query("UPDATE bank_inbox SET status=:status,entryId=:entryId WHERE id=:id")
    suspend fun status(id: String, status: String, entryId: String?)

    /**
     * Same-bank duplicate: same card OR same merchant, within ±90 s.
     * Guards against push re-delivery from the same issuing bank.
     */
    @Query(
        """SELECT COUNT(*) FROM bank_inbox WHERE bankId=:bank AND amountMinor=:amount
        AND currency=:currency AND type=:type AND ABS(eventAtMillis-:time)<=90000
        AND status IN ('IMPORTED','REVIEW','DUPLICATE_REVIEW','CROSS_DUPLICATE_REVIEW')
        AND ((cardLast4 IS NOT NULL AND cardLast4=:card) OR (merchant<>'' AND LOWER(merchant)=LOWER(:merchant)))""",
    )
    suspend fun similar(
        bank: String, amount: Long, currency: String, type: String,
        time: Long, card: String?, merchant: String,
    ): Int

    /**
     * Cross-source duplicate: same amount + currency + type from **any** bank or wallet app
     * within a ±180 s sliding window.
     *
     * Catches the common double-fire pattern:
     *   1. Google Pay / gаманець fires first.
     *   2. Issuing bank (PrivatBank, mono…) fires 0–120 s later.
     * Without this check both events would be recorded as separate ledger entries.
     */
    @Query(
        """SELECT COUNT(*) FROM bank_inbox WHERE amountMinor=:amount
        AND currency=:currency AND type=:type AND ABS(eventAtMillis-:time)<=180000
        AND status IN ('IMPORTED','REVIEW','DUPLICATE_REVIEW','CROSS_DUPLICATE_REVIEW')""",
    )
    suspend fun similarCrossSource(amount: Long, currency: String, type: String, time: Long): Int

    @Query(
        """SELECT sourceType,bankId,COALESCE(SUM(amountMinor),0) AS totalMinor FROM entries
        WHERE deletedAtMillis IS NULL AND type='EXPENSE' AND currency=:currency AND epochDay BETWEEN :from AND :through
        GROUP BY sourceType,bankId ORDER BY totalMinor DESC""",
    )
    fun sources(currency: String, from: Long, through: Long): Flow<List<SourceSpendRow>>

    @Query(
        """SELECT e.category,e.customCategoryId,c.name AS customName,c.colorHex AS customColor,SUM(e.amountMinor) AS totalMinor
        FROM entries e LEFT JOIN custom_categories c ON e.customCategoryId=c.id
        WHERE e.deletedAtMillis IS NULL AND e.type='EXPENSE' AND e.currency=:currency AND e.epochDay BETWEEN :from AND :through
        AND (:source IS NULL OR e.sourceType=:source) AND (:bank IS NULL OR e.bankId=:bank)
        GROUP BY e.category,e.customCategoryId,c.name,c.colorHex ORDER BY totalMinor DESC""",
    )
    fun detailCategories(
        currency: String, from: Long, through: Long, source: String?, bank: String?,
    ): Flow<List<DetailedCategoryRow>>

    @Query(
        """SELECT * FROM entries WHERE deletedAtMillis IS NULL AND currency=:currency
        AND epochDay BETWEEN :from AND :through AND (:source IS NULL OR sourceType=:source) AND (:bank IS NULL OR bankId=:bank)
        ORDER BY COALESCE(eventAtMillis,createdAtMillis) DESC LIMIT 100""",
    )
    fun recent(currency: String, from: Long, through: Long, source: String?, bank: String?): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deletedAtMillis IS NULL ORDER BY epochDay DESC,createdAtMillis DESC")
    suspend fun exportEntries(): List<EntryEntity>

    @Query("DELETE FROM bank_inbox")
    suspend fun clearInbox()
}
