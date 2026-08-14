package dev.mockarr.app.di

import android.content.Context
import android.location.LocationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mockarr.app.BuildConfig
import dev.mockarr.core.data.MockarrDatabase
import dev.mockarr.core.data.SavedRoutesRepository
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.mocklocation.AndroidMockLocationController
import dev.mockarr.core.mocklocation.MockLocationController
import dev.mockarr.core.mocklocation.SetupStatusRepository
import dev.mockarr.core.routing.OsrmRouteProvider
import dev.mockarr.core.routing.RouteProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

val USER_AGENT = "Mockarr/${BuildConfig.VERSION_NAME} (+https://github.com/Ethanjopro/Mockarr)"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Core modules stay Hilt-free; all bindings live here. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideLocationManager(@ApplicationContext context: Context): LocationManager =
        context.getSystemService(LocationManager::class.java)

    @Provides
    @Singleton
    fun provideMockLocationController(locationManager: LocationManager): MockLocationController =
        AndroidMockLocationController(locationManager)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        appScope: CoroutineScope,
    ): SettingsRepository = SettingsRepository(context.settingsDataStore, appScope)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MockarrDatabase =
        Room.databaseBuilder(context, MockarrDatabase::class.java, MockarrDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideSavedRoutesRepository(database: MockarrDatabase): SavedRoutesRepository =
        SavedRoutesRepository(database.savedRouteDao())

    @Provides
    @Singleton
    fun provideRouteProvider(settingsRepository: SettingsRepository): RouteProvider = OsrmRouteProvider(
        baseUrlProvider = { settingsRepository.settings.value.osrmBaseUrl },
        userAgent = USER_AGENT,
    )

    @Provides
    @Singleton
    fun provideSetupStatusRepository(
        @ApplicationContext context: Context,
        locationManager: LocationManager,
        controller: MockLocationController,
    ): SetupStatusRepository = SetupStatusRepository(context, locationManager, controller)
}
