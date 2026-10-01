package com.marcow.bible.core.common

import javax.inject.Qualifier

/**
 * The dispatcher for disk and network work.
 *
 * Injected rather than referenced directly so tests can substitute it.
 *
 * The targets cover both halves of an injection point. FIELD and PROPERTY are
 * there because Kotlin 2.3 warns when a qualifier on a constructor parameter is
 * not also declared for the field it backs; FUNCTION is there because the same
 * qualifier annotates the @Provides method in DatabaseModule.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
)
annotation class IoDispatcher

/** The dispatcher for CPU-bound work such as parsing and markdown rendering. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
)
annotation class DefaultDispatcher

/** The main thread, used where a framework API demands it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
)
annotation class MainDispatcher
