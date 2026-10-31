package com.widgetsplus.agenda

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgetsplus.R
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** One line of the 30-day list: a month heading, or a day with its index on the widget. */
private sealed interface MonthRow {
    data class Heading(val name: String) : MonthRow
    data class Day(val index: Int, val day: AgendaDay) : MonthRow
}

/**
 * Frosted sheet centred on the screen with the next 30 days. The list scrolls with a finger;
 * the bar on the right is a scrubber: press and drag it to race through the days while a bubble
 * shows the day you are on. Drag the handle or header down to dismiss.
 */
@Composable
fun AgendaMonthSheet(
    days: List<AgendaDay>?,
    scrollTo: LocalDate,
    palette: AgendaPalette,
    onShowDay: (Int) -> Unit,
    onOpenEvent: (AgendaEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    val dismissPx = with(density) { 90.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val settled by animateFloatAsState(drag, tween(if (drag == 0f) 220 else 0), label = "sheet")

    val plate = Color(palette.plate).copy(alpha = 0.72f)
    val group = Color(palette.group).copy(alpha = 0.58f)
    val card = Color(palette.card).copy(alpha = 0.93f)
    val badge = Color(palette.badge)
    val accent = Color(palette.accent)
    val onCard = Color(palette.onCard)
    val onCardVar = Color(palette.onCardVar)
    val onPlate = Color(palette.onPlate)
    val onPlateVar = Color(palette.onPlateVar)

    val rows = remember(days) {
        val out = mutableListOf<MonthRow>()
        var month = -1
        days.orEmpty().forEachIndexed { i, d ->
            if (d.date.monthValue != month) { out += MonthRow.Heading(AgendaFormat.monthName(d.date)); month = d.date.monthValue }
            out += MonthRow.Day(i, d)
        }
        out
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(rows) {
        val target = rows.indexOfFirst { it is MonthRow.Day && it.day.date == scrollTo }
        if (target > 0) listState.scrollToItem(if (rows[target - 1] is MonthRow.Heading) target - 1 else target)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, settled.roundToInt()) }
                .graphicsLayer { alpha = 1f - (settled / (dismissPx * 4)).coerceIn(0f, 0.6f) }
                .clip(RoundedCornerShape(28.dp))
                .background(plate)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // handle and header: drag down to dismiss
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag > dismissPx) onDismiss() else drag = 0f },
                        onDragCancel = { drag = 0f },
                        onVerticalDrag = { change, dy -> change.consume(); drag = (drag + dy).coerceAtLeast(0f) },
                    )
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(onPlate.copy(alpha = 0.35f)))
                }
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.agenda_next_days), color = onPlate, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val first = days?.firstOrNull()?.date
                    val last = days?.lastOrNull()?.date
                    if (first != null && last != null) {
                        Text(
                            stringResource(R.string.agenda_range, AgendaFormat.shortDate(first), AgendaFormat.shortDate(last)),
                            color = onPlateVar, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp)).background(group)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 26.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rows, key = { r -> if (r is MonthRow.Day) "d${r.index}" else "h${(r as MonthRow.Heading).name}" }) { row ->
                        when (row) {
                            is MonthRow.Heading -> Text(
                                row.name.uppercase(), color = onPlate, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                            )
                            is MonthRow.Day -> DayLine(row, card, badge, accent, onCard, onCardVar, context, onShowDay, onOpenEvent)
                        }
                    }
                }

                // the scrubber: press and drag anywhere on the bar
                val trackPx = with(density) { (maxHeight - 20.dp).toPx() }
                val thumbPx = with(density) { 40.dp.toPx() }
                var scrubbing by remember { mutableStateOf(false) }
                val fraction by remember(rows) {
                    derivedStateOf {
                        val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                        val maxFirst = (rows.size - visible).coerceAtLeast(1)
                        (listState.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
                    }
                }
                val dayOnTop by remember(rows) {
                    derivedStateOf {
                        rows.drop(listState.firstVisibleItemIndex).firstOrNull { it is MonthRow.Day } as MonthRow.Day?
                    }
                }
                fun scrubTo(y: Float) {
                    val f = ((y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)
                    val target = (f * (rows.size - 1)).roundToInt()
                    scope.launch { listState.scrollToItem(target) }
                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .width(16.dp)
                        .fillMaxHeight()
                        .pointerInput(rows) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                scrubbing = true
                                scrubTo(down.position.y)
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    scrubTo(change.position.y)
                                    change.consume()
                                }
                                scrubbing = false
                            }
                        },
                ) {
                    Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(onPlate.copy(alpha = 0.22f)))
                    Box(
                        Modifier
                            .offset { IntOffset(0, (fraction * (trackPx - thumbPx)).roundToInt()) }
                            .align(Alignment.TopCenter)
                            .width(if (scrubbing) 14.dp else 10.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(accent),
                    )
                }
                if (scrubbing) {
                    dayOnTop?.let { d ->
                        Text(
                            AgendaFormat.dayTitle(d.day.date),
                            color = onCard, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset { IntOffset(-with(density) { 26.dp.roundToPx() }, (fraction * (trackPx - thumbPx)).roundToInt() + with(density) { 18.dp.roundToPx() }) }
                                .clip(RoundedCornerShape(999.dp))
                                .background(accent)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayLine(
    row: MonthRow.Day,
    card: Color, badge: Color, accent: Color, onCard: Color, onCardVar: Color,
    context: android.content.Context,
    onShowDay: (Int) -> Unit,
    onOpenEvent: (AgendaEvent) -> Unit,
) {
    val day = row.day
    val label = stringResource(R.string.agenda_show_day, AgendaFormat.dayTitle(day.date))
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(card).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var chip = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(badge.copy(alpha = 0.55f))
        if (day.isToday) chip = chip.border(2.dp, accent, RoundedCornerShape(12.dp))
        Column(
            chip.clickable { onShowDay(row.index) }.semantics { contentDescription = label }.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(AgendaFormat.dow(day.date), color = onCardVar, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
            Text(day.date.dayOfMonth.toString(), color = onCard, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            if (day.events.isEmpty()) {
                Text(stringResource(R.string.agenda_nothing_planned), color = onCardVar, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            }
            day.events.forEach { event ->
                val time = if (event.allDay) stringResource(R.string.agenda_all_day) else AgendaFormat.time(context, event.begin)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = onCardVar, fontWeight = FontWeight.Medium)) { append(time) }
                        append("  ")
                        append(event.title)
                    },
                    color = onCard, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onOpenEvent(event) }.padding(vertical = 2.dp),
                )
            }
        }
    }
}
