package com.folderplus.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.folderplus.graph
import com.folderplus.ui.editor.AppsScreen
import com.folderplus.ui.editor.FolderEditorScreen
import com.folderplus.ui.folders.FolderListScreen
import com.folderplus.ui.picker.AppPickerScreen
import com.folderplus.ui.theme.FolderPlusTheme
import com.folderplus.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startFolderId = intent?.getLongExtra(EXTRA_FOLDER_ID, -1L) ?: -1L
        setContent {
            FolderPlusTheme {
                FolderPlusNavHost(startFolderId = startFolderId)
            }
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

        val Saver = listSaver<Screen, Long>(
            save = { listOf(it.kind.toLong(), it.folderId, it.categoryId) },
            restore = { Screen(it[0].toInt(), it[1], it[2]) },
        )
    }
}

@Composable
fun FolderPlusNavHost(startFolderId: Long) {
    var screen by rememberSaveable(stateSaver = Screen.Saver) {
        mutableStateOf(if (startFolderId > 0) Screen(Screen.EDITOR, startFolderId) else Screen(Screen.FOLDERS))
    }
    val folderId = screen.folderId

    BackHandler(enabled = screen.kind != Screen.FOLDERS) {
        screen = when (screen.kind) {
            Screen.EDITOR -> Screen(Screen.FOLDERS)
            Screen.APPS -> Screen(Screen.EDITOR, folderId)
            Screen.PICKER -> Screen(Screen.APPS, folderId)
            else -> Screen(Screen.FOLDERS)
        }
    }

    when (screen.kind) {
        Screen.FOLDERS -> FolderListScreen(onOpenFolder = { screen = Screen(Screen.EDITOR, it) })
        Screen.EDITOR -> FolderEditorScreen(
            folderId = folderId,
            onBack = { screen = Screen(Screen.FOLDERS) },
            onManageApps = { screen = Screen(Screen.APPS, folderId) },
        )
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
