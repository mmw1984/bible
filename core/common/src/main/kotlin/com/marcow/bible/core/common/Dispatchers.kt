package com.marcow.bible.core.common

import javax.inject.Qualifier

/**
 * The dispatcher for disk and network work.
 *
 * Injected rather than referenced directly so tests can substitute it.
 *
 * The targets include FIELD and PROPERTY because Kotlin 2.3 warns when a
 * qualifier on a constructor parameter is not also declared for the field it
 * backs, which is how Dagger and Hilt end up seeing it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class IoDispatcher

/** The dispatcher for CPU-bound work such as parsing and markdown rendering. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class DefaultDispatcher

/** The main thread, used where a framework API demands it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class MainDispatcher
