package com.semmyx.ledger.voice

import org.junit.Assert.*
import org.junit.Test

class OptionalVoiceAssistantParserTest {
    @Test fun recognizesEnglishBalanceQuestion() {
        assertEquals(VoiceCommand.ShowBalance, OptionalVoiceAssistant.classify("what's my balance"))
    }
    @Test fun recognizesUkrainianBalanceQuestion() {
        assertEquals(VoiceCommand.ShowBalance, OptionalVoiceAssistant.classify("який в мене баланс"))
    }
    @Test fun recognizesSpentTodayInBothLanguages() {
        assertEquals(VoiceCommand.ShowSpentToday, OptionalVoiceAssistant.classify("what did I spend today"))
        assertEquals(VoiceCommand.ShowSpentToday, OptionalVoiceAssistant.classify("скільки я витратив сьогодні"))
    }
    @Test fun recognizesAddEntryRequests() {
        assertEquals(VoiceCommand.OpenAddEntry, OptionalVoiceAssistant.classify("add a new expense"))
        assertEquals(VoiceCommand.OpenAddEntry, OptionalVoiceAssistant.classify("додати новий запис"))
    }
    @Test fun returnsNullForUnrelatedSpeech() {
        assertNull(OptionalVoiceAssistant.classify("tell me a joke"))
    }
    @Test fun returnsNullForBlankInput() {
        assertNull(OptionalVoiceAssistant.classify("   "))
    }
}
