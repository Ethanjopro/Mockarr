package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartsNearTest {

    private val routeStart = LatLng(48.0, 2.0)

    @Test
    fun `an origin within the threshold starts the route as-is`() {
        assertTrue(startsNear(routeStart, GeoMath.destination(routeStart, 90.0, 20.0)))
    }

    @Test
    fun `an origin past the threshold offers the start choice`() {
        assertFalse(startsNear(routeStart, GeoMath.destination(routeStart, 90.0, 45.0)))
    }
}
