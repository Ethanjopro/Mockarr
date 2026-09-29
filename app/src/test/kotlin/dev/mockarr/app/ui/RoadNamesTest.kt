package dev.mockarr.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RoadNamesTest {

    @Test
    fun `expands highway abbreviations and drops a trailing direction`() {
        assertEquals("Woodall Rodgers Freeway", tidyRoadName("Woodall Rodgers Fwy Eb"))
        assertEquals("Dallas North Tollway", tidyRoadName("Dallas North Tollway NB"))
        assertEquals("State Highway 360", tidyRoadName("State Hwy 360"))
        assertEquals("George Bush Parkway", tidyRoadName("George Bush Pkwy."))
    }

    @Test
    fun `leaves ordinary names and a lone direction word alone`() {
        assertEquals("Reunion Tower", tidyRoadName("Reunion Tower"))
        assertEquals("North Field Street", tidyRoadName("North Field Street"))
        assertEquals("Eb", tidyRoadName("Eb"))
        assertEquals("Canton @ Farmers Market Way - W - FS", tidyRoadName("Canton @ Farmers Market Way - W - FS"))
    }
}
