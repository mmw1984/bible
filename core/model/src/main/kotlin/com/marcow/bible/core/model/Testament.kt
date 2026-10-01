package com.marcow.bible.core.model

/**
 * Which half of the canon a book belongs to.
 *
 * [ordinal] is written into `books.testament`, so the order of the entries is
 * part of the on-disk format. Never reorder.
 */
enum class Testament(val ordinal: Int) {
    OLD(0),
    NEW(1),
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): Testament =
            entries.firstOrNull { it.ordinal == ordinal } ?: OLD
    }
}