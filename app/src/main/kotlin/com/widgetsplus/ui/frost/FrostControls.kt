package com.widgetsplus.ui.frost

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.widgetsplus.ui.common.Option

/** What sits at the end of a [FrostRow]. */
sealed interface Trailing {
    data object None : Trailing
    data object Chevron : Trailing
    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : Trailing
    /** A small icon button, such as remove. */
    data class Action(val icon: String, val description: String, val onClick: () -> Unit) : Trailing
}

/** The wash over the wallpaper that every page sits on. */
@Composable
fun FrostBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(LocalFrost.current.scrim), content = content)
}

/**
 * The top of a page: a round back button, then the small overline and the bold title, then any
 * round [actions]. No back button when [onBack] is null.
 */
@Composable
fun FrostHeader(
    title: String,
    overline: String,
    onBack: (() -> Unit)?,
    backIcon: String = FrostIcons.ChevronLeft,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val frost = LocalFrost.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) FrostCircleButton(backIcon, backDescription, onBack)
        Column(Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp)) {
            Text(overline.uppercase(), style = FrostType.overline, color = frost.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = FrostType.title, color = frost.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/**
 * A frost settings page over the wallpaper: the [FrostHeader], then the group cards, 14 dp from
 * the sides with [spacing] between them.
 */
@Composable
fun FrostPage(
    title: String,
    overline: String,
    onBack: (() -> Unit)?,
    backIcon: String = FrostIcons.ChevronLeft,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    spacing: Dp = 12.dp,
    bottomSpace: Dp = 0.dp,
    overlay: @Composable BoxScope.(bottomInset: Dp) -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    FrostBackground {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + 12.dp, bottom = bottom + 18.dp + bottomSpace),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Box(Modifier.padding(bottom = if (spacing < 12.dp) 12.dp - spacing else 0.dp)) {
                    FrostHeader(title, overline, onBack, backIcon, backDescription, actions)
                }
            }
            content()
        }
        overlay(bottom)
    }
}

/** A 44 dp round frost button with a 20 dp glyph. */
@Composable
fun FrostCircleButton(icon: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val frost = LocalFrost.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .frost(frost, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else 0.4f),
    ) { FrostIcon(icon, frost.textSecondary, size = 20.dp) }
}

/** A group card: its label in the accent (when it has one), then its rows with hairlines between them. */
@Composable
fun FrostGroup(label: String?, rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    val frost = LocalFrost.current
    Column(modifier.fillMaxWidth().frost(frost).padding(start = 8.dp, end = 8.dp, top = if (label != null) 10.dp else 6.dp, bottom = if (label != null) 12.dp else 6.dp)) {
        if (label != null) GroupLabel(label)
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = frost.divider, modifier = Modifier.padding(horizontal = 10.dp))
            row()
        }
    }
}

/** A group's label, in capitals in the accent. */
@Composable
fun GroupLabel(label: String, modifier: Modifier = Modifier) {
    Text(label.uppercase(), style = FrostType.overline, color = LocalFrost.current.accent, modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

/**
 * One piece of a group card that's split over the rows of a long list (so the list can stay
 * lazy): the [first] piece has the card's top corners, the [last] its bottom ones, and every
 * piece after the first a hairline above it when [divider].
 */
fun Modifier.frostSegment(frost: FrostColors, first: Boolean, last: Boolean, divider: Boolean = !first): Modifier = drawBehind {
    val r = 28.dp.toPx()
    val line = 1.dp.toPx()
    val reach = r + line * 2
    val top = if (first) 0f else -reach
    val bottom = if (last) size.height else size.height + reach
    clipRect {
        drawRoundRect(frost.fill, topLeft = Offset(0f, top), size = Size(size.width, bottom - top), cornerRadius = CornerRadius(r))
        drawRoundRect(
            frost.border,
            topLeft = Offset(line / 2, top + line / 2),
            size = Size(size.width - line, bottom - top - line),
            cornerRadius = CornerRadius(r - line / 2),
            style = Stroke(line),
        )
        if (divider) {
            val inset = 18.dp.toPx()
            drawRect(frost.divider, topLeft = Offset(inset, 0f), size = Size(size.width - 2 * inset, line))
        }
    }
}

/**
 * A settings row: 38 dp icon tile (or [leading]), title, its current value, and a chevron, a
 * switch or a small button.
 */
@Composable
fun FrostRow(
    icon: String?,
    title: String,
    caption: String? = null,
    trailing: Trailing = Trailing.None,
    onClick: (() -> Unit)? = null,
    error: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
) {
    val frost = LocalFrost.current
    val clickModifier = when {
        trailing is Trailing.Toggle -> Modifier.toggleable(trailing.checked, enabled = enabled, role = Role.Switch, onValueChange = trailing.onChange)
        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
        else -> Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(FrostShapes.inner)
            .then(clickModifier)
            .padding(horizontal = 10.dp, vertical = 11.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        when {
            leading != null -> leading()
            icon != null -> IconTile(38.dp, if (error) frost.errorTile else frost.tile, FrostShapes.icon) {
                FrostIcon(icon, if (error) frost.errorInk else frost.textSecondary, size = 18.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = FrostType.body, color = if (error) frost.errorText else frost.textPrimary)
            if (caption != null) Text(caption, style = FrostType.small, color = frost.textTertiary)
        }
        end?.invoke()
        when (trailing) {
            Trailing.None -> Unit
            Trailing.Chevron -> FrostIcon(FrostIcons.ChevronRight, frost.textMuted, size = 16.dp)
            is Trailing.Toggle -> FrostToggle(trailing.checked, null)
            is Trailing.Action -> FrostIconButton(trailing.icon, trailing.description, trailing.onClick)
        }
    }
}

/** A 40 dp round tap target with an 18 dp glyph, for actions inside rows. */
@Composable
fun FrostIconButton(icon: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else 0.4f),
    ) { FrostIcon(icon, LocalFrost.current.textTertiary, size = 18.dp) }
}

/** The menu's row for a section: its tinted 40 dp tile, name and what's in it; tap opens it. */
@Composable
fun FrostMenuRow(icon: String, title: String, caption: String, tone: Tone, onClick: () -> Unit) {
    val frost = LocalFrost.current
    val (fill, ink) = frost.tones.getValue(tone)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(FrostShapes.inner)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        IconTile(40.dp, fill, FrostShapes.menuTile) { FrostIcon(icon, ink, size = 19.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = FrostType.bodyStrong, color = frost.textPrimary)
            Text(caption, style = FrostType.small, color = frost.textTertiary)
        }
        FrostIcon(FrostIcons.ChevronRight, frost.textMuted, size = 16.dp)
    }
}

/** The launcher's switch: 40 × 22 dp track, 16 dp thumb 3 dp in. [onChange] null: the row toggles it. */
@Composable
fun FrostToggle(checked: Boolean, onChange: ((Boolean) -> Unit)?) {
    val frost = LocalFrost.current
    val track by animateColorAsState(if (checked) frost.accent else frost.toggleOff, label = "track")
    val thumb by animateColorAsState(if (checked) frost.onAccent else frost.toggleThumbOff, label = "thumb")
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, label = "x")
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 22.dp)
            .background(track, CircleShape)
            .then(if (onChange != null) Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange) else Modifier),
    ) {
        Box(Modifier.offset(x = x, y = 3.dp).size(16.dp).background(thumb, CircleShape))
    }
}

/** A slider row: title with its live value, then the slider between two end glyphs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostSliderRow(
    icon: String,
    title: String,
    value: String,
    position: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    startIcon: String = FrostIcons.Small,
    endIcon: String = FrostIcons.Large,
) {
    val frost = LocalFrost.current
    val thumbColor = if (frost.dark) frost.textPrimary else frost.accent
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(38.dp, frost.tile, FrostShapes.icon) { FrostIcon(icon, frost.textSecondary, size = 18.dp) }
            Text(title, style = FrostType.body, color = frost.textPrimary, modifier = Modifier.weight(1f))
            Text(value, style = FrostType.small, color = frost.textTertiary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            FrostIcon(startIcon, frost.textMuted, size = 16.dp)
            Slider(
                value = position,
                onValueChange = onChange,
                onValueChangeFinished = onChangeFinished,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(thumbColor = thumbColor, activeTrackColor = frost.accent, inactiveTrackColor = frost.sliderTrack),
                thumb = { Box(Modifier.size(18.dp).shadow(2.dp, CircleShape).background(thumbColor, CircleShape)) },
                track = { state ->
                    val span = state.valueRange.endInclusive - state.valueRange.start
                    val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        val corner = CornerRadius(size.height / 2)
                        drawRoundRect(frost.sliderTrack, cornerRadius = corner)
                        drawRoundRect(frost.accent, size = Size(size.width * fraction, size.height), cornerRadius = corner)
                    }
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = title },
            )
            FrostIcon(endIcon, frost.textMuted, size = 16.dp)
        }
    }
}

/** One choice of several: the selected one in the launcher's selected state. */
@Composable
fun FrostChoiceRow(title: String, caption: String?, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val frost = LocalFrost.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(FrostShapes.inner)
            .then(if (selected) Modifier.background(frost.selected).border(1.dp, frost.selectedBorder, FrostShapes.inner) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = FrostType.body, color = frost.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = FrostType.small, color = frost.textTertiary)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).background(frost.accent, CircleShape))
        }
    }
}

/**
 * A page of choices (in place of a pop-up list): the chosen one highlighted; picking one goes
 * back. Back without picking leaves it as it was.
 */
@Composable
fun <T> FrostChoicePage(
    overline: String,
    title: String,
    label: String,
    options: List<Option<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    FrostPage(title = title, overline = overline, onBack = onBack) {
        item(key = "choices") {
            FrostGroup(label, listOf {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { option ->
                        FrostChoiceRow(
                            title = option.label,
                            caption = option.description,
                            selected = option.value == selected,
                            enabled = option.enabled,
                            // Back first: a choice can open something else (a PIN to set before a lock).
                            onClick = { onBack(); onSelect(option.value) },
                        )
                    }
                }
            })
        }
    }
}

/** A plain line of help text, inside a group or on its own between groups. */
@Composable
fun FrostNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = FrostType.small, color = LocalFrost.current.textTertiary, modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp))
}

/** A filter chip: the chosen one in the accent, the others frosted. */
@Composable
fun FrostChip(text: String, selected: Boolean, onClick: () -> Unit, icon: String? = null, modifier: Modifier = Modifier) {
    val frost = LocalFrost.current
    val ink = if (selected) frost.onAccent else frost.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .height(36.dp)
            .then(if (selected) Modifier.clip(CircleShape).background(frost.accent) else Modifier.frost(frost, CircleShape, shadow = false))
            .clickable(onClick = onClick)
            .padding(start = if (icon != null) 12.dp else 16.dp, end = 16.dp),
    ) {
        if (icon != null) FrostIcon(icon, ink, size = 16.dp)
        Text(text, style = FrostType.label, color = ink, maxLines = 1)
    }
}

/** A floating button over the page's bottom corner: [primary] in the accent, otherwise frosted. */
@Composable
fun FrostPill(text: String, icon: String, onClick: () -> Unit, primary: Boolean = true, modifier: Modifier = Modifier) {
    val frost = LocalFrost.current
    val ink = if (primary) frost.onAccent else frost.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(52.dp)
            .then(
                if (primary) Modifier.shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Transparent).clip(CircleShape).background(frost.accent)
                else Modifier.frost(frost, CircleShape),
            )
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 22.dp),
    ) {
        FrostIcon(icon, ink, size = 18.dp)
        Text(text, style = FrostType.label, color = ink, maxLines = 1)
    }
}

/** A small text button for a page's header, such as "Select all". */
@Composable
fun FrostTextButton(text: String, onClick: () -> Unit) {
    val frost = LocalFrost.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(44.dp)
            .frost(frost, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) { Text(text, style = FrostType.label, color = frost.textPrimary, maxLines = 1) }
}

/** A round tick for picking several rows. */
@Composable
fun FrostCheck(checked: Boolean) {
    val frost = LocalFrost.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .then(if (checked) Modifier.background(frost.accent, CircleShape) else Modifier.border(2.dp, frost.textMuted, CircleShape)),
    ) {
        if (checked) FrostIcon(FrostIcons.Check, frost.onAccent, size = 16.dp, strokeWidth = 2.6f)
    }
}

/** A frosted search field. */
@Composable
fun FrostSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val frost = LocalFrost.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = FrostType.input.copy(color = frost.textPrimary),
        cursorBrush = SolidColor(frost.accent),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).frost(frost, CircleShape).padding(horizontal = 16.dp),
            ) {
                FrostIcon(FrostIcons.Search, frost.textMuted, size = 18.dp)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = FrostType.input, color = frost.textMuted, maxLines = 1)
                    field()
                }
                if (value.isNotEmpty()) FrostIconButton(FrostIcons.Close, "Clear", { onValueChange("") }, Modifier.size(32.dp))
            }
        },
    )
}
