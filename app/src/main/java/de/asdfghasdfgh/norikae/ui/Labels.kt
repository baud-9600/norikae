package de.asdfghasdfgh.norikae.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.asdfghasdfgh.norikae.R
import de.asdfghasdfgh.norikae.api.PlanLeg
import de.asdfghasdfgh.norikae.api.asDayLabel
import de.asdfghasdfgh.norikae.api.asPlanLabel
import java.time.LocalDate
import java.time.OffsetDateTime

/** "Bus 44", "Metro 51", "U-Bahn 51", or the train's own name ("Sprinter", "ECD 9563"). */
@Composable
fun PlanLeg.displayLabel(): String = when (mode) {
    "BUS" -> stringResource(R.string.mode_bus, routeShortName.orEmpty()).trim()
    "TRAM" -> stringResource(R.string.mode_tram, routeShortName.orEmpty()).trim()
    "SUBWAY" -> stringResource(R.string.mode_subway, routeShortName.orEmpty()).trim()
    "FERRY" -> stringResource(R.string.mode_ferry, routeShortName.orEmpty()).trim()
    else -> routeShortName ?: stringResource(R.string.mode_train)
}

/** "Today", "Tomorrow", or a localized day and month. */
@Composable
fun LocalDate.dayLabel(): String = asDayLabel(
    today = LocalDate.now(),
    todayLabel = stringResource(R.string.today),
    tomorrowLabel = stringResource(R.string.tomorrow),
    pattern = stringResource(R.string.date_pattern),
)

/** Clock time for today, day label plus clock otherwise; "now" when unset. */
@Composable
fun OffsetDateTime?.planLabel(): String = this?.asPlanLabel(
    today = LocalDate.now(),
    todayLabel = stringResource(R.string.today),
    tomorrowLabel = stringResource(R.string.tomorrow),
    pattern = stringResource(R.string.date_pattern),
) ?: stringResource(R.string.leave_now)
