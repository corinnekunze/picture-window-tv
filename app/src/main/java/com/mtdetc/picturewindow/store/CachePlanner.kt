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
 * Sizes come from [sizeOf], so a TV that also keeps each photo's video budgets for both files.
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
        return steps
    }

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
