package com.mtdetc.picturewindow.rotation

import com.mtdetc.picturewindow.TestData.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RotationTest {
    private val interval = 300_000L
    private val rotation = Rotation(interval, Random(42))

    @Test
    fun `keeps the current photo until the interval passes`() {
        val available = listOf(entry("a"), entry("b"))
        val first = rotation.advanceIfDue(RotationState(), available, now = 1_000)
        assertEquals(first, rotation.advanceIfDue(first, available, now = 1_000 + interval - 1))
        assertNotEquals(first.currentId, rotation.advanceIfDue(first, available, now = 1_000 + interval).currentId)
    }

    @Test
    fun `never shows the same photo twice in a row`() {
        val available = (1..5).map { number -> entry("p$number") }
        var state = RotationState()
        var previousId: String? = null
        repeat(500) { step ->
            state = rotation.advanceIfDue(state, available, now = step * interval)
            assertNotEquals(previousId, state.currentId)
            previousId = state.currentId
        }
    }

    @Test
    fun `shows every never-shown photo of the newest batch before older ones`() {
        val available = listOf(entry("old1", "2026-09"), entry("new1", "2026-10"), entry("new2", "2026-10"))
        var state = RotationState()
        val shown = (0 until 2).map { step ->
            state = rotation.advanceIfDue(state, available, now = step * interval)
            state.currentId
        }
        assertEquals(setOf("new1", "new2"), shown.toSet())
        state = rotation.advanceIfDue(state, available, now = 2 * interval)
        assertEquals("old1", state.currentId)
    }

    @Test
    fun `moves on at once when the current photo disappears`() {
        val state = RotationState(currentId = "gone", currentSince = 0)
        val next = rotation.advanceIfDue(state, listOf(entry("a")), now = 1)
        assertEquals("a", next.currentId)
    }

    @Test
    fun `treats a clock that jumped backwards as due`() {
        val available = listOf(entry("a"), entry("b"))
        val state = RotationState(currentId = "a", currentSince = 10 * interval, lastShown = mapOf("a" to 10 * interval))
        assertEquals("b", rotation.advanceIfDue(state, available, now = 0).currentId)
    }

    @Test
    fun `cycles through everything once all photos have been shown`() {
        val available = (1..8).map { number -> entry("p$number") }
        var state = RotationState()
        val seen = mutableSetOf<String>()
        repeat(40) { step ->
            state = rotation.advanceIfDue(state, available, now = step * interval)
            seen += state.currentId!!
        }
        assertTrue(seen.containsAll(available.map { entry -> entry.item.id }))
    }

    @Test
    fun `leaves the state alone with nothing downloaded`() {
        val state = RotationState(currentId = "a")
        assertEquals(state, rotation.advanceIfDue(state, emptyList(), now = interval * 9))
    }
}
