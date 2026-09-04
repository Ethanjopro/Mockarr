package dev.mockarr.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mockarr.app.BuildConfig
import dev.mockarr.app.ui.map.RouteThumbnails
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.routing.BackendConfig
import dev.mockarr.core.routing.ElevationProvider
import dev.mockarr.core.routing.GeoapifyGeocoder
import dev.mockarr.core.routing.GeoapifyRouteProvider
import dev.mockarr.core.routing.Geocoder
import dev.mockarr.core.routing.OsrmRouteProvider
import dev.mockarr.core.routing.PhotonGeocoder
import dev.mockarr.core.routing.RouteProvider
import dev.mockarr.core.routing.SwitchingGeocoder
import dev.mockarr.core.routing.SwitchingRouteProvider
import dev.mockarr.core.routing.TerrariumElevationProvider
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Routing/geocoding/elevation providers and route-thumbnail rendering, split out
 * of [AppModule] to stay under detekt's per-object function threshold. The app
 * only ever sees the interfaces — swapping a backend is one class + one binding
 * here (ADR 0002). ADR 0003: Geoapify is the managed default when the build has
 * a key (`GEOAPIFY_KEY` in `secrets.properties`), the public OSRM/Photon servers
 * are the fallback and the "Public" setting; no key = public servers only.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    private val geoapifyKey: String? = BuildConfig.GEOAPIFY_KEY.takeIf { it.isNotBlank() }

    @Provides
    @Singleton
    fun provideBackendConfig(): BackendConfig = BackendConfig(managedAvailable = geoapifyKey != null)

    @Provides
    @Singleton
    fun provideRouteProvider(settingsRepository: SettingsRepository): RouteProvider = SwitchingRouteProvider(
        managed = geoapifyKey?.let { GeoapifyRouteProvider(apiKey = it, userAgent = USER_AGENT) },
        fallback = OsrmRouteProvider(
            baseUrlProvider = { settingsRepository.settings.value.osrmBaseUrl },
            userAgent = USER_AGENT,
        ),
        mode = { settingsRepository.settings.value.backendMode },
    )

    @Provides
    @Singleton
    fun provideGeocoder(settingsRepository: SettingsRepository): Geocoder = SwitchingGeocoder(
        managed = geoapifyKey?.let { GeoapifyGeocoder(apiKey = it, userAgent = USER_AGENT) },
        fallback = PhotonGeocoder(userAgent = USER_AGENT),
        mode = { settingsRepository.settings.value.backendMode },
    )

    @Provides
    @Singleton
    fun provideElevationProvider(): ElevationProvider = TerrariumElevationProvider(userAgent = USER_AGENT)

    @Provides
    @Singleton
    fun provideRouteThumbnails(
        @ApplicationContext context: Context,
        appScope: CoroutineScope,
    ): RouteThumbnails = RouteThumbnails(context, appScope)
}
