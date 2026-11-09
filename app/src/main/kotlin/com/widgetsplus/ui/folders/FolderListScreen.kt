package com.widgetsplus.ui.folders

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.Graph
import com.widgetsplus.R
import com.widgetsplus.apps.ShortcutStore
import com.widgetsplus.core.model.Folder
import com.widgetsplus.data.backup.BackupCodec
import com.widgetsplus.graph
import com.widgetsplus.ui.common.FolderIconImage
import com.widgetsplus.ui.common.TextInputDialog
import com.widgetsplus.ui.common.displayName
import com.widgetsplus.ui.frost.FrostGroup
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostNote
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostRow
import com.widgetsplus.ui.frost.Trailing
import com.widgetsplus.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FolderRow(val folder: Folder, val appCount: Int, val categoryCount: Int = 0)

class FolderListViewModel(private val graph: Graph) : ViewModel() {
    val rows: StateFlow<List<FolderRow>> =
        combine(graph.repository.observeFolders(), graph.repository.observeItemCounts(), graph.repository.observeCategoryCounts()) { folders, counts, categories ->
            folders.map { FolderRow(it, counts[it.id] ?: 0, categories[it.id] ?: 0) }
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
                    val used = graph.repository.usedShortcutIds()
                    val json = BackupCodec.encode(graph.repository.exportAll(), ShortcutStore.all(appContext).filter { it.id in used })
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
                    ShortcutStore.putAll(appContext, BackupCodec.decodeShortcuts(text))
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

/**
 * Every folder with its icon, what's in it and whether it's locked; "New folder" at the end
 * (once the round + button). Backup is on its own page from the menu.
 */
@Composable
fun FolderListScreen(onBack: () -> Unit, onOpenFolder: (Long) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm: FolderListViewModel = viewModel(key = "folders") { FolderListViewModel(graph) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    FrostPage(title = stringResource(R.string.folders_title), overline = stringResource(R.string.settings_title), onBack = onBack) {
        item(key = "folders") {
            val folderRows = rows.map<FolderRow, @Composable () -> Unit> { row ->
                {
                    val parts = listOfNotNull(
                        pluralStringResource(R.plurals.category_app_count, row.appCount, row.appCount),
                        if (row.categoryCount > 0) pluralStringResource(R.plurals.category_count, row.categoryCount, row.categoryCount) else null,
                        if (row.folder.lockMode.isLocked) stringResource(R.string.folder_locked) else null,
                    )
                    FrostRow(
                        icon = null,
                        title = row.folder.displayName(),
                        caption = parts.joinToString(" · "),
                        trailing = Trailing.Chevron,
                        onClick = { onOpenFolder(row.folder.id) },
                        leading = { FolderIconImage(folder = row.folder, size = 38.dp, modifier = Modifier.clip(RoundedCornerShape(12.dp))) },
                    )
                }
            }
            val empty: List<@Composable () -> Unit> = if (rows.isEmpty()) listOf({ FrostNote(stringResource(R.string.folders_empty)) }) else emptyList()
            FrostGroup(
                stringResource(R.string.folders_group),
                empty + folderRows + listOf<@Composable () -> Unit>({
                    FrostRow(FrostIcons.Plus, stringResource(R.string.new_folder), stringResource(R.string.new_folder_hint), onClick = { showCreate = true })
                }),
            )
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
