package com.folderplus.agenda

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folderplus.R
import kotlinx.coroutines.delay
import java.time.LocalDate

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val SETTLE_DELAY_MS = 110L
private const val FLIP_DELAY_MS = 120

/**
 * Pinned date card + scrolling feed. The card shows the day whose group has passed a
 * line 36dp below the top of the feed, and only updates once scrolling has settled.
 */
@Composable
fun AgendaPopup(
    days: List<AgendaDay>?,
    palette: AgendaPalette,
    onOpenEvent: (AgendaEvent) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val plate = Color(palette.plate).copy(alpha = 0.58f)
    val group = Color(palette.group).copy(alpha = 0.58f)
    val card = Color(palette.card).copy(alpha = 0.93f)
    val badge = Color(palette.badge)
    val onCard = Color(palette.onCard)
    val onCardVar = Color(palette.onCardVar)
    val onPlate = Color(palette.onPlate)
    val accent = Color(palette.accent)

    val list = days.orEmpty()
    var active by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(1) }
    val listState = rememberLazyListState()
    val anchorPx = with(LocalDensity.current) { 36.dp.roundToPx() }

    LaunchedEffect(listState, list.size) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (!moving) {
                delay(SETTLE_DELAY_MS)
                val idx = dayAtAnchor(listState, anchorPx)
                if (idx != active && idx in list.indices) {
                    direction = if (idx > active) 1 else -1
                    active = idx
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .clip(RoundedCornerShape(28.dp))
                .background(plate)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            if (days == null) return@Box
            if (list.isEmpty()) {
                Text(
                    stringResource(R.string.agenda_empty),
                    color = onPlate,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                return@Box
            }
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.width(104.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DateCard(
                        day = list[active.coerceIn(list.indices)],
                        direction = direction,
                        card = card, onCard = onCard, onCardVar = onCardVar, accent = accent,
                        onClick = { onOpenDay(list[active.coerceIn(list.indices)].date) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Dots(count = list.size, active = active, accent = accent, idle = onPlate.copy(alpha = 0.3f))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 160.dp),
                ) {
                    itemsIndexed(list, key = { _, d -> d.date.toEpochDay() }) { index, day ->
                        DayGroup(
                            day = day,
                            isActive = index == active,
                            group = group, card = card, badge = badge, onCard = onCard, onCardVar = onCardVar, accent = accent,
                            onOpenEvent = onOpenEvent,
                            onOpenDay = { onOpenDay(day.date) },
                        )
                    }
                }
            }
        }
    }
}

/** The day whose group top has passed the anchor line; the first visible one otherwise. */
private fun dayAtAnchor(state: LazyListState, anchorPx: Int): Int {
    val visible = state.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return 0
    var idx = visible.first().index
    visible.forEach { if (it.offset <= anchorPx) idx = it.index }
    return idx
}

@Composable
private fun DateCard(
    day: AgendaDay,
    direction: Int,
    card: Color, onCard: Color, onCardVar: Color, accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .width(104.dp)
            .height(138.dp)
            .clip(shape)
            .background(card)
            .then(if (day.isToday) Modifier.border(2.dp, accent, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = day,
            transitionSpec = {
                val dir = direction
                (slideInVertically(tween(380, delayMillis = FLIP_DELAY_MS, easing = EmphasizedDecelerate)) { dir * it / 2 } +
                    fadeIn(tween(380, delayMillis = FLIP_DELAY_MS, easing = EmphasizedDecelerate)))
                    .togetherWith(
                        slideOutVertically(tween(220, easing = EmphasizedAccelerate)) { -dir * it / 2 } +
                            fadeOut(tween(220, easing = EmphasizedAccelerate)),
                    )
            },
            label = "date",
        ) { d ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(AgendaFormat.dow(d.date), color = onCardVar, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
                Text(d.date.dayOfMonth.toString(), color = onCard, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text(AgendaFormat.mon(d.date), color = onCardVar, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            }
        }
    }
}

@Composable
private fun Dots(count: Int, active: Int, accent: Color, idle: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(14)) { i ->
            val width by animateDpAsState(if (i == active) 14.dp else 4.dp, tween(300, easing = EmphasizedDecelerate), label = "dot")
            Box(Modifier.width(width).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i == active) accent else idle))
        }
    }
}

@Composable
private fun DayGroup(
    day: AgendaDay,
    isActive: Boolean,
    group: Color, card: Color, badge: Color, onCard: Color, onCardVar: Color, accent: Color,
    onOpenEvent: (AgendaEvent) -> Unit,
    onOpenDay: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(group)
            .then(if (isActive) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (day.events.isEmpty()) {
            FreeCard(onCard = onCard, onCardVar = onCardVar, onClick = onOpenDay)
        } else {
            day.events.forEach { event ->
                EventCard(day.date, event, card, badge, onCard, onCardVar) { onOpenEvent(event) }
            }
        }
    }
}

@Composable
private fun EventCard(
    date: LocalDate,
    event: AgendaEvent,
    card: Color, badge: Color, onCard: Color, onCardVar: Color,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(badge), contentAlignment = Alignment.Center) {
            Icon(painterResource(CalendarAgenda.iconFor(event.title)), contentDescription = null, tint = onCard, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(AgendaFormat.whenLabel(context, date, event), color = onCardVar, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(event.title, color = onCard, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FreeCard(onCard: Color, onCardVar: Color, onClick: () -> Unit) {
    val dash = onCard.copy(alpha = 0.3f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.agenda_nothing_planned), color = onCardVar, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
