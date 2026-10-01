package com.marcow.bible.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the one SQLite connection to `bible.db`.
 *
 * The database ships as a compressed asset, so it is copied to filesDir once on
 * first use. The copy also makes the file writable, which reading progress
 * needs, and it is what allows `AssetManager.openFd` to be avoided.
 */
@Singleton
class ScriptureConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) {

    private val lock = Any()

    @Volatile
    private var database: SQLiteDatabase? = null

    /**
     * Copies the asset out if needed and opens the database. Suspends on IO;
     * safe to call from several screens, the work happens once.
     */
    suspend fun open(): ScriptureConnection = withContext(io) {
        synchronized(lock) {
            if (database?.isOpen == true) return@withContext this@ScriptureConnection
            val file = installedFile()
            database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        }
        this
    }

    /** Runs [block] against the database, opening it synchronously if needed. */
    fun <T> read(block: (SQLiteDatabase) -> T): T = block(requireDatabase())

    fun <T> write(block: (SQLiteDatabase) -> T): T = block(requireDatabase())

    private fun requireDatabase(): SQLiteDatabase = database?.takeIf { it.isOpen } ?: run {
        // Callers are expected to await open() during startup; reaching this
        // means something touched scripture first, so open synchronously.
        synchronized(lock) {
            database?.takeIf { it.isOpen } ?: run {
                val file = installedFile()
                SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                ).also { database = it }
            }
        }
    }

    private fun installedFile(): File {
        val target = File(context.filesDir, "databases/$FILE_NAME")
        if (target.exists() && target.length() > 0L) return target
        val parent = target.parentFile
        if (parent != null) parent.mkdirs()
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
