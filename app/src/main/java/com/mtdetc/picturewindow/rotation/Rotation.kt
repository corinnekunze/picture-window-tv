package com.mtdetc.picturewindow.rotation

import com.mtdetc.picturewindow.store.CatalogEntry
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Persisted so the rotation survives reboots. Times are wall-clock milliseconds. */
@Serializable
data class RotationState(
    val currentId: String? = null,
    val currentSince: Long = 0,
    val lastShown: Map<String, Long> = emptyMap(),
)

/**
 * Picks the photo on screen. It changes every `intervalMs`, never repeats back to back, prefers
 * photos never shown (newest batch first, random within it), and otherwise picks among the photos
 * shown longest ago.
 */
class Rotation(private val random: Random = Random.Default) {

    /** [available] is the photos on disk, in catalog order. */
    fun advanceIfDue(state: RotationState, available: List<CatalogEntry>, now: Long, intervalMs: Long): RotationState {
        if (available.isEmpty()) return state
        if (isCurrentStillValid(state, available, now, intervalMs)) return state
        val next = pickNext(state, available)
        return RotationState(next.item.id, now, state.lastShown + (next.item.id to now))
    }

    private fun isCurrentStillValid(state: RotationState, available: List<CatalogEntry>, now: Long, intervalMs: Long): Boolean {
        val currentId = state.currentId ?: return false
        if (available.none { entry -> entry.item.id == currentId }) return false
        // A clock that jumped backwards counts as due, so a bad clock can't freeze the rotation.
        return now >= state.currentSince && now - state.currentSince < intervalMs
    }

    private fun pickNext(state: RotationState, available: List<CatalogEntry>): CatalogEntry {
        val candidates = available.filter { entry -> entry.item.id != state.currentId }
        if (candidates.isEmpty()) return available.first()

        val neverShown = candidates.filter { entry -> entry.item.id !in state.lastShown }
        if (neverShown.isNotEmpty()) {
            val newestBatch = neverShown.maxOf { entry -> entry.batch }
            return neverShown.filter { entry -> entry.batch == newestBatch }.random(random)
        }

        val oldestFirst = candidates.sortedBy { entry -> state.lastShown.getValue(entry.item.id) }
        val poolSize = (oldestFirst.size / OLDEST_POOL_DIVISOR).coerceAtLeast(1)
        return oldestFirst.take(poolSize).random(random)
    }

    private companion object {
        /** Re-shown photos come from the oldest quarter, so the order differs each time round. */
        const val OLDEST_POOL_DIVISOR = 4
    }
}
