package com.folderplus.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.folderplus.Graph
import com.folderplus.R
import com.folderplus.core.model.Folder
import com.folderplus.graph
import com.folderplus.ui.common.FolderIconImage
import com.folderplus.ui.common.TextInputDialog
import com.folderplus.ui.common.displayName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderRow(val folder: Folder, val appCount: Int)

class FolderListViewModel(private val graph: Graph) : ViewModel() {
    val rows: StateFlow<List<FolderRow>> =
        combine(graph.repository.observeFolders(), graph.repository.observeItemCounts()) { folders, counts ->
            folders.map { FolderRow(it, counts[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(graph.repository.createFolder(name)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(onOpenFolder: (Long) -> Unit) {
    val graph = LocalContext.current.graph
    val vm: FolderListViewModel = viewModel(key = "folders") { FolderListViewModel(graph) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.folders_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_folder))
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.folders_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(rows, key = { it.folder.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.folder.displayName()) },
                        supportingContent = { Text(stringResource(R.string.apps_count, row.appCount)) },
                        leadingContent = { FolderIconImage(folder = row.folder, size = 44.dp) },
                        trailingContent = if (row.folder.lockMode.isLocked) {
                            { Icon(Icons.Default.Lock, contentDescription = null) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(row.folder.id) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            label = stringResource(R.string.folder_name),
            placeholder = stringResource(R.string.folder_name_hint),
            confirmText = stringResource(R.string.create),
            allowBlank = true,
            onConfirm = { name -> vm.create(name, onOpenFolder) },
            onDismiss = { showCreate = false },
        )
    }
}
