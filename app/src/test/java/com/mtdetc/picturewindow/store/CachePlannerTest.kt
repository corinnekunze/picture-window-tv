package com.mtdetc.picturewindow.store

import com.mtdetc.picturewindow.TestData.entry
import org.junit.Assert.assertEquals
import org.junit.Test

class CachePlannerTest {

    private fun downloads(steps: List<CacheStep>) =
        steps.filterIsInstance<CacheStep.Download>().map { step -> step.item.id }

    private fun evictions(steps: List<CacheStep>) =
        steps.filterIsInstance<CacheStep.Evict>().map { step -> step.id }

    @Test
    fun `fills the budget newest batch first`() {
        val catalog = Catalog(listOf(entry("new1", "2026-10"), entry("new2", "2026-10"), entry("old1", "2026-09")))
        val steps = CachePlanner.plan(catalog, emptySet(), emptyMap(), currentId = null, budgetBytes = 200)
        assertEquals(listOf("new1", "new2"), downloads(steps))
    }

    @Test
    fun `evicts the most recently shown photos to make room for unseen ones`() {
        val catalog = Catalog(listOf(entry("a"), entry("b"), entry("c"), entry("unseen")))
        val cached = setOf("a", "b", "c")
        val lastShown = mapOf("a" to 1L, "b" to 3L, "c" to 2L)
        val steps = CachePlanner.plan(catalog, cached, lastShown, currentId = "a", budgetBytes = 300)
        assertEquals(listOf(CacheStep.Evict("b"), CacheStep.Download(catalog.entries[3].item)), steps)
    }

    @Test
    fun `never evicts the photo on screen`() {
        val catalog = Catalog(listOf(entry("current"), entry("unseen")))
        val steps = CachePlanner.plan(catalog, setOf("current"), mapOf("current" to 5L), currentId = "current", budgetBytes = 100)
        assertEquals(emptyList<CacheStep>(), steps)
    }

    @Test
    fun `removes revoked and stale photos first`() {
        val catalog = Catalog(listOf(entry("kept")))
        val steps = CachePlanner.plan(catalog, setOf("kept", "revoked"), emptyMap(), currentId = "revoked", budgetBytes = 1_000)
        assertEquals(listOf("revoked"), evictions(steps))
        assertEquals(emptyList<String>(), downloads(steps))
    }

    @Test
    fun `re-downloads shown photos only into free space, oldest shown first`() {
        val catalog = Catalog(listOf(entry("cached"), entry("shownRecently"), entry("shownLongAgo")))
        val cached = setOf("cached")
        val lastShown = mapOf("cached" to 9L, "shownRecently" to 8L, "shownLongAgo" to 1L)
        val steps = CachePlanner.plan(catalog, cached, lastShown, currentId = "cached", budgetBytes = 200)
        assertEquals(listOf("shownLongAgo"), downloads(steps))
        assertEquals(emptyList<String>(), evictions(steps))
    }

    @Test
    fun `budgets for each photo's video as well as its image`() {
        val catalog = Catalog(listOf(entry("a"), entry("b"), entry("c")))
        val steps = CachePlanner.plan(catalog, emptySet(), emptyMap(), currentId = null, budgetBytes = 400) { item -> item.bytes * 2 }
        assertEquals(listOf("a", "b"), downloads(steps))
    }

    @Test
    fun `stops downloading once a TV has everything it can hold`() {
        val catalog = Catalog(listOf(entry("a"), entry("b"), entry("evicted")))
        val cached = setOf("a", "b")
        val lastShown = mapOf("a" to 1L, "b" to 2L, "evicted" to 3L)
        assertEquals(emptyList<CacheStep>(), CachePlanner.plan(catalog, cached, lastShown, currentId = "a", budgetBytes = 200))
    }
}
