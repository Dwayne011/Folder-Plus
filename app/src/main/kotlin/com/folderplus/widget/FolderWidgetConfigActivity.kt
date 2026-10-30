package com.folderplus.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.folderplus.R
import com.folderplus.core.model.Folder
import com.folderplus.graph
import com.folderplus.ui.MainActivity
import com.folderplus.ui.common.FolderIconImage
import com.folderplus.ui.theme.FolderPlusTheme
import kotlinx.coroutines.launch

/** Shown by the launcher after a widget is dropped: picks which folder it shows. */
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
        setContent {
            FolderPlusTheme {
                val folders by remember { graph.repository.observeFolders() }.collectAsStateWithLifecycle(initialValue = emptyList())
                ConfigScreen(
                    folders = folders,
                    onPick = ::bind,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                )
            }
        }
    }

    private fun bind(folder: Folder) {
        val id = appWidgetId
        lifecycleScope.launch {
            graph.repository.bindWidget(id, folder.id)
            WidgetRenderer.render(this@FolderWidgetConfigActivity, AppWidgetManager.getInstance(this@FolderWidgetConfigActivity), id)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(folders: List<Folder>, onPick: (Folder) -> Unit, onOpenApp: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.config_title)) }) }) { padding ->
        if (folders.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.config_no_folders), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onOpenApp, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.open_app))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(folders, key = { it.id }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        supportingContent = { Text(folder.widgetSize.label) },
                        leadingContent = { FolderIconImage(folder = folder, size = 40.dp) },
                        modifier = Modifier.fillMaxWidth().clickable { onPick(folder) },
                    )
                }
            }
        }
    }
}
