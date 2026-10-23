package com.mtdetc.picturewindow.store

import com.mtdetc.picturewindow.feed.FeedItem

sealed interface CacheStep {
    data class Evict(val id: String) : CacheStep
    data class Download(val item: FeedItem) : CacheStep
}

/**
 * Decides which photos to keep on the TV when the feed is bigger than the budget. Photos never shown
 * come first, newest batch first; to make room for them, the most recently shown photos go (they'd
 * be the last to come round again). Photos already shown are only downloaded again into free space,
 * so a TV that has seen everything stops downloading until a new batch arrives.
 *
 * Sizes come from [sizeOf], so a TV that also keeps each photo's video budgets for both files. A TV
 * already over the budget (it just started keeping videos, or the budget shrank) evicts down to it:
 * shown photos first, most recently shown first, then unseen ones from the oldest batch.
 * Steps come in order: each eviction sits just before the download it makes room for, so a failed
 * download never costs more than the space it was given.
 */
object CachePlanner {

    fun plan(
        catalog: Catalog,
        cachedIds: Set<String>,
        lastShown: Map<String, Long>,
        currentId: String?,
        budgetBytes: Long,
        sizeOf: (FeedItem) -> Long = FeedItem::bytes,
    ): List<CacheStep> {
        val itemsById = catalog.entries.associate { entry -> entry.item.id to entry.item }
        val steps = mutableListOf<CacheStep>()
        cachedIds.filter { id -> id !in itemsById }.forEach { id -> steps += CacheStep.Evict(id) }

        var usedBytes = cachedIds.mapNotNull(itemsById::get).sumOf(sizeOf)
        val evictable = ArrayDeque(evictionOrder(cachedIds, itemsById.keys, lastShown, currentId))
        val uncached = catalog.entries.map { entry -> entry.item }.filter { item -> item.id !in cachedIds }

        for (item in uncached.filter { item -> item.id !in lastShown }) {
            while (usedBytes + sizeOf(item) > budgetBytes && evictable.isNotEmpty()) {
                val id = evictable.removeFirst()
                steps += CacheStep.Evict(id)
                usedBytes -= sizeOf(itemsById.getValue(id))
            }
            if (usedBytes + sizeOf(item) > budgetBytes) break
            steps += CacheStep.Download(item)
            usedBytes += sizeOf(item)
        }

        for (item in uncached.filter { item -> item.id in lastShown }.sortedBy { item -> lastShown[item.id] }) {
            if (usedBytes + sizeOf(item) > budgetBytes) continue
            steps += CacheStep.Download(item)
            usedBytes += sizeOf(item)
        }

        val trimmable = ArrayDeque(evictable + unseenEvictionOrder(catalog, cachedIds, lastShown, currentId))
        while (usedBytes > budgetBytes && trimmable.isNotEmpty()) {
            val id = trimmable.removeFirst()
            steps += CacheStep.Evict(id)
            usedBytes -= sizeOf(itemsById.getValue(id))
        }
        return steps
    }

    /** Cached photos never shown, oldest batch first and last in each batch first. Never the one on screen. */
    private fun unseenEvictionOrder(
        catalog: Catalog,
        cachedIds: Set<String>,
        lastShown: Map<String, Long>,
        currentId: String?,
    ): List<String> =
        catalog.entries.map { entry -> entry.item.id }
            .filter { id -> id in cachedIds && id !in lastShown && id != currentId }
            .reversed()

    /** Cached photos that may make room, most recently shown first. Never the one on screen. */
    private fun evictionOrder(
        cachedIds: Set<String>,
        catalogIds: Set<String>,
        lastShown: Map<String, Long>,
        currentId: String?,
    ): List<String> =
        cachedIds
            .filter { id -> id in catalogIds && id in lastShown && id != currentId }
            .sortedByDescending { id -> lastShown[id] }
}
