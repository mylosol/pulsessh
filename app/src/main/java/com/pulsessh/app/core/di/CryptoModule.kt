package com.pulsessh.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pulsessh.app.core.crypto.AndroidMasterKeyGateway
import com.pulsessh.app.core.crypto.MasterKeyGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val CRYPTO_PREFERENCES_FILE_NAME = "pulsessh_crypto_prefs"

/** Bindings for the KeyStore master key and the DataStore that persists the wrapped passphrase. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    abstract fun bindMasterKeyGateway(impl: AndroidMasterKeyGateway): MasterKeyGateway

    companion object {
        @Provides
        @Singleton
        fun provideCryptoPreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile(CRYPTO_PREFERENCES_FILE_NAME) },
            )
    }
}
