package com.marcow.bible.core.common

import javax.inject.Qualifier

/** The dispatcher for disk and network work. Injected instead of referenced directly so tests can swap it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** The dispatcher for CPU-bound work such as parsing and markdown rendering. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** The main thread, used where a framework API demands it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
