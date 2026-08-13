package com.pulsessh.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Process-wide bindings that have no better home.
 *
 * Everything here is stateless plumbing. Feature-specific bindings (database, key store, SSH
 * transport) belong in their own modules next to the code they serve, so that this file does not
 * become the place where the whole graph is declared.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * The dispatcher for blocking I/O: sockets, files, SQLCipher and the Android key store.
     *
     * @return [Dispatchers.IO], which is shared with the default dispatcher's thread pool and
     * grows on demand up to a 64-thread limit.
     */
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * A scope that lives as long as the application process.
     *
     * [SupervisorJob] means one failed child cancels only itself: a crashed background upload must
     * not take the session-logging coroutine down with it. No exception handler is installed, so an
     * unhandled failure still reaches the default handler and is reported rather than swallowed.
     *
     * @param dispatcher the injected I/O dispatcher; a test can replace it to control timing.
     * @return a never-cancelled [CoroutineScope].
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
