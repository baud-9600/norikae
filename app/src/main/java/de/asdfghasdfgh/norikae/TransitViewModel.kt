package de.asdfghasdfgh.norikae

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.asdfghasdfgh.norikae.api.GeoMatch
import de.asdfghasdfgh.norikae.api.Itinerary
import de.asdfghasdfgh.norikae.api.createTransitousApi
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the plan may use: everything, local transport only, or long-distance only. */
enum class TransitFilter { All, Local, LongDistance }

class TransitViewModel(application: Application) : AndroidViewModel(application) {

    private val transitous by lazy { createTransitousApi() }
    private val prefs by lazy { application.getSharedPreferences("norikae", Context.MODE_PRIVATE) }

    var fromPlace by mutableStateOf<GeoMatch?>(null)
    var toPlace by mutableStateOf<GeoMatch?>(null)
    var departAt by mutableStateOf<OffsetDateTime?>(null); private set // null = leave now
    var arriveBy by mutableStateOf(false); private set
    var transitFilter by mutableStateOf(TransitFilter.All); private set
    var trips by mutableStateOf<List<Itinerary>>(emptyList()); private set
    var isPlanning by mutableStateOf(false); private set
    var planError by mutableStateOf<String?>(null); private set
    var hasPlanned by mutableStateOf(false); private set

    var placeResults by mutableStateOf<List<GeoMatch>>(emptyList()); private set
    var recentPlaces by mutableStateOf<List<GeoMatch>>(emptyList()); private set
    var favoritePlaces by mutableStateOf<List<GeoMatch>>(emptyList()); private set
    private var placeSearchJob: Job? = null

    init {
        recentPlaces = loadPlaces("recent_places")
        favoritePlaces = loadPlaces("favorite_places")
        transitFilter = runCatching {
            TransitFilter.valueOf(prefs.getString("transit_filter", "") ?: "")
        }.getOrDefault(TransitFilter.All)
    }

    /** Remember a picked place; most recent first, deduplicated, persisted. */
    fun addRecentPlace(match: GeoMatch) {
        recentPlaces = (listOf(match) + recentPlaces.filterNot { samePlace(it, match) }).take(8)
        savePlaces("recent_places", recentPlaces)
    }

    fun removeRecentPlace(match: GeoMatch) {
        recentPlaces = recentPlaces.filterNot { samePlace(it, match) }
        savePlaces("recent_places", recentPlaces)
    }

    fun isFavorite(match: GeoMatch): Boolean = favoritePlaces.any { samePlace(it, match) }

    fun toggleFavorite(match: GeoMatch) {
        favoritePlaces =
            if (isFavorite(match)) favoritePlaces.filterNot { samePlace(it, match) }
            else favoritePlaces + match
        savePlaces("favorite_places", favoritePlaces)
    }

    private fun samePlace(a: GeoMatch, b: GeoMatch) = a.name == b.name && a.city == b.city

    private fun loadPlaces(key: String): List<GeoMatch> = runCatching {
        Gson().fromJson(prefs.getString(key, null), Array<GeoMatch>::class.java)?.toList()
    }.getOrNull().orEmpty()

    private fun savePlaces(key: String, places: List<GeoMatch>) {
        prefs.edit().putString(key, Gson().toJson(places)).apply()
    }

    fun swapPlaces() {
        val from = fromPlace
        fromPlace = toPlace
        toPlace = from
    }

    /** Set the local/long-distance filter and, when results are on screen, ask again. */
    fun selectTransitFilter(filter: TransitFilter) {
        if (filter == transitFilter) return
        transitFilter = filter
        prefs.edit().putString("transit_filter", filter.name).apply()
        if (!isPlanning && (hasPlanned || trips.isNotEmpty()) && fromPlace != null && toPlace != null) planTrip()
    }

    /** Set an explicit planning time; null returns to "leave now". */
    fun setDeparture(time: OffsetDateTime?) {
        if (time == null) resetDeparture() else departAt = time
    }

    /** Toggle between "leave at" and "arrive by"; arrive-by needs a concrete time to aim for. */
    fun toggleArriveBy() {
        arriveBy = !arriveBy
        if (arriveBy && departAt == null) {
            departAt = OffsetDateTime.now(ZoneId.systemDefault()).plusHours(1)
        }
    }

    fun resetDeparture() {
        departAt = null
        arriveBy = false
    }

    fun searchPlaces(query: String) {
        placeSearchJob?.cancel()
        if (query.length < 3) {
            placeResults = emptyList()
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(300)
            // Failures just leave the previous type-ahead results in place
            runCatching { transitous.geocode(query) }.onSuccess { matches ->
                placeResults = matches
                    .filter { it.name != null && it.place != null }
                    .distinctBy { it.name to it.city }
                    .take(10)
            }
        }
    }

    fun planTrip() {
        val from = fromPlace?.place ?: return
        val to = toPlace?.place ?: return
        viewModelScope.launch {
            isPlanning = true
            planError = null
            hasPlanned = false
            runCatching {
                transitous.plan(
                    from,
                    to,
                    time = departAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    arriveBy = if (arriveBy) true else null,
                    transitModes = transitFilter.transitModes(),
                )
            }
                .onSuccess { response ->
                    // Arrive-by results come back chronologically; put the itinerary
                    // arriving closest to the requested time on top
                    trips =
                        if (arriveBy) response.itineraries.sortedByDescending { it.endTime.orEmpty() }
                        else response.itineraries
                }
                .onFailure {
                    planError = it.message ?: getApplication<Application>().getString(R.string.plan_failed)
                    trips = emptyList()
                }
            hasPlanned = true
            isPlanning = false
        }
    }
}

// MOTIS transitModes. "TRANSIT" is deliberately absent from both lists: it
// expands to every mode, long-distance included. RAIL is absent for the same
// reason; the local set names its regional and suburban parts instead.
internal fun TransitFilter.transitModes(): String? = when (this) {
    TransitFilter.All -> null
    TransitFilter.Local -> LOCAL_TRANSIT_MODES
    TransitFilter.LongDistance -> LONG_DISTANCE_TRANSIT_MODES
}

private const val LOCAL_TRANSIT_MODES =
    "TRAM,SUBWAY,FERRY,BUS,REGIONAL_RAIL,SUBURBAN,FUNICULAR,AERIAL_LIFT,ODM,RIDE_SHARING,FLEX,OTHER"
private const val LONG_DISTANCE_TRANSIT_MODES =
    "HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,COACH,AIRPLANE"
