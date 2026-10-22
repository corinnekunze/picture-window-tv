package com.mtdetc.picturewindow.dream

/**
 * The place line under the credit, unless the about line already says where the photo is
 * ("Great Grey Shrike in Bhigwan, Maharashtra, India" doesn't need "Maharashtra, India" again).
 */
object PlaceLine {
    fun toShow(place: String?, about: String?): String? {
        if (place == null) return null
        if (about == null) return place
        val area = place.substringBefore(',').trim()
        return if (about.contains(area, ignoreCase = true)) null else place
    }
}
