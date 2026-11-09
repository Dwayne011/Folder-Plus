package com.widgetsplus.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.widgetsplus.R
import com.widgetsplus.core.model.Folder
import com.widgetsplus.graph
import com.widgetsplus.ui.MainActivity
import com.widgetsplus.ui.common.FolderIconImage
import com.widgetsplus.ui.common.TextInputDialog
import com.widgetsplus.ui.common.displayName
import com.widgetsplus.ui.frost.FrostGroup
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostNote
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostRow
import com.widgetsplus.ui.frost.FrostTheme
import com.widgetsplus.ui.frost.Trailing
import kotlinx.coroutines.launch

/**
 * Shown by the launcher after a widget is dropped: picks which folder it shows,
 * or creates a new folder and links it to this widget on the spot. Also the target
 * of the launcher's long-press "settings" button, which opens the bound folder.
 */
class FolderWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Long-press > settings on a widget that already shows a folder: go straight to
        // that folder's settings instead of asking which folder to show again.
        lifecycleScope.launch {
            val bound = graph.repository.folderIdForWidget(appWidgetId)?.takeIf { graph.repository.getFolder(it) != null }
            if (bound != null) {
                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                startActivity(MainActivity.editorIntent(this@FolderWidgetConfigActivity, bound))
                finish()
            } else {
                showPicker()
            }
        }
    }

    private fun showPicker() {
        setContent {
            FrostTheme {
                val folders by remember { graph.repository.observeFolders() }.collectAsStateWithLifecycle(initialValue = emptyList())
                ConfigScreen(
                    folders = folders,
                    onPick = { bind(it.id, openEditor = false) },
                    onCreate = { name ->
                        lifecycleScope.launch {
                            val id = graph.repository.createFolder(name)
                            Toast.makeText(this@FolderWidgetConfigActivity, R.string.config_created_hint, Toast.LENGTH_LONG).show()
                            bind(id, openEditor = true)
                        }
                    },
                )
            }
        }
    }

    /** Links the widget to [folderId], redraws it and returns RESULT_OK to the launcher. */
    private fun bind(folderId: Long, openEditor: Boolean) {
        val id = appWidgetId
        lifecycleScope.launch {
            graph.repository.bindWidget(id, folderId)
            WidgetRenderer.render(this@FolderWidgetConfigActivity, AppWidgetManager.getInstance(this@FolderWidgetConfigActivity), id)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            if (openEditor) startActivity(MainActivity.editorIntent(this@FolderWidgetConfigActivity, folderId))
            finish()
        }
    }
}

/** Which folder the new widget shows: each folder, then a new one. */
@Composable
private fun ConfigScreen(folders: List<Folder>, onPick: (Folder) -> Unit, onCreate: (String) -> Unit) {
    var showCreate by rememberSaveable { mutableStateOf(false) }

    FrostPage(title = stringResource(R.string.config_title), overline = stringResource(R.string.app_name), onBack = null) {
        item(key = "folders") {
            val empty: List<@Composable () -> Unit> = if (folders.isEmpty()) listOf({ FrostNote(stringResource(R.string.config_no_folders)) }) else emptyList()
            FrostGroup(
                stringResource(R.string.folders_group),
                empty + folders.map<Folder, @Composable () -> Unit> { folder ->
                    {
                        FrostRow(
                            icon = null,
                            title = folder.displayName(),
                            caption = folder.widgetSize.label,
                            trailing = Trailing.Chevron,
                            onClick = { onPick(folder) },
                            leading = { FolderIconImage(folder = folder, size = 38.dp, modifier = Modifier.clip(RoundedCornerShape(12.dp))) },
                        )
                    }
                } + listOf<@Composable () -> Unit>({
                    FrostRow(FrostIcons.Plus, stringResource(R.string.new_folder), stringResource(R.string.config_or_create), onClick = { showCreate = true })
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
            onConfirm = onCreate,
            onDismiss = { showCreate = false },
        )
    }
}
