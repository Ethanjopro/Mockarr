package dev.mockarr.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mockarr.app.ui.map.RouteThumbnails
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.routing.ElevationProvider
import dev.mockarr.core.routing.Geocoder
import dev.mockarr.core.routing.OpenMeteoElevationClient
import dev.mockarr.core.routing.OsrmRouteProvider
import dev.mockarr.core.routing.PhotonGeocoder
import dev.mockarr.core.routing.RouteProvider
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Routing/geocoding/elevation providers and route-thumbnail rendering, split out
 * of [AppModule] to stay under detekt's per-object function threshold. The app
 * only ever sees the interfaces — swapping a backend is one class + one binding
 * here (ADR 0002).
 */
@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    @Provides
    @Singleton
    fun provideRouteProvider(settingsRepository: SettingsRepository): RouteProvider = OsrmRouteProvider(
        baseUrlProvider = { settingsRepository.settings.value.osrmBaseUrl },
        userAgent = USER_AGENT,
    )

    @Provides
    @Singleton
    fun provideGeocoder(): Geocoder = PhotonGeocoder(userAgent = USER_AGENT)

    @Provides
    @Singleton
    fun provideElevationProvider(): ElevationProvider =
        OpenMeteoElevationClient(userAgent = USER_AGENT)

    @Provides
    @Singleton
    fun provideRouteThumbnails(
        @ApplicationContext context: Context,
        appScope: CoroutineScope,
    ): RouteThumbnails = RouteThumbnails(context, appScope)
}
