package com.folderplus.agenda

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folderplus.R
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frosted sheet anchored near the widget with that day's events. Drag down (from the
 * grab bar, the header, or the list when it is at the top) to dismiss.
 */
@Composable
fun AgendaDaySheet(
    date: LocalDate,
    day: AgendaDay?,
    palette: AgendaPalette,
    anchorTopPx: Int,
    onOpenEvent: (AgendaEvent) -> Unit,
    onOpenLocation: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val anchorTop = if (anchorTopPx >= 0) with(density) { (anchorTopPx.toFloat()).toDp() - 56.dp } else screenHeight * 0.18f
    val top = anchorTop.coerceIn(48.dp, screenHeight * 0.45f)
    val maxHeight = screenHeight - top - 96.dp
    val dismissPx = with(density) { 90.dp.toPx() }

    var drag by remember { mutableFloatStateOf(0f) }
    val settled by animateFloatAsState(drag, tween(if (drag == 0f) 220 else 0), label = "sheet")
    val listState = rememberLazyListState()
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0 && atTop) { drag += available.y; return Offset(0f, available.y) }
                if (available.y < 0 && drag > 0) { val d = max(-drag, available.y); drag += d; return Offset(0f, d) }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag > dismissPx || (drag > 0 && available.y > 1800)) { onDismiss(); return available }
                drag = 0f
                return Velocity.Zero
            }
        }
    }

    val plate = Color(palette.plate).copy(alpha = 0.72f)
    val group = Color(palette.group).copy(alpha = 0.58f)
    val card = Color(palette.card).copy(alpha = 0.93f)
    val onPlate = Color(palette.onPlate)
    val onPlateVar = Color(palette.onPlateVar)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .padding(top = top)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .offset { IntOffset(0, settled.roundToInt()) }
                .graphicsLayer { alpha = 1f - (settled / (dismissPx * 4)).coerceIn(0f, 0.6f) }
                .clip(RoundedCornerShape(28.dp))
                .background(plate)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag > dismissPx) onDismiss() else drag = 0f },
                        onDragCancel = { drag = 0f },
                        onVerticalDrag = { change, dy -> change.consume(); drag = (drag + dy).coerceAtLeast(0f) },
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(onPlate.copy(alpha = 0.35f)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(AgendaFormat.dayTitle(date), color = onPlate, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val count = day?.events?.size ?: 0
                Text(pluralStringResource(R.plurals.agenda_event_count, count, count), color = onPlateVar, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(group)
                    .padding(6.dp)
                    .nestedScroll(connection),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val events = day?.events.orEmpty()
                if (day != null && events.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.agenda_nothing_planned), color = Color(palette.onCardVar), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                items(events, key = { it.id }) { event ->
                    EventDetailCard(event, card, Color(palette.badge), Color(palette.onCard), Color(palette.onCardVar), onOpenEvent, onOpenLocation)
                }
            }
        }
    }
}

@Composable
private fun EventDetailCard(
    event: AgendaEvent,
    card: Color, badge: Color, onCard: Color, onCardVar: Color,
    onOpenEvent: (AgendaEvent) -> Unit,
    onOpenLocation: (String) -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .clickable { onOpenEvent(event) }
            .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(badge), contentAlignment = Alignment.Center) {
            Icon(painterResource(CalendarAgenda.iconFor(event.title)), contentDescription = null, tint = onCard, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(AgendaFormat.timeRange(context, event), color = onCardVar, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            Text(event.title, color = onCard, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (event.location.isNotEmpty()) {
                val link = AgendaFormat.isLink(event.location)
                Row(
                    Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(badge.copy(alpha = 0.45f))
                        .clickable { onOpenLocation(event.location) }
                        .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(if (link) Icons.Default.Link else Icons.Default.Place, contentDescription = null, tint = onCard, modifier = Modifier.size(14.dp))
                    Text(
                        if (link) stringResource(R.string.agenda_join_link) else event.location,
                        color = onCard, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (event.description.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(event.description, color = onCardVar, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
