package com.berend.transit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.berend.transit.R
import com.berend.transit.TransitViewModel
import com.berend.transit.api.GeoMatch
import com.berend.transit.api.Itinerary
import com.berend.transit.api.PlanLeg
import com.berend.transit.api.asClock
import com.berend.transit.api.toPlanTime
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

private enum class PickTarget { From, To }

@Composable
fun PlannerScreen(viewModel: TransitViewModel) {
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var pickingTime by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Itinerary?>(null) }
    var walking by remember { mutableStateOf<PlanLeg?>(null) }

    val walk = walking
    if (walk != null) {
        WalkScreen(leg = walk, onBack = { walking = null })
        return
    }

    val detail = selected
    if (detail != null) {
        TripDetailScreen(itinerary = detail, onBack = { selected = null }, onWalk = { walking = it })
        return
    }

    if (pickingTime) {
        DepartureTimePicker(
            initial = viewModel.departAt,
            onConfirm = {
                viewModel.setDeparture(it)
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
        return
    }

    val pick = picking
    if (pick != null) {
        var query by remember(pick) { mutableStateOf("") }
        fun matches(m: GeoMatch) = query.isBlank() || m.name.orEmpty().contains(query, ignoreCase = true)
        val favorites = viewModel.favoritePlaces.filter(::matches)
        val recents = viewModel.recentPlaces
            .filter(::matches)
            .filterNot { viewModel.isFavorite(it) }
        val results = viewModel.placeResults
            .filter { query.isNotBlank() }
            .filterNot { r -> (favorites + recents).any { it.name == r.name && it.city == r.city } }
        val favoriteLabel = stringResource(R.string.favorite)
        val recentLabel = stringResource(R.string.recent)
        val stopLabel = stringResource(R.string.stop)
        val addressLabel = stringResource(R.string.address)
        SearchPicker(
            placeholder = stringResource(if (pick == PickTarget.From) R.string.from_hint else R.string.to_hint),
            query = query,
            onQueryChange = {
                query = it
                viewModel.searchPlaces(it)
            },
            entries = favorites.map { match ->
                PickerEntry(
                    match.name.orEmpty(),
                    listOfNotNull(favoriteLabel, match.city).joinToString(", "),
                    match,
                    favorite = true,
                )
            } + recents.map { match ->
                PickerEntry(
                    match.name.orEmpty(),
                    listOfNotNull(recentLabel, match.city).joinToString(", "),
                    match,
                )
            } + results.map { match ->
                PickerEntry(
                    match.name.orEmpty(),
                    placeSublabel(match, stopLabel, addressLabel),
                    match,
                )
            },
            onSelect = { match ->
                viewModel.addRecentPlace(match)
                if (pick == PickTarget.From) viewModel.fromPlace = match else viewModel.toPlace = match
                picking = null
            },
            onDismiss = { picking = null },
            onToggleFavorite = viewModel::toggleFavorite,
            onLongPress = viewModel::removeRecentPlace,
        )
        return
    }

    PlannerContent(
        viewModel = viewModel,
        onPickFrom = { picking = PickTarget.From },
        onPickTo = { picking = PickTarget.To },
        onPickTime = { pickingTime = true },
        onSelectTrip = { selected = it },
    )
}

private fun placeSublabel(match: GeoMatch, stopLabel: String, addressLabel: String): String? {
    val type = when (match.type) {
        "STOP" -> stopLabel
        "ADDRESS" -> addressLabel
        else -> null
    }
    return listOfNotNull(type, match.city).joinToString(", ").ifBlank { null }
}

@Composable
private fun PlannerContent(
    viewModel: TransitViewModel,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onPickTime: () -> Unit,
    onSelectTrip: (Itinerary) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButtonMMD(onClick = onPickFrom, modifier = Modifier.fillMaxWidth()) {
                    TextMMD(viewModel.fromPlace?.name ?: stringResource(R.string.from), fontSize = 16.sp)
                }
                OutlinedButtonMMD(onClick = onPickTo, modifier = Modifier.fillMaxWidth()) {
                    TextMMD(viewModel.toPlace?.name ?: stringResource(R.string.to), fontSize = 16.sp)
                }
            }
            IconButton(onClick = viewModel::swapPlaces) {
                Icon(
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = stringResource(R.string.cd_swap),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButtonMMD(onClick = viewModel::toggleArriveBy) {
                TextMMD(
                    stringResource(if (viewModel.arriveBy) R.string.arrive else R.string.leave),
                    fontSize = 16.sp,
                )
            }
            OutlinedButtonMMD(onClick = onPickTime, modifier = Modifier.weight(1f)) {
                TextMMD(viewModel.departAt.planLabel(), fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryButtonMMD(
            onClick = viewModel::planTrip,
            enabled = viewModel.fromPlace != null && viewModel.toPlace != null && !viewModel.isPlanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextMMD(
                stringResource(if (viewModel.isPlanning) R.string.planning else R.string.plan_trip),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        viewModel.planError?.let {
            Spacer(modifier = Modifier.height(8.dp))
            TextMMD(stringResource(R.string.plan_error, it))
        }

        Spacer(modifier = Modifier.height(8.dp))

        MonoLazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.weight(1f)) {
            items(viewModel.trips.size) { index ->
                val itinerary = viewModel.trips[index]
                ItineraryRow(itinerary, onClick = { onSelectTrip(itinerary) })
                if (index < viewModel.trips.lastIndex) HorizontalDividerMMD()
            }
        }

        AttributionFooter()
    }
}

// Transitous usage policy requires a visible link to its data sources page,
// and OSM attribution guidelines require crediting OpenStreetMap contributors.
@Composable
private fun AttributionFooter() {
    val context = LocalContext.current
    TextMMD(
        text = stringResource(R.string.attribution),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://transitous.org/sources/")))
                }
            }
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun ItineraryRow(itinerary: Itinerary, onClick: () -> Unit) {
    val minutes = itinerary.duration / 60
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        TextMMD(
            text = "${itinerary.startTime.toPlanTime().asClock()} - ${itinerary.endTime.toPlanTime().asClock()}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        LegSummary(itinerary, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        TextMMD(stringResource(R.string.duration_short, minutes / 60, minutes % 60), fontSize = 16.sp)
    }
}

// Icon plus line name while they fit (up to 2 legs), icons only up to 4,
// then a transfer count.
@Composable
private fun LegSummary(itinerary: Itinerary, modifier: Modifier = Modifier) {
    val legs = itinerary.legs.filter { it.isTransit }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier,
    ) {
        if (legs.size <= 4) {
            legs.forEachIndexed { index, leg ->
                if (index > 0) TextMMD("+", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 3.dp))
                Icon(
                    imageVector = legIcon(leg.mode),
                    contentDescription = leg.displayLabel(),
                    modifier = Modifier.size(20.dp),
                )
                // Trains: the departure track is more useful at a glance than
                // the train's own ID ("EST 9381").
                val name = when (leg.mode) {
                    "BUS", "TRAM", "SUBWAY", "FERRY" -> leg.routeShortName
                    else -> leg.from?.track?.let { stringResource(R.string.track, it) } ?: leg.routeShortName
                }
                if (legs.size <= 2 && !name.isNullOrBlank()) {
                    TextMMD(
                        text = name,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        } else {
            TextMMD(
                pluralStringResource(R.plurals.transfers, itinerary.transfers, itinerary.transfers),
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

private fun legIcon(mode: String?): ImageVector = when (mode) {
    "BUS" -> Icons.Outlined.DirectionsBus
    "TRAM" -> Icons.Outlined.Tram
    "SUBWAY" -> Icons.Outlined.Subway
    "FERRY" -> Icons.Outlined.DirectionsBoat
    else -> Icons.Outlined.Train
}
