package com.marcow.bible.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.marcow.bible.core.model.BibleBook
import com.marcow.bible.core.model.Testament

/**
 * Row of the `books` table. The column set must stay identical to the `CREATE TABLE books` in
 * `tool/build_bible_db.mjs`, which writes the pre-packaged database.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
    @ColumnInfo(name = "name_zh")
    val nameZh: String,
    @ColumnInfo(name = "name_en")
    val nameEn: String,
    @ColumnInfo(name = "chapters")
    val chapters: Int,
    @ColumnInfo(name = "testament")
    val testament: Int,
) {
    fun toDomain(): BibleBook = BibleBook(
        id = id,
        ordinal = ordinal,
        nameZh = nameZh,
        nameEn = nameEn,
        chapters = chapters,
        testament = Testament.fromStorage(testament),
    )
}
