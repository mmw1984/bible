package com.marcow.bible.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.marcow.bible.core.model.ReadingProgress
import com.marcow.bible.core.model.ReadingMode

/**
 * Row of the `reading_progress` table, one per book. Empty by default; the reader writes it from
 * Phase 2 on, and `LegacyPrefsImporter` seeds it from the Flutter preferences.
 */
@Entity(
    tableName = "reading_progress",
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
data class ReadingProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "chapter")
    val chapter: Int,
    @ColumnInfo(name = "verse")
    val verse: Int?,
    @ColumnInfo(name = "scroll_ratio", defaultValue = "0")
    val scrollRatio: Float,
    @ColumnInfo(name = "mode")
    val mode: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    fun toDomain(): ReadingProgress = ReadingProgress(
        bookId = bookId,
        chapter = chapter,
        verse = verse,
        scrollRatio = scrollRatio,
        mode = ReadingMode.fromStorage(mode),
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(progress: ReadingProgress): ReadingProgressEntity = ReadingProgressEntity(
            bookId = progress.bookId,
            chapter = progress.chapter,
            verse = progress.verse,
            scrollRatio = progress.scrollRatio,
            mode = progress.mode.storageValue,
            updatedAt = progress.updatedAt,
        )
    }
}
