package dev.mockarr.app.di

import android.content.Context
import android.location.LocationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mockarr.core.mocklocation.AndroidMockLocationController
import dev.mockarr.core.mocklocation.MockLocationController
import dev.mockarr.core.mocklocation.SetupStatusRepository
import dev.mockarr.core.routing.OsrmRouteProvider
import dev.mockarr.core.routing.RouteProvider
import javax.inject.Singleton

/** Core modules stay Hilt-free; all bindings live here. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
    fun provideRouteProvider(): RouteProvider = OsrmRouteProvider(
        baseUrlProvider = { OsrmRouteProvider.DEFAULT_BASE_URL },
        userAgent = "Mockarr/0.1.0 (+https://github.com/Ethanjopro/Mockarr)",
    )

    @Provides
    @Singleton
    fun provideSetupStatusRepository(
        @ApplicationContext context: Context,
        locationManager: LocationManager,
        controller: MockLocationController,
    ): SetupStatusRepository = SetupStatusRepository(context, locationManager, controller)
}
