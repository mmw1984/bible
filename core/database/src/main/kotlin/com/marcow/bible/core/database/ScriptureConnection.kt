package com.marcow.bible.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.marcow.bible.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single SQLite connection to `bible.db`.
 *
 * The database ships as a compressed asset, so it is copied to filesDir once on
 * first use. The copy also makes the file writable, which reading progress
 * needs, and it means the APK can store it compressed.
 */
@Singleton
class ScriptureConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val lock = Any()

    @Volatile
    private var database: SQLiteDatabase? = null

    /**
     * Copies the asset out if needed and opens the database. Safe to call from
     * several screens; the work happens once.
     */
    suspend fun open(): ScriptureConnection {
        withContext(io) { openBlocking() }
        return this
    }

    /** Runs [block] against the database. */
    fun <T> read(block: (SQLiteDatabase) -> T): T = block(openBlocking())

    /** Runs [block] against the database, for writes. */
    fun <T> write(block: (SQLiteDatabase) -> T): T = block(openBlocking())

    private fun openBlocking(): SQLiteDatabase = database?.takeIf { it.isOpen } ?: synchronized(lock) {
        database?.takeIf { it.isOpen } ?: run {
            val file = installedFile()
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).also { database = it }
        }
    }

    private fun installedFile(): File {
        val target = File(context.filesDir, "databases/$FILE_NAME")
        if (target.exists() && target.length() > 0L) return target
        target.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private companion object {
        const val ASSET_PATH = "databases/bible.db"
        const val FILE_NAME = "bible.db"
    }
}
