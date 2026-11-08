package de.asdfghasdfgh.norikae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitFilterTest {

    private fun modes(filter: TransitFilter): Set<String> =
        filter.transitModes()?.split(",")?.toSet().orEmpty()

    @Test
    fun `all leaves the server default`() {
        assertNull(TransitFilter.All.transitModes())
    }

    @Test
    fun `local keeps local and regional modes, never long distance`() {
        val local = modes(TransitFilter.Local)
        assertTrue(local.containsAll(setOf("REGIONAL_RAIL", "SUBURBAN", "SUBWAY", "TRAM", "BUS", "FERRY")))
        val longDistance = setOf("HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "COACH", "AIRPLANE")
        assertTrue(local.intersect(longDistance).isEmpty())
        // TRANSIT and RAIL expand to every mode, long distance included.
        assertTrue(local.intersect(setOf("TRANSIT", "RAIL")).isEmpty())
    }

    @Test
    fun `long distance keeps exactly the long distance modes`() {
        assertEquals(
            setOf("HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "COACH", "AIRPLANE"),
            modes(TransitFilter.LongDistance),
        )
    }
}
