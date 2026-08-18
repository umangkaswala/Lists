package com.stackpointer.lists.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved location a reminder can be tied to.
 *
 * Coordinates come from the on-device [android.location.Geocoder] rather than
 * the paid Places API (see PLAN.md), so [address] is whatever the geocoder gave
 * back and may be null for a place picked off the map.
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /**
     * Google's geofencing is unreliable below about 100 m, so the UI offers
     * 100 / 200 / 500 / 1000 and nothing smaller.
     */
    val radiusMeters: Int,
    val address: String? = null
)
