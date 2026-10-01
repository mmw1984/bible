package com.marcow.bible.core.database

import androidx.room.ColumnInfo
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.ScriptureHit
import com.marcow.bible.core.model.Testament
import com.marcow.bible.core.model.VersePair

/**
 * One row of [BibleDao.searchContains]: a verse plus the book columns the search result needs.
 *
 * Room can only project into a flat POJO, so the book is flattened into the `book_*` columns the
 * query aliases. [toDomain] is where it is folded back into the [ScriptureHit] the UI renders, the
 * same [VersePair] pairing rule [VerseEntity.toDomain] applies.
 */
data class ScriptureSearchRow(
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "chapter")
    val chapter: Int,
    @ColumnInfo(name = "verse")
    val verse: Int,
    @ColumnInfo(name = "text_cuv")
    val textCuv: String?,
    @ColumnInfo(name = "text_web")
    val textWeb: String?,
    @ColumnInfo(name = "book_ordinal")
    val bookOrdinal: Int,
    @ColumnInfo(name = "book_name_zh")
    val bookNameZh: String,
    @ColumnInfo(name = "book_name_en")
    val bookNameEn: String,
    @ColumnInfo(name = "book_chapters")
    val bookChapters: Int,
    @ColumnInfo(name = "book_testament")
    val bookTestament: Int,
) {
    /** The [ScriptureHit] the search tile renders, mirroring `ScriptureHit` in `bible_data.dart`. */
    fun toDomain(): ScriptureHit = ScriptureHit(
        book = BibleBook(
            id = bookId,
            ordinal = bookOrdinal,
            nameZh = bookNameZh,
            nameEn = bookNameEn,
            chapters = bookChapters,
            testament = Testament.fromStorage(bookTestament),
        ),
        chapter = chapter,
        verse = VersePair(number = verse, zh = textCuv.orEmpty(), en = textWeb.orEmpty()),
    )
}
