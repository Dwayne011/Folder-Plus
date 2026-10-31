package com.widgetsplus.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.widgetsplus.graph
import com.widgetsplus.ui.editor.AppearanceScreen
import com.widgetsplus.ui.editor.AppsScreen
import com.widgetsplus.ui.editor.FolderEditorScreen
import com.widgetsplus.ui.editor.GesturesScreen
import com.widgetsplus.ui.folders.FolderListScreen
import com.widgetsplus.ui.picker.AppPickerScreen
import com.widgetsplus.ui.theme.FolderPlusTheme
import com.widgetsplus.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Folder the latest intent asked us to edit. The activity is singleTop, so
     * requests from widget setup or the popup arrive via onNewIntent while we
     * are already showing something else; this state lets the UI follow them.
     */
    private var requestedFolderId by mutableStateOf(-1L)
    private var requestSerial by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedFolderId = intent?.getLongExtra(EXTRA_FOLDER_ID, -1L) ?: -1L
        setContent {
            FolderPlusTheme {
                FolderPlusNavHost(requestedFolderId = requestedFolderId, requestSerial = requestSerial)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getLongExtra(EXTRA_FOLDER_ID, -1L)
        if (id > 0) {
            requestedFolderId = id
            requestSerial++
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up installs/uninstalls and drop items for apps that are gone.
        lifecycleScope.launch {
            val apps = graph.installedApps.refresh()
            val removed = graph.repository.pruneMissingApps(apps.map { it.ref }.toSet())
            if (removed > 0) WidgetUpdater.updateAllAsync(this@MainActivity)
        }
    }

    companion object {
        const val EXTRA_FOLDER_ID = "folder_id"

        fun editorIntent(context: Context, folderId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/** Minimal in-app navigation; a dependency on navigation-compose isn't worth it for four screens. */
data class Screen(val kind: Int, val folderId: Long = -1L, val categoryId: Long = -1L) {
    companion object {
        const val FOLDERS = 0
        const val EDITOR = 1
        const val APPS = 2
        const val PICKER = 3
        const val GESTURES = 4
        const val APPEARANCE = 5

        val Saver = listSaver<Screen, Long>(
            save = { listOf(it.kind.toLong(), it.folderId, it.categoryId) },
            restore = { Screen(it[0].toInt(), it[1], it[2]) },
        )
    }
}

@Composable
fun FolderPlusNavHost(requestedFolderId: Long, requestSerial: Int) {
    var screen by rememberSaveable(stateSaver = Screen.Saver) {
        mutableStateOf(if (requestedFolderId > 0) Screen(Screen.EDITOR, requestedFolderId) else Screen(Screen.FOLDERS))
    }
    // Jump to the editor whenever a new external request arrives.
    LaunchedEffect(requestSerial) {
        if (requestSerial > 0 && requestedFolderId > 0) screen = Screen(Screen.EDITOR, requestedFolderId)
    }
    val folderId = screen.folderId

    BackHandler(enabled = screen.kind != Screen.FOLDERS) {
        screen = when (screen.kind) {
            Screen.EDITOR -> Screen(Screen.FOLDERS)
            Screen.APPS -> Screen(Screen.EDITOR, folderId)
            Screen.PICKER -> Screen(Screen.APPS, folderId)
            Screen.GESTURES, Screen.APPEARANCE -> Screen(Screen.EDITOR, folderId)
            else -> Screen(Screen.FOLDERS)
        }
    }

    when (screen.kind) {
        Screen.FOLDERS -> FolderListScreen(onOpenFolder = { screen = Screen(Screen.EDITOR, it) })
        Screen.EDITOR -> FolderEditorScreen(
            folderId = folderId,
            onBack = { screen = Screen(Screen.FOLDERS) },
            onManageApps = { screen = Screen(Screen.APPS, folderId) },
            onGestures = { screen = Screen(Screen.GESTURES, folderId) },
            onAppearance = { screen = Screen(Screen.APPEARANCE, folderId) },
        )
        Screen.GESTURES -> GesturesScreen(folderId = folderId, onBack = { screen = Screen(Screen.EDITOR, folderId) })
        Screen.APPEARANCE -> AppearanceScreen(folderId = folderId, onBack = { screen = Screen(Screen.EDITOR, folderId) })
        Screen.APPS -> AppsScreen(
            folderId = folderId,
            onBack = { screen = Screen(Screen.EDITOR, folderId) },
            onAddApps = { categoryId -> screen = Screen(Screen.PICKER, folderId, categoryId ?: -1L) },
        )
        Screen.PICKER -> AppPickerScreen(
            folderId = folderId,
            categoryId = screen.categoryId.takeIf { it > 0 },
            onDone = { screen = Screen(Screen.APPS, folderId) },
        )
    }
}
