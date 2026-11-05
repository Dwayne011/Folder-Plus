package com.widgetsplus.ui.open

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgetsplus.R
import com.widgetsplus.apps.LauncherApp
import com.widgetsplus.core.model.AppRef
import com.widgetsplus.ui.common.AppIconImage

/** Height of the open folder's add-apps list (the picker the plus pill opens). */
private val PICKER_LIST_HEIGHT = 380.dp

/**
 * The small plus pill in the bottom-right corner of the open folder's apps panel (approved
 * mockup): it opens [AddToTabPicker] for the tab on show. [tabTitle] names it for TalkBack.
 */
@Composable
internal fun AddPill(tabTitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.add_apps_to, tabTitle)
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.agenda_badge))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = colorResource(R.color.agenda_on_badge), modifier = Modifier.size(20.dp))
    }
}

/**
 * The open folder turned into a picker (approved mockup): "Add to <tab>", a search box, and the
 * phone's apps that aren't in this folder yet, each with a tick. "Add" hands the ticked apps to
 * [onAdd]; back (the arrow or the system back) returns to the folder without adding anything.
 */
@Composable
internal fun AddToTabPicker(
    tabTitle: String,
    candidates: List<LauncherApp>,
    onAdd: (List<AppRef>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val onPlate = colorResource(R.color.agenda_on_plate)
    val card = colorResource(R.color.agenda_card)
    val onCard = colorResource(R.color.agenda_on_card)
    val onCardVar = colorResource(R.color.agenda_on_card_var)
    val badge = colorResource(R.color.agenda_badge)
    val onBadge = colorResource(R.color.agenda_on_badge)

    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val shown = candidates.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) || it.ref.packageName.contains(query, ignoreCase = true)
    }

    Column {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = onPlate)
            }
            Text(
                stringResource(R.string.add_to_tab, tabTitle),
                color = onPlate,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Search: a pill on the card colour.
        Row(
            Modifier
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(card)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = onCardVar, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(R.string.search_apps), color = onCardVar, fontSize = 14.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = onCard, fontSize = 14.sp),
                    cursorBrush = SolidColor(onCard),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(PICKER_LIST_HEIGHT)
                .clip(RoundedCornerShape(20.dp))
                .background(colorResource(R.color.agenda_group)),
        ) {
            if (shown.isEmpty()) {
                Text(
                    stringResource(if (candidates.isEmpty()) R.string.add_none_left else R.string.add_no_match),
                    color = onCardVar,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(shown, key = { it.ref.flatten() }) { app ->
                    val key = app.ref.flatten()
                    val ticked = key in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(card)
                            .clickable { selected = if (ticked) selected - key else selected + key }
                            .semantics { role = Role.Checkbox }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIconImage(app = app.ref, size = 40.dp)
                        Text(
                            app.label,
                            color = onCard,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(if (ticked) Modifier.background(badge) else Modifier.border(2.dp, onCardVar, CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ticked) Icon(Icons.Default.Check, contentDescription = null, tint = onBadge, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (selected.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(badge)
                        .clickable {
                            val chosen = selected.toSet()
                            onAdd(candidates.filter { it.ref.flatten() in chosen }.map { it.ref })
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = onBadge, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.add_selected, selected.size), color = onBadge, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
