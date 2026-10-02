package com.marcow.bible.feature.devotion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Where the article's images are kept on disk, from `devotionImageCache`.
 *
 * It is a rule about where bytes land rather than about anything that can be looked at on a screen,
 * which is why it is its own function: an `ImageLoader.Builder` cannot be checked without an Android
 * runtime and a network, but `cacheDir` can be handed in as a path and the answer read off it.
 *
 * Three claims are pinned here — that the images live in the app's own reclaimable cache directory
 * rather than anywhere durable, that the directory is this feature's own rather than Coil's shared
 * one, and that the ceiling is a number rather than a percentage of whatever the device has free.
 */
class DevotionImageLoaderTest {

    @Test
    fun `the images land in a directory of their own under the app's cache`() {
        // Coil's default is `cacheDir/coil3_disk_cache`, a directory any other module using Coil would
        // also be writing to.
        val cache = devotionImageCache(File("/data/user/0/com.marcow.bible/cache"))

        assertEquals(File("/data/user/0/com.marcow.bible/cache/devotion_images"), cache.directory)
        assertNotEquals(
            File("/data/user/0/com.marcow.bible/cache/coil3_disk_cache"),
            cache.directory,
        )
    }

    @Test
    fun `the directory is inside the cache directory, so the system may reclaim it`() {
        // The images are re-fetchable from the blog, so a full card should cost a download rather than
        // a reader with no pictures. `parentFile` rather than a prefix check on the path, so the claim
        // is about the tree and not about how `File` spells it.
        val cacheDir = File("/data/user/0/com.marcow.bible/cache")
        val cache = devotionImageCache(cacheDir)

        assertEquals(cacheDir, cache.directory.parentFile)
        assertTrue(cache.directory.absolutePath.startsWith(cacheDir.absolutePath))
        assertNotEquals(cacheDir, cache.directory)
    }

    @Test
    fun `the ceiling is one fixed number whatever directory it is given`() {
        // Coil's default is 2% of the device's free space, clamped to 10 MB..250 MB, so the same day's
        // images would be kept on one device and thrown away on another. A named byte count is the
        // decision; `64 MB` is what it was decided to be.
        val busy = devotionImageCache(File("/data/user/0/com.marcow.bible/cache"))
        val empty = devotionImageCache(File("/storage/emulated/0/Android/data/.cache"))

        assertEquals(64L * 1024 * 1024, busy.maxBytes)
        assertEquals(busy.maxBytes, empty.maxBytes)
        // The directories differ, so the equality above is about the ceiling and not about the value
        // being derived from the path.
        assertNotEquals(busy.directory, empty.directory)
        // Coil's builder rejects a non-positive size, so a zero here would be a crash rather than an
        // unlimited cache.
        assertTrue(busy.maxBytes > 0)
    }
}
