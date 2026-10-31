package com.widgetsplus.ui.open

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.widgetsplus.R
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.HostedWidget
import com.widgetsplus.ui.common.displayName
import com.widgetsplus.widget.HostedWidgets

/** Scrim + centred card showing the folder's hosted app widget, live. */
@Composable
fun WidgetPopup(
    folder: Folder,
    hosted: HostedWidget,
    hostedWidgets: HostedWidgets,
    onShowFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    frosting: Int = Folder.DEFAULT_FROSTING,
) {
    val info = remember(hosted) { hostedWidgets.info(hosted.appWidgetId) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha(frosting)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = popupContainerColor(frosting),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (info != null) hostedWidgets.label(info) else folder.displayName(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onShowFolder) { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.show_folder)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
                }
                if (info == null) {
                    Text(
                        stringResource(R.string.hosted_widget_unavailable),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                } else {
                    val heightDp = remember(info) { hostedWidgets.popupHeightDp(info) }
                    AndroidView(
                        factory = { ctx -> hostedWidgets.createView(ctx, hosted.appWidgetId, info) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightDp.dp)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
