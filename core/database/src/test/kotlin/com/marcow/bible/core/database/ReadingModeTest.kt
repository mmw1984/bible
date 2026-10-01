package com.marcow.bible.core.database

import com.marcow.bible.core.model.ReadingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReadingModeTest {

    @Test
    @DisplayName("storage keys match the Flutter enum names")
    fun storageKeys() {
        assertEquals("chinese", ReadingMode.CHINESE.storageKey)
        assertEquals("english", ReadingMode.ENGLISH.storageKey)
        assertEquals("bilingual", ReadingMode.BILINGUAL.storageKey)
    }

    @Test
    fun unknownKeysFallBackToChinese() {
        assertEquals(ReadingMode.CHINESE, ReadingMode.fromStorageKey(null))
        assertEquals(ReadingMode.CHINESE, ReadingMode.fromStorageKey(""))
        assertEquals(ReadingMode.CHINESE, ReadingMode.fromStorageKey("klingon"))
        assertEquals(ReadingMode.BILINGUAL, ReadingMode.fromStorageKey("bilingual"))
    }
}