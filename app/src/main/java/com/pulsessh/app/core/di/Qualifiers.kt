package com.pulsessh.app.core.di

import javax.inject.Qualifier

/**
 * Marks the [kotlinx.coroutines.CoroutineDispatcher] used for blocking I/O.
 *
 * Injecting the dispatcher instead of referencing `Dispatchers.IO` directly is what makes the
 * SSH, SFTP, crypto and database layers testable: a unit test binds a `TestDispatcher` in its
 * place and gains deterministic, virtual-time control over work that would otherwise escape onto
 * a real thread pool.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Marks the [kotlinx.coroutines.CoroutineScope] whose lifetime is the whole process.
 *
 * Use it only for work that must outlive the screen that started it - flushing a session log,
 * finishing a key rotation - never as a convenient escape hatch from a ViewModel's own scope.
 * Anything launched here keeps running until the process dies and is nobody's responsibility to
 * cancel, so every use should be justified in a comment at the call site.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
