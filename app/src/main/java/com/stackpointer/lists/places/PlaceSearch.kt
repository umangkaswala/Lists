package com.stackpointer.lists.places

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** One geocoder hit, already reduced to what the picker shows. */
data class PlaceSuggestion(
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double
)

/**
 * Type-and-search place lookup on the device's own [Geocoder].
 *
 * PLAN.md chose this over the Google Places API deliberately: no API key, no
 * billing account, no key rotation. The trade-off is real and shows in the UI —
 * there is no live autocomplete dropdown, results are only as good as the
 * platform geocoder, and on some devices there is no geocoder at all.
 */
class PlaceSearch(private val context: Context) {

    val isAvailable: Boolean get() = Geocoder.isPresent()

    suspend fun search(query: String, limit: Int = 6): Result<List<PlaceSuggestion>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        if (!isAvailable) {
            return Result.failure(IllegalStateException("This phone has no place lookup service."))
        }
        val geocoder = Geocoder(context, Locale.getDefault())
        return runCatching {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.awaitFromLocationName(trimmed, limit)
            } else {
                // Deprecated from API 33 and blocking on every version, so it
                // is kept off the main thread rather than trusted to be quick.
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(trimmed, limit).orEmpty()
                }
            }
            addresses.mapNotNull { it.toSuggestion(trimmed) }
        }
    }

    /**
     * Reverse-geocodes a point, for naming a place picked off the map. Failure
     * is not an error worth surfacing — the caller falls back to coordinates.
     */
    suspend fun describe(latitude: Double, longitude: Double): String? {
        if (!isAvailable) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return runCatching {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.awaitFromLocation(latitude, longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) {
                    geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
                }
            }
            addresses.firstOrNull()?.readableAddress()
        }.getOrNull()
    }
}

/**
 * The API 33+ callback form. The blocking overloads were deprecated because
 * they can sit on a thread for seconds when the network is slow.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private suspend fun Geocoder.awaitFromLocationName(query: String, limit: Int): List<Address> =
    suspendCancellableCoroutine { continuation ->
        getFromLocationName(
            query,
            limit,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(addresses)
                }

                // Not a failure worth throwing on: "no such place" arrives here
                // as well as a genuine lookup error, and the picker's empty
                // state says the same thing either way.
                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
        )
    }

@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
private suspend fun Geocoder.awaitFromLocation(
    latitude: Double,
    longitude: Double,
    limit: Int
): List<Address> = suspendCancellableCoroutine { continuation ->
    getFromLocation(
        latitude,
        longitude,
        limit,
        object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses)
            }

            override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
    )
}

private fun Address.toSuggestion(query: String): PlaceSuggestion? {
    if (!hasLatitude() || !hasLongitude()) return null
    // featureName is often just a house number, which is a poor label on its
    // own; the locality or the user's own words read better.
    val label = listOfNotNull(
        featureName?.takeIf { it.length > 3 && !it.all(Char::isDigit) },
        locality,
        subAdminArea
    ).firstOrNull() ?: query
    return PlaceSuggestion(
        name = label,
        address = readableAddress(),
        latitude = latitude,
        longitude = longitude
    )
}

private fun Address.readableAddress(): String? {
    val lines = (0..maxAddressLineIndex).mapNotNull { getAddressLine(it) }
    if (lines.isNotEmpty()) return lines.joinToString(", ")
    return listOfNotNull(thoroughfare, locality, countryName)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
}
