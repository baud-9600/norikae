package com.berend.transit.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

class PlanLabelTest {

    private val today = LocalDate.of(2026, 9, 22)
    private fun at(date: LocalDate, hour: Int, minute: Int): OffsetDateTime =
        date.atTime(hour, minute).atOffset(ZoneOffset.UTC)

    private fun label(at: OffsetDateTime): String =
        at.asPlanLabel(today, "Today", "Tomorrow", "EEE d MMM", Locale.ENGLISH)

    @Test
    fun `today shows clock only`() {
        assertEquals("08:15", label(at(today, 8, 15)))
    }

    @Test
    fun `tomorrow shows day label with clock`() {
        assertEquals("Tomorrow 08:15", label(at(today.plusDays(1), 8, 15)))
    }

    @Test
    fun `other days show day and date with clock`() {
        assertEquals("Fri 25 Sep 17:05", label(at(today.plusDays(3), 17, 5)))
    }
}
