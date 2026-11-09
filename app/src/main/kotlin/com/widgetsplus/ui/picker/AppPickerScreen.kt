package com.widgetsplus.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.R
import com.widgetsplus.graph
import com.widgetsplus.ui.common.AppIconImage
import com.widgetsplus.ui.common.displayName
import com.widgetsplus.ui.editor.FolderEditorViewModel
import com.widgetsplus.ui.frost.FrostCheck
import com.widgetsplus.ui.frost.FrostChip
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostPill
import com.widgetsplus.ui.frost.FrostSearchField
import com.widgetsplus.ui.frost.FrostShapes
import com.widgetsplus.ui.frost.FrostTextButton
import com.widgetsplus.ui.frost.FrostType
import com.widgetsplus.ui.frost.LocalFrost
import com.widgetsplus.ui.frost.frostSegment

/** Multi-select list of installed apps not yet in the folder. */
@Composable
fun AppPickerScreen(folderId: Long, categoryId: Long?, onDone: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val frost = LocalFrost.current
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(graph, folderId) }
    val installed by graph.installedApps.apps.collectAsStateWithLifecycle()
    val existing by vm.items.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    // Category the added apps go into; preset from the Apps screen's active chip.
    var target by rememberSaveable { mutableStateOf(categoryId ?: -1L) }

    val inFolder = existing.map { it.app.flatten() }.toSet()
    val candidates = installed
        .filter { it.ref.flatten() !in inFolder }
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.ref.packageName.contains(query, ignoreCase = true) }

    FrostPage(
        title = stringResource(R.string.add_apps),
        overline = folder?.displayName() ?: stringResource(R.string.apps_in_folder),
        onBack = onDone,
        actions = {
            if (candidates.isNotEmpty()) {
                FrostTextButton(stringResource(R.string.select_all)) { selected = selected + candidates.map { it.ref.flatten() } }
            }
        },
        spacing = 0.dp,
        bottomSpace = if (selected.isNotEmpty()) 80.dp else 0.dp,
        overlay = { bottom ->
            if (selected.isNotEmpty()) {
                FrostPill(
                    stringResource(R.string.add_selected, selected.size),
                    FrostIcons.Check,
                    {
                        val refs = installed.filter { it.ref.flatten() in selected }.map { it.ref }
                        vm.addApps(refs, target.takeIf { it > 0 })
                        onDone()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottom + 16.dp),
                )
            }
        },
    ) {
        item(key = "search") {
            Column {
                FrostSearchField(query, { query = it }, stringResource(R.string.search_apps))
                Spacer(Modifier.height(12.dp))
            }
        }
        if (categories.isNotEmpty()) {
            item(key = "target") {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.target_category).uppercase(), style = FrostType.overline, color = frost.textTertiary, modifier = Modifier.padding(start = 4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FrostChip(stringResource(R.string.category_none), selected = target <= 0, onClick = { target = -1L })
                        categories.forEach { category ->
                            FrostChip(category.name, selected = target == category.id, onClick = { target = category.id })
                        }
                    }
                }
            }
        }
        itemsIndexed(candidates, key = { _, app -> app.ref.flatten() }) { index, app ->
            val key = app.ref.flatten()
            val checked = key in selected
            val first = index == 0
            val last = index == candidates.lastIndex
            Box(Modifier.frostSegment(frost, first = first, last = last).padding(start = 8.dp, end = 8.dp, top = if (first) 6.dp else 0.dp, bottom = if (last) 6.dp else 0.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(FrostShapes.inner)
                        .clickable { selected = if (checked) selected - key else selected + key }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                ) {
                    AppIconImage(app = app.ref, size = 38.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(app.label, style = FrostType.body, color = frost.textPrimary)
                        Text(app.ref.packageName, style = FrostType.small, color = frost.textTertiary)
                    }
                    FrostCheck(checked)
                }
            }
        }
    }
}
