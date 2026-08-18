package com.stackpointer.lists.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.repository.PlaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlaceReminderUiModel(
    val id: Long,
    val title: String,
    val trigger: PlaceTrigger,
    /** "Arrive · within 200 m · only 8:00 am–10:00 pm" */
    val meta: String
)

data class PlaceGroupUiModel(
    val placeId: Long,
    val name: String,
    val address: String?,
    val radiusMeters: Int,
    val reminders: List<PlaceReminderUiModel>
)

data class PlacesUiState(
    val isLoading: Boolean = true,
    val groups: List<PlaceGroupUiModel> = emptyList(),
    val geofenceStatus: GeofenceStatus = GeofenceStatus()
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
    val totalTriggers: Int get() = groups.sumOf { it.reminders.size }
}

private val WINDOW_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

class PlacesViewModel(
    private val placeRepository: PlaceRepository,
    private val registrar: GeofenceRegistrar
) : ViewModel() {

    val uiState: StateFlow<PlacesUiState> = combine(
        placeRepository.observePlaces(),
        placeRepository.observePlaceReminders(),
        registrar.status
    ) { places, reminders, status ->
        PlacesUiState(
            isLoading = false,
            groups = buildGroups(places, reminders),
            geofenceStatus = status
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())

    /** Called when the screen regains focus, e.g. back from system settings. */
    fun refreshGeofences() {
        registrar.requestSync()
    }

    fun deletePlace(placeId: Long) {
        viewModelScope.launch { placeRepository.deletePlace(placeId) }
    }

    private fun buildGroups(
        places: List<PlaceEntity>,
        reminders: List<ReminderEntity>
    ): List<PlaceGroupUiModel> {
        val byPlace = reminders.groupBy { it.placeId }
        // Every saved place is listed, including ones with nothing attached:
        // a place with no reminders is still something the user created and
        // may want to delete, and hiding it would leave no way to.
        return places.map { place ->
            PlaceGroupUiModel(
                placeId = place.id,
                name = place.name,
                address = place.address,
                radiusMeters = place.radiusMeters,
                reminders = byPlace[place.id].orEmpty().map { toUiModel(it, place) }
            )
        }
    }

    private fun toUiModel(reminder: ReminderEntity, place: PlaceEntity): PlaceReminderUiModel {
        val trigger = PlaceTrigger.parse(reminder.placeTrigger) ?: PlaceTrigger.ARRIVE
        val parts = mutableListOf<String>()
        parts += if (trigger == PlaceTrigger.ARRIVE) "Arrive" else "Leave"
        parts += if (place.radiusMeters >= 1000) "within 1 km" else "within ${place.radiusMeters} m"
        val start = reminder.placeWindowStartMinute
        val end = reminder.placeWindowEndMinute
        if (start != null && end != null) {
            parts += "only ${formatMinute(start)}–${formatMinute(end)}"
        }
        reminder.placeWindowDays?.takeIf { it.isNotBlank() }?.let { parts += it.replace(",", " ") }
        return PlaceReminderUiModel(
            id = reminder.id,
            title = reminder.title,
            trigger = trigger,
            meta = parts.joinToString(" · ")
        )
    }

    private fun formatMinute(minute: Int): String =
        LocalTime.ofSecondOfDay((minute * 60).toLong())
            .format(WINDOW_FORMAT)
            .lowercase(Locale.ENGLISH)

    class Factory(
        private val placeRepository: PlaceRepository,
        private val registrar: GeofenceRegistrar
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlacesViewModel(placeRepository, registrar) as T
        }
    }
}
