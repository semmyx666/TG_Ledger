package com.semmyx.ledger.data

import com.semmyx.ledger.data.repository.RoomEntryRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPatternTest {
    @Test fun wildcardsAreLiteralsAndUkrainianIsCaseFolded() {
        assertEquals("%кава%", RoomEntryRepository.likePattern(" КАВА "))
        assertEquals("%50\\%\\_off%", RoomEntryRepository.likePattern("50%_off"))
    }
}
