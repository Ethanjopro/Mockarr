package dev.mockarr.app.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteNameTest {

    private val between = "%1\$s to %2\$s"
    private val along = "Along %1\$s"
    private val inCity = "%1\$s, %2\$s"

    @Test
    fun `two ends read start to end, with the city`() {
        assertEquals(
            "Main Street to Oak Avenue, Dallas",
            formatRouteName(RouteNameParts("Main Street", "Oak Avenue", "Dallas"), between, along, inCity),
        )
    }

    @Test
    fun `ends that share a name read along it`() {
        // Session 41 critique: "North Akard Street to North Akard Street".
        assertEquals(
            "Along North Akard Street, Dallas",
            formatRouteName(
                RouteNameParts("North Akard Street", "north akard street", "Dallas"),
                between,
                along,
                inCity,
            ),
        )
    }

    @Test
    fun `no city leaves the route part alone`() {
        assertEquals("A to B", formatRouteName(RouteNameParts("A", "B", null), between, along, inCity))
    }
}
