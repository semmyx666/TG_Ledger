package com.semmyx.ledger.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "entries", indices = [Index(value = ["currency", "deletedAtMillis", "epochDay", "createdAtMillis"])])
data class EntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val type: String,
    val category: String,
    val epochDay: Long,
    val note: String,
    val searchText: String,
    val createdAtMillis: Long,
    val deletedAtMillis: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "'UNKNOWN'") val sourceType: String = "UNKNOWN",
    val bankId: String? = null,
    val cardLast4: String? = null,
    val eventAtMillis: Long? = null,
    val customCategoryId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'MANUAL'") val origin: String = "MANUAL",
)

data class BalanceRow(val incomeMinor: Long, val expenseMinor: Long, val entryCount: Int)
data class CategoryTotalRow(val category: String, val incomeMinor: Long, val expenseMinor: Long, val count: Int, val incomeCount: Int, val expenseCount: Int)
data class ActivityRow(val epochDay: Long, val incomeMinor: Long, val expenseMinor: Long)

@Entity(tableName = "rates")
data class RateEntity(
    @PrimaryKey val currency: String,
    val uahPerUnit: Double,
    val epochDay: Long,
    val fetchedAtMillis: Long,
)
