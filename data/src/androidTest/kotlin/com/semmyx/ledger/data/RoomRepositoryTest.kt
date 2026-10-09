package com.semmyx.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.data.local.LedgerDatabase
import com.semmyx.ledger.data.repository.RoomEntryRepository
import com.semmyx.ledger.domain.*
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repository: RoomEntryRepository
    private fun entry(id: String = "one", title: String = "КАВА 50%") = LedgerEntry(id, title, 3500,
        LedgerCurrency.UAH, EntryType.EXPENSE, Category.FOOD, LocalDate.of(2026, 9, 8))
    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).build()
        repository = RoomEntryRepository(db.ledgerDao(), Clock.systemUTC())
    }
    @After fun closeDatabase() { db.close() }
    @Test fun reactiveCrudAndUnicodeLiteralSearch() = runTest {
        repository.save(entry())
        assertEquals(1, repository.observeEntries(EntryFilter(LedgerCurrency.UAH, "кава")).first().size)
        assertEquals(1, repository.observeEntries(EntryFilter(LedgerCurrency.UAH, "50%")).first().size)
        assertTrue(repository.observeEntries(EntryFilter(LedgerCurrency.USD)).first().isEmpty())
        repository.save(entry().copy(amountMinor = 4500))
        assertEquals(4500L, repository.observeBalance(LedgerCurrency.UAH).first().expenseMinor)
        val token = requireNotNull(repository.delete("one"))
        assertEquals(0, repository.observeBalance(LedgerCurrency.UAH).first().entryCount)
        assertTrue(repository.undo(token))
        assertFalse(repository.undo(token))
    }
    @Test fun anEditCannotResurrectADeletedRow() = runTest {
        repository.save(entry())
        repository.delete("one")
        try { repository.save(entry().copy(title = "Changed")); fail("Deleted row should be protected") }
        catch (_: IllegalStateException) { assertNull(repository.find("one")) }
    }
    @Test fun purgeRemovesOnlyOldTombstones() = runTest {
        repository.save(entry())
        val token = requireNotNull(repository.delete("one"))
        repository.purgeDeletedBefore(token.deletedAtMillis + 1)
        assertFalse(repository.undo(token))
        assertNull(db.ledgerDao().findIncludingDeleted("one"))
    }
}
