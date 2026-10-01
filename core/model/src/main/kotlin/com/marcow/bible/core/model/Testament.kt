package com.marcow.bible.core.model

/**
 * Which half of the canon a book belongs to.
 *
 * The persisted value is [storageValue], which is written into
 * `books.testament`. It is part of the on-disk format, so never change it.
 */
enum class Testament(val storageValue: Int) {
    OLD(0),
    NEW(1),
    ;

    companion object {
        fun fromStorageValue(value: Int): Testament = entries.firstOrNull { it.storageValue == value } ?: OLD
    }
}
