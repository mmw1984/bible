package com.marcow.bible.core.legacymigration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ParseLegacyDoubleTest {
    @Test
    fun `the plugin stores a double as a string carrying a base64 marker`() {
        assertEquals(1420.5, parseLegacyDouble("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu1420.5"))
        assertEquals(0.0, parseLegacyDouble("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu0.0"))
        assertEquals(-12.25, parseLegacyDouble("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu-12.25"))
    }

    @Test
    fun `an unmarked decimal string is still read`() {
        assertEquals(88.0, parseLegacyDouble("88.0"))
    }

    @Test
    fun `numbers written natively are accepted`() {
        assertEquals(1.5, parseLegacyDouble(1.5f))
        assertEquals(2.0, parseLegacyDouble(2))
        assertEquals(3.0, parseLegacyDouble(3L))
    }

    @Test
    fun `an unparseable entry is skipped instead of becoming zero`() {
        assertNull(parseLegacyDouble(null))
        assertNull(parseLegacyDouble(true))
        assertNull(parseLegacyDouble("not a number"))
    }
}
