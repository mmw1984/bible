package com.marcow.bible.feature.devotion

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import java.io.File

/**
 * The article's image loader, and the disk it keeps its images on.
 *
 * **One loader, not one per image.** Coil is built to be shared — its own KDoc says a loader "works
 * best when you create a single instance and share it throughout your app" — and the images of one
 * article are fetched back out of its cache by each other: scrolling a long post back up finds the
 * pictures it already decoded, and every image is decoded again from the same bytes if the loader is
 * per composable. `remember` cannot share them, because a `remember` is scoped to one call site, and
 * `DevotionImageFrame` and `DevotionVideoCard` are two call sites called once per image.
 *
 * So it is held here instead, for the life of the process, which is also what Coil's
 * `singletonDiskCache` does — and *has* to, since two [DiskCache] instances in one directory corrupt
 * it. It is built on the application context rather than on the `LocalContext` the composable is
 * given, because a process-wide reference to an activity is a leak, and nothing here needs one.
 *
 * Coil's own global slot is left empty on purpose. Installing a loader there is a whole-app decision
 * that belongs in the `app` module, and this feature is the only thing that loads images — so reaching
 * for the slot would mean deciding the arrangement for whoever comes next, and forgetting that an
 * earlier one had claimed it. This reaches for the same shape instead, and keeps it to itself.
 */
private object DevotionImages {

    private var instance: ImageLoader? = null

    /** The loader, built once. A second call gets the first one's caches, disk lock and all. */
    fun loader(context: Context): ImageLoader {
        instance?.let { return it }
        val application = context.applicationContext
        val cache = devotionImageCache(application.cacheDir)
        return ImageLoader.Builder(application)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cache.directory)
                    .maxSizeBytes(cache.maxBytes)
                    .build()
            }
            .build()
            .also { instance = it }
    }
}

/**
 * The loader for every image in an article — `DevotionImage` blocks and [DevotionVideoCard]
 * thumbnails alike.
 *
 * Its OkHttp fetcher still arrives by `ServiceLoader`, which is how `coil-network-okhttp` announces
 * itself: a loader is handed a client rather than building one, so there is nothing here to configure
 * for the fetcher to find.
 */
@Composable
internal fun devotionImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) { DevotionImages.loader(context) }
}

/** The reader's images on disk: a directory and a ceiling for it. */
internal data class DevotionImageCache(
    /** Where the bytes are kept. Under the app's own `cacheDir`, so the system may delete it. */
    val directory: File,
    /** The most this cache is allowed to occupy. */
    val maxBytes: Long,
)

/**
 * The directory and the ceiling, in one value because one of them is wrong without the other: a
 * directory with Coil's default ceiling is a directory whose size depends on how full the device
 * happens to be.
 *
 * Coil's default cache is `2%` of the free space on the device, clamped to between 10 MB and 250 MB
 * (`DiskCache.Builder`'s `maxSizePercent`, `minimumMaxSizeBytes` and `maximumMaxSizeBytes`). That is a
 * number nobody chose for this feature, and it moves: a device with a nearly full card gets 10 MB, one
 * with a freshly wiped card gets 250 MB, and the same day's article caches the same three images in
 * both. 64 MB is this feature's answer instead — a day's images are a few megabytes, so it holds a
 * long while even for somebody reading the same post every morning, and it is small enough that being
 * wrong about it costs a re-download rather than a disk.
 *
 * There was no ceiling to port: Flutter's `buildDevotionImage` used `Image.network`, which keeps
 * decoded images in `ImageCache` and bytes nowhere, and `NATIVE_PLAN.md` asks for a disk cache here in
 * the first place (`圖片 → Coil 3 + disk cache`). So the number is this feature's decision to defend
 * rather than a figure the Dart side happened to have.
 *
 * The directory is the app's `cacheDir` and not Coil's default `cacheDir/coil3_disk_cache`, for the
 * same reason it is under `cacheDir` at all: nothing here is content the user would miss if Android
 * reclaimed it under storage pressure, and a cache the system knows it may delete is one that gets
 * deleted when it should. Naming it after this feature also means clearing the reader's images cannot
 * take another feature's cache with it.
 */
internal fun devotionImageCache(cacheDir: File): DevotionImageCache = DevotionImageCache(
    directory = File(cacheDir, DEVOTION_IMAGE_CACHE_DIRECTORY),
    maxBytes = DEVOTION_IMAGE_DISK_CACHE_BYTES,
)

/** A directory of its own, so it can be told apart from a `coil3_disk_cache` nobody else reads. */
private const val DEVOTION_IMAGE_CACHE_DIRECTORY = "devotion_images"

/** 64 MB, in bytes: the whole point of naming it is that the number is a decision. */
private const val DEVOTION_IMAGE_DISK_CACHE_BYTES = 64L * 1024 * 1024
