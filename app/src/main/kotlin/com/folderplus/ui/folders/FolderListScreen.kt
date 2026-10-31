package com.folderplus.ui.folders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.folderplus.ui.common.SectionTitle
import com.folderplus.ui.common.SettingsGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.folderplus.agenda.AgendaSetupActivity
import com.folderplus.core.model.Folder
import com.folderplus.data.backup.BackupCodec
import com.folderplus.graph
import com.folderplus.ui.common.ConfirmDialog
import com.folderplus.ui.common.FolderIconImage
import com.folderplus.ui.common.TextInputDialog
import com.folderplus.ui.common.displayName
import com.folderplus.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FolderRow(val folder: Folder, val appCount: Int)

class FolderListViewModel(private val graph: Graph) : ViewModel() {
    val rows: StateFlow<List<FolderRow>> =
        combine(graph.repository.observeFolders(), graph.repository.observeItemCounts()) { folders, counts ->
            folders.map { FolderRow(it, counts[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(graph.repository.createFolder(name)) }
    }

    /** Writes a JSON backup of everything to [uri] (chosen by the user via the system picker). */
    fun exportTo(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupCodec.encode(graph.repository.exportAll())
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: error("No output stream")
                }.isSuccess
            }
            Toast.makeText(appContext, if (ok) R.string.backup_exported else R.string.backup_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Replaces all folders with the backup at [uri]. */
    fun importFrom(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val text = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("No input stream")
                    val data = BackupCodec.decode(text)
                    graph.repository.replaceAll(data)
                    data.size
                }.getOrNull()
            }
            if (count == null) {
                Toast.makeText(appContext, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(appContext, appContext.getString(R.string.backup_imported, count), Toast.LENGTH_SHORT).show()
                WidgetUpdater.updateAll(appContext)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(onOpenFolder: (Long) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm: FolderListViewModel = viewModel(key = "folders") { FolderListViewModel(graph) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupCodec.MIME_TYPE)) { uri ->
        if (uri != null) vm.exportTo(context, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.agenda_menu)) },
                            onClick = {
                                menuOpen = false
                                context.startActivity(Intent(context, AgendaSetupActivity::class.java))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_export)) },
                            onClick = {
                                menuOpen = false
                                val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                                exportLauncher.launch("widgets-plus-backup-$stamp.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.backup_import)) },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf(BackupCodec.MIME_TYPE, "text/plain", "*/*"))
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_folder)) },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.folders_empty) + "\n\n" + stringResource(R.string.backup_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { SectionTitle(stringResource(R.string.folders_title)) }
                item {
                    SettingsGroup(rows = rows.map<FolderRow, @Composable () -> Unit> { row ->
                        {
                            ListItem(
                                headlineContent = { Text(row.folder.displayName()) },
                                supportingContent = { Text(stringResource(R.string.apps_count, row.appCount)) },
                                leadingContent = { FolderIconImage(folder = row.folder, size = 44.dp) },
                                trailingContent = if (row.folder.lockMode.isLocked) {
                                    { Icon(Icons.Default.Lock, contentDescription = null) }
                                } else {
                                    null
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(row.folder.id) },
                            )
                        }
                    })
                }
                item {
                    Text(
                        stringResource(R.string.backup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
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

    val importUri = pendingImport
    if (importUri != null) {
        ConfirmDialog(
            title = stringResource(R.string.backup_import_title),
            text = stringResource(R.string.backup_import_confirm),
            confirmText = stringResource(R.string.backup_import_action),
            onConfirm = { vm.importFrom(context, importUri) },
            onDismiss = { pendingImport = null },
        )
    }
}
