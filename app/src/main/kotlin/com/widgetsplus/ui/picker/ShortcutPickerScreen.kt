package com.widgetsplus.ui.picker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.R
import com.widgetsplus.apps.SavedShortcut
import com.widgetsplus.apps.ShortcutStore
import com.widgetsplus.core.model.AppRef
import com.widgetsplus.graph
import com.widgetsplus.ui.editor.FolderEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** One kind of shortcut an app offers through Android's shortcut picker (ACTION_CREATE_SHORTCUT). */
private data class ShortcutSource(val component: ComponentName, val label: String, val appLabel: String, val icon: Bitmap?)

/**
 * Shortcuts, kept apart from the app list because there can be many: every installed app's
 * shortcut types, grouped by app and searchable. Picking one opens that app's own shortcut
 * screen; what it hands back is added to the folder like an app. Some apps only hand shortcuts
 * to the phone's home-screen app; those are refused with an explanation instead of adding a
 * shortcut that wouldn't open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutPickerScreen(folderId: Long, categoryId: Long?, onDone: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(graph, folderId) }

    var sources by remember { mutableStateOf<List<ShortcutSource>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // The source being asked; survives the round trip to the other app's screen.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { sources = withContext(Dispatchers.IO) { loadSources(context) } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val source = pending?.let(ComponentName::unflattenFromString)
        pending = null
        if (source == null || result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val outcome = accept(context, source, result)
        if (outcome is Outcome.Added) {
            vm.addApps(listOf(AppRef.shortcut(source.packageName, outcome.shortcut.id)), categoryId)
            Toast.makeText(context, context.getString(R.string.shortcut_added, outcome.shortcut.label), Toast.LENGTH_SHORT).show()
            onDone()
        } else if (outcome is Outcome.Refused) {
            Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_shortcut)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_shortcuts)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val all = sources
            when {
                all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val shown = all.filter {
                        query.isBlank() || it.label.contains(query, ignoreCase = true) || it.appLabel.contains(query, ignoreCase = true)
                    }
                    if (shown.isEmpty()) {
                        Text(
                            stringResource(if (all.isEmpty()) R.string.no_shortcuts else R.string.no_matches),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    stringResource(R.string.shortcuts_intro),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            shown.groupBy { it.component.packageName }.forEach { (pkg, list) ->
                                item(key = "h:$pkg") {
                                    Text(
                                        list.first().appLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                                    )
                                }
                                items(list, key = { it.component.flattenToString() }) { source ->
                                    ListItem(
                                        headlineContent = { Text(source.label) },
                                        leadingContent = {
                                            val icon = source.icon
                                            if (icon != null) Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
                                            else Box(Modifier.size(40.dp))
                                        },
                                        modifier = Modifier.clickable {
                                            pending = source.component.flattenToString()
                                            try {
                                                launcher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(source.component))
                                            } catch (e: ActivityNotFoundException) {
                                                pending = null
                                                Toast.makeText(context, context.getString(R.string.shortcut_unavailable, source.appLabel), Toast.LENGTH_LONG).show()
                                            } catch (e: SecurityException) {
                                                pending = null
                                                Toast.makeText(context, context.getString(R.string.shortcut_unavailable, source.appLabel), Toast.LENGTH_LONG).show()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Every shortcut type other apps offer, sorted by app then shortcut name. */
private fun loadSources(context: Context): List<ShortcutSource> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
    val size = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    return resolved
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.exported }
        .map { info ->
            val ai = info.activityInfo
            ShortcutSource(
                component = ComponentName(ai.packageName, ai.name),
                label = info.loadLabel(pm).toString(),
                appLabel = ai.applicationInfo.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm).toBitmap(size, size, Bitmap.Config.ARGB_8888) }.getOrNull(),
            )
        }
        .sortedWith(compareBy<ShortcutSource, String>(String.CASE_INSENSITIVE_ORDER) { it.appLabel }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

private sealed interface Outcome {
    data class Added(val shortcut: SavedShortcut) : Outcome
    data class Refused(val message: String) : Outcome
}

/**
 * Reads what the app's shortcut screen handed back. The older form carries the shortcut's intent,
 * name and icon, which a folder can keep and open. Apps that only send the newer pin request
 * (meant for the home-screen app alone) are refused, as are shortcuts this app isn't allowed to
 * open, so nothing is added that wouldn't work.
 */
@Suppress("DEPRECATION")
private fun accept(context: Context, source: ComponentName, result: ActivityResult): Outcome {
    val pm = context.packageManager
    val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(source.packageName, 0)).toString() }.getOrDefault(source.packageName)
    val data = result.data ?: return Outcome.Refused(context.getString(R.string.shortcut_nothing_returned, appLabel))
    val target: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
    } else {
        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
    }
    if (target == null) {
        val launcherOnly = data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")
        return Outcome.Refused(
            context.getString(if (launcherOnly) R.string.shortcut_launcher_only else R.string.shortcut_nothing_returned, appLabel),
        )
    }
    val safe = ShortcutStore.sanitize(context, Intent(target)) ?: return Outcome.Refused(context.getString(R.string.shortcut_cannot_open, appLabel))

    // Only add shortcuts this app is allowed to open (the home-screen app may have more rights).
    val activity = runCatching { pm.resolveActivity(safe, 0)?.activityInfo }.getOrNull()
        ?: return Outcome.Refused(context.getString(R.string.shortcut_cannot_open, appLabel))
    val permission = activity.permission
    if (!activity.exported ||
        (permission != null && ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
    ) {
        return Outcome.Refused(context.getString(R.string.shortcut_launcher_only, appLabel))
    }

    val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.takeIf { it.isNotBlank() }
        ?: runCatching { pm.getActivityInfo(source, 0).loadLabel(pm).toString() }.getOrDefault(appLabel)
    val bitmap: Bitmap? = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
    } else {
        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
    }) ?: iconFromResource(context, data)
    val shortcut = SavedShortcut(
        id = UUID.randomUUID().toString(),
        packageName = source.packageName,
        label = name,
        intentUri = safe.toUri(Intent.URI_INTENT_SCHEME),
        iconPng = bitmap?.let(ShortcutStore::png),
    )
    ShortcutStore.put(context, shortcut)
    return Outcome.Added(shortcut)
}

// The resource is named by the other app, so it can only be looked up by name.
@SuppressLint("DiscouragedApi")
@Suppress("DEPRECATION")
private fun iconFromResource(context: Context, data: Intent): Bitmap? {
    val res: Intent.ShortcutIconResource = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
    } else {
        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
    }) ?: return null
    return runCatching {
        val resources = context.packageManager.getResourcesForApplication(res.packageName)
        val id = resources.getIdentifier(res.resourceName, null, null)
        ResourcesCompat.getDrawable(resources, id, null)?.toBitmap(192, 192, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
