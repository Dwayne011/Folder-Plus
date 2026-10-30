package com.folderplus.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folderplus.R
import com.folderplus.graph
import com.folderplus.ui.common.AppIconImage
import com.folderplus.ui.editor.FolderEditorViewModel

/** Multi-select list of installed apps not yet in the folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(folderId: Long, categoryId: Long?, onDone: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(graph, folderId) }
    val installed by graph.installedApps.apps.collectAsStateWithLifecycle()
    val existing by vm.items.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }

    val inFolder = existing.map { it.app.flatten() }.toSet()
    val candidates = installed
        .filter { it.ref.flatten() !in inFolder }
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.ref.packageName.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_apps)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            if (selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val refs = installed.filter { it.ref.flatten() in selected }.map { it.ref }
                        vm.addApps(refs, categoryId)
                        onDone()
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_selected, selected.size)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_apps)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(candidates, key = { it.ref.flatten() }) { app ->
                    val key = app.ref.flatten()
                    val checked = key in selected
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.ref.packageName) },
                        leadingContent = { AppIconImage(app = app.ref, size = 40.dp) },
                        trailingContent = {
                            Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + key else selected - key })
                        },
                        modifier = Modifier.fillMaxWidth().clickable { selected = if (checked) selected - key else selected + key },
                    )
                }
            }
        }
    }
}
