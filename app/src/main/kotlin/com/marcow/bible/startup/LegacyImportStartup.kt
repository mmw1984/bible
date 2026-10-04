package com.marcow.bible.startup

import com.marcow.bible.core.legacymigration.LegacyPrefsImporter
import com.marcow.bible.di.ApplicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the one-shot legacy import so startup can await it without running it more than once.
 */
@Singleton
class LegacyImportStartup @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    private val importer: LegacyPrefsImporter,
) {
    private val started = AtomicBoolean(false)
    private val completed = CompletableDeferred<Unit>()

    @Volatile
    private var ready = false

    val isReady: Boolean
        get() = ready

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            try {
                importer.migrate()
            } catch (_: Exception) {
                // Best effort: startup still continues and defaults remain usable.
            } finally {
                ready = true
                completed.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() {
        start()
        completed.await()
    }
}
