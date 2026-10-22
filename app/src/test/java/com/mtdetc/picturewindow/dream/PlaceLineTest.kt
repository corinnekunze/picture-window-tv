package com.mtdetc.picturewindow.dream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceLineTest {
    @Test
    fun `hides the place when the about line already names it`() =
        assertNull(PlaceLine.toShow("Maharashtra, India", "Great Grey Shrike in Bhigwan, Maharashtra, India"))

    @Test
    fun `hides a country-only place the about line mentions`() =
        assertNull(PlaceLine.toShow("Iceland", "Arctic fox in winter coat in the snow, Iceland"))

    @Test
    fun `keeps the place when the about line doesn't say where`() =
        assertEquals("Zug, Switzerland", PlaceLine.toShow("Zug, Switzerland", "Restaurant Platzmühle at blue hour"))

    @Test
    fun `shows the place alone when there's no about line`() =
        assertEquals("Oshana, Namibia", PlaceLine.toShow("Oshana, Namibia", null))
}
