package de.asdfghasdfgh.norikae.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// Replacement for LazyColumnMMD, whose own scrollbar crashes with
// "Cannot coerce value to an empty range" whenever its canvas is drawn at
// (near) zero height, and swallows swipes when it under-estimates content.
// The scrolling stays a plain LazyColumn with the gestures left to the
// system; the bar beside it is MMD's (chevrons, bordered track, thumb), drawn
// in its own column so neither rows nor times can run under it.
@Composable
fun MonoLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val layoutInfo by remember { derivedStateOf { state.layoutInfo } }
    val isScrollable by remember {
        derivedStateOf { state.canScrollBackward || state.canScrollForward }
    }

    Row(
        modifier = modifier
            // As MMD does: nothing is drawn until the rows are there, so the
            // screen does not flash.
            .alpha(if (layoutInfo.totalItemsCount > 0) 1f else 0f),
    ) {
        LazyColumn(
            state = state,
            contentPadding = contentPadding,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            content = content,
        )
        if (isScrollable) {
            MmdScrollbar(state = state, layoutInfo = layoutInfo)
        }
    }
}

/**
 * MMD's vertical scrollbar (MMD 1.0.2, `LazyMMD.kt`, Apache-2.0, Copyright
 * 2025 The Android Open Source Project, modified by Mudita Sp. z o.o.), with
 * the clamping MMD leaves out: an empty or not-yet-measured list draws no bar,
 * and the thumb can never be taller than its track.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MmdScrollbar(state: LazyListState, layoutInfo: LazyListLayoutInfo) {
    val visible = layoutInfo.visibleItemsInfo
    val total = layoutInfo.totalItemsCount
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    if (visible.isEmpty() || total <= 0 || viewport <= 0f) return

    val scope = rememberCoroutineScope()
    val first = visible.first()
    val last = visible.last()
    val firstFully = first.offset >= 0
    val lastFully = last.offset + last.size <= viewport
    val atStart = first.index == 0 && firstFully
    val atEnd = last.index == total - 1 && lastFully

    fun turn(direction: Int) {
        val target = (state.firstVisibleItemIndex + direction * MMD_SCROLL_STEP)
            .coerceIn(0, total - 1)
        scope.launch { state.scrollToItem(target) }
    }

    val track = MaterialTheme.colorScheme.onPrimary
    val slider = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(
                if (atStart) com.mudita.mmd.R.drawable.chevron_dotted_up
                else com.mudita.mmd.R.drawable.chevron_filled_up,
            ),
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .size(24.dp)
                .combinedClickable(
                    onClick = { turn(1) },
                    onLongClick = { scope.launch { state.scrollToItem(0) } },
                ),
        )

        Canvas(
            modifier = Modifier
                .width(8.dp)
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = slider,
                    shape = RoundedCornerShape(4.dp),
                )
                .pointerInput(total) {
                    detectTapGestures { offset ->
                        if (size.height <= 0) return@detectTapGestures
                        val count = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                        val thumb = size.height.toFloat() * count / total
                        val target = ((offset.y - thumb / 2) / size.height * total)
                            .toInt()
                            .coerceIn(0, total - 1)
                        scope.launch { state.scrollToItem(target) }
                    }
                },
        ) {
            if (size.height <= 0f) return@Canvas

            var visibleCount = (last.index - first.index + 1).coerceIn(1, total)
            if (!firstFully || !lastFully) visibleCount--
            visibleCount = visibleCount.coerceIn(1, total)

            val thumbHeight = (size.height * visibleCount / total)
                .coerceAtLeast(max(size.height * 0.05f, MMD_MIN_THUMB))
                .coerceAtMost(size.height)
            val firstVisible = if (firstFully) {
                first.index
            } else {
                min(first.index + 1, (total - visibleCount).coerceAtLeast(0))
            }
            val maxOffset = (size.height - thumbHeight).coerceAtLeast(0f)
            val denominator = (total - visibleCount).coerceAtLeast(1)
            val offset = (firstVisible.toFloat() / denominator * maxOffset).coerceIn(0f, maxOffset)
            val corner = CornerRadius(size.width / 2, size.width / 2)

            // Track, then thumb. A list that fits shows only the track.
            drawRoundRect(
                color = track,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = if (visibleCount >= total) track else slider,
                topLeft = Offset(0f, offset),
                size = Size(size.width, thumbHeight),
                cornerRadius = corner,
            )
        }

        Icon(
            painter = painterResource(
                if (atEnd) com.mudita.mmd.R.drawable.chevron_dotted_down
                else com.mudita.mmd.R.drawable.chevron_filled_down,
            ),
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .size(24.dp)
                .combinedClickable(
                    onClick = { turn(-1) },
                    onLongClick = { scope.launch { state.scrollToItem(total - 1) } },
                ),
        )
    }
}

// MMD's own step and minimum thumb, kept so the bar behaves as the other apps do.
private const val MMD_SCROLL_STEP = 4
private const val MMD_MIN_THUMB = 16f
