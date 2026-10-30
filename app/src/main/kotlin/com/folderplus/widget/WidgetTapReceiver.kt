package com.folderplus.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.folderplus.core.gesture.TapResolver
import com.folderplus.core.model.Folder
import com.folderplus.core.model.TapAction
import com.folderplus.graph
import com.folderplus.launch.AppLauncher
import com.folderplus.ui.open.FolderOpenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receives clicks from a widget's main area when the folder has both a
 * single-tap and a double-tap action, and tells them apart by timing.
 *
 * Home-screen widgets cannot report long presses or swipes (the launcher owns
 * those for moving/resizing), so this timing trick is the only way to get a
 * second gesture out of a widget.
 */
class WidgetTapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val repo = appContext.graph.repository
                val folder = repo.folderIdForWidget(appWidgetId)?.let { repo.getFolder(it) } ?: return@launch
                val doubleTapEnabled = folder.effectiveDoubleTapAction != TapAction.NONE
                val decision = synchronized(resolver) {
                    resolver.onTap(appWidgetId, SystemClock.elapsedRealtime(), doubleTapEnabled)
                }
                when (decision) {
                    TapResolver.Decision.SingleTap -> perform(appContext, folder, folder.effectiveTapAction)
                    TapResolver.Decision.DoubleTap -> perform(appContext, folder, folder.effectiveDoubleTapAction)
                    TapResolver.Decision.WaitForSecondTap -> {
                        delay(TapResolver.DEFAULT_WINDOW_MILLIS + 20)
                        val fire = synchronized(resolver) { resolver.resolvePending(appWidgetId, SystemClock.elapsedRealtime()) }
                        if (fire) perform(appContext, folder, folder.effectiveTapAction)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun perform(context: Context, folder: Folder, action: TapAction) {
        when (action) {
            TapAction.OPEN_FOLDER -> context.startActivity(FolderOpenActivity.intent(context, folder.id))
            TapAction.LAUNCH_DEFAULT_APP -> {
                val app = folder.defaultApp ?: return
                if (AppLauncher.launch(context, app)) {
                    val repo = context.graph.repository
                    repo.getItems(folder.id).firstOrNull { it.app == app }?.let { repo.recordLaunch(it.id) }
                    WidgetUpdater.updateForFolder(context, folder.id)
                }
            }
            TapAction.NONE -> Unit
        }
    }

    companion object {
        const val ACTION_TAP = "com.folderplus.widget.TAP"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        /** Process-wide so two broadcasts for the same widget share state. */
        private val resolver = TapResolver()
    }
}
