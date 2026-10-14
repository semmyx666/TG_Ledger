package com.semmyx.ledger.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceSourceTest {
    @Test fun sourceIsDetectedOnlyWhenSpoken() {
        assertEquals("CARD", VoiceEntryParser.detectSource("купив каву за 50 карткою"))
        assertEquals("CASH", VoiceEntryParser.detectSource("bought coffee for 5 cash"))
        assertEquals("CASH", VoiceEntryParser.detectSource("витратив 100 готівкою"))
        assertNull(VoiceEntryParser.detectSource("купив каву за 50"))
        assertNull(VoiceEntryParser.detectSource("карткою або готівкою 50"))
    }
}
