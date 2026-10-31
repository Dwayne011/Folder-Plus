package com.widgetsplus.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.widgetsplus.R
import com.widgetsplus.graph
import com.widgetsplus.ui.theme.WidgetsPlusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists every installed app widget; picking one allocates an id in our
 * AppWidgetHost, binds it (asking the user for permission the first time),
 * runs the widget's own configuration screen if it has one, and returns the
 * provider and id to the caller.
 */
class HostedWidgetPickerActivity : ComponentActivity() {

    private val hosted get() = graph.hostedWidgets
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProvider: ComponentName? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)
        setContent {
            WidgetsPlusTheme {
                PickerScreen(onPick = ::pick, onBack = ::finish)
            }
        }
    }

    private fun pick(info: AppWidgetProviderInfo) {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) return // a pick is already in flight
        pendingId = hosted.allocateId()
        pendingProvider = info.provider
        if (hosted.bindIfAllowed(pendingId, info.provider)) {
            configureOrFinish(info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_BIND)
        }
    }

    private fun configureOrFinish(info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            hosted.host.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIGURE, null)
        } else {
            finishWithResult()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> {
                val info = hosted.info(pendingId)
                if (resultCode == Activity.RESULT_OK && info != null) {
                    configureOrFinish(info)
                } else {
                    Toast.makeText(this, R.string.widget_bind_denied, Toast.LENGTH_LONG).show()
                    cancelPending()
                }
            }
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finishWithResult() else cancelPending()
        }
    }

    private fun cancelPending() {
        hosted.deleteId(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
    }

    private fun finishWithResult() {
        val provider = pendingProvider ?: return cancelPending()
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_PROVIDER, provider.flattenToString())
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId),
        )
        finish()
    }

    companion object {
        const val EXTRA_PROVIDER = "provider"
        private const val REQ_BIND = 1
        private const val REQ_CONFIGURE = 2
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScreen(onPick: (AppWidgetProviderInfo) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val hosted = context.graph.hostedWidgets
    val groups = remember {
        hosted.installedProviders()
            .groupBy { hosted.appLabel(it) }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .mapValues { (_, list) -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { hosted.label(it) }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_widget)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            groups.forEach { (appLabel, widgets) ->
                item(key = "app-$appLabel") {
                    Text(
                        appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
                widgets.forEach { info ->
                    item(key = info.provider.flattenToString()) {
                        val density = context.resources.displayMetrics.density
                        ListItem(
                            headlineContent = { Text(hosted.label(info)) },
                            supportingContent = {
                                Text(stringResource(R.string.widget_size_label, (info.minWidth / density).toInt(), (info.minHeight / density).toInt()))
                            },
                            leadingContent = { WidgetPreview(info) },
                            modifier = Modifier.fillMaxWidth().clickable { onPick(info) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo) {
    val context = LocalContext.current
    var bitmap by remember(info.provider) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(info.provider) {
        bitmap = withContext(Dispatchers.IO) {
            val drawable = runCatching { info.loadPreviewImage(context, 0) }.getOrNull()
                ?: runCatching { info.loadIcon(context, 0) }.getOrNull()
            drawable?.let { d ->
                val maxPx = (64 * context.resources.displayMetrics.density).toInt()
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: maxPx
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: maxPx
                val scale = minOf(maxPx.toFloat() / w, maxPx.toFloat() / h, 1f)
                d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }
    }
    Box(Modifier.size(64.dp)) {
        bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp)) }
    }
}
