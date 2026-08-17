package com.stackpointer.lists.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Schema-only until Phase 7 (Places & geofencing) — no DAO/repository wired to
// this yet.
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val address: String? = null
)
