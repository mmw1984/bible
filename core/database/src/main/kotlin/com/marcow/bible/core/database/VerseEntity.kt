package com.marcow.bible.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.marcow.bible.core.model.VersePair

/**
 * Row of the `verses` table. A row exists for every verse index either translation has, so
 * `text_cuv` / `text_web` are nullable and [toDomain] reproduces the Flutter pairing rule.
 *
 * The table is `WITHOUT ROWID` in the pre-packaged database, keyed on (book_id, chapter, verse) —
 * that is also the exact order `BibleDao.chapter()` reads, so no extra index is needed.
 */
@Entity(
    tableName = "verses",
    primaryKeys = ["book_id", "chapter", "verse"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
)
data class VerseEntity(
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
) {
    /**
     * Mirrors `BibleRepository.chapter()` in `legacy/flutter/lib/bible_data.dart`: a missing
     * index in one translation is an empty string, never a dropped row.
     */
    fun toDomain(): VersePair = VersePair(
        number = verse,
        zh = textCuv.orEmpty(),
        en = textWeb.orEmpty(),
    )
}
