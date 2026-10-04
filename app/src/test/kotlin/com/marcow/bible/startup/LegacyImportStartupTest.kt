package com.marcow.bible.startup

import com.marcow.bible.core.legacymigration.LegacyPrefsImporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LegacyImportStartupTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `start and await only run the importer once`() = runTest(dispatcher) {
        val importer = mockk<LegacyPrefsImporter>()
        coEvery { importer.migrate() } returns true
        val startup = LegacyImportStartup(
            appScope = CoroutineScope(SupervisorJob() + dispatcher),
            importer = importer,
        )

        startup.start()
        startup.start()
        startup.awaitReady()
        advanceUntilIdle()

        coVerify(exactly = 1) { importer.migrate() }
        assertTrue(startup.isReady)
    }

    @Test
    fun `a migration failure still unblocks startup`() = runTest(dispatcher) {
        val importer = mockk<LegacyPrefsImporter>()
        coEvery { importer.migrate() } throws IllegalStateException("broken legacy file")
        val startup = LegacyImportStartup(
            appScope = CoroutineScope(SupervisorJob() + dispatcher),
            importer = importer,
        )

        startup.awaitReady()
        advanceUntilIdle()

        coVerify(exactly = 1) { importer.migrate() }
        assertTrue(startup.isReady)
    }
}
