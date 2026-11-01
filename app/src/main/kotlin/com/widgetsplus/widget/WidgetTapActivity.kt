package com.widgetsplus.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.widgetsplus.core.gesture.TapResolver
import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.TapAction
import com.widgetsplus.graph
import com.widgetsplus.launch.AppLauncher
import com.widgetsplus.ui.open.FolderOpenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Invisible activity behind a widget's main tap area when the folder has a
 * double-tap action, and behind every app icon on a folder widget bigger than
 * one cell (a single tap opens that app, a double tap opens the folder).
 *
 * The first tap starts this activity (a direct activity PendingIntent, which
 * launchers always allow). While it is up, a second tap either lands on its
 * transparent window or re-fires the widget's PendingIntent into onNewIntent;
 * either counts as a double tap. If nothing arrives within the window the
 * single-tap action runs. Everything happens from a foreground activity, so
 * Android's background-activity-start restrictions never apply.
 */
class WidgetTapActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var folder: Folder? = null
    /** Set when an app icon was tapped: the single tap opens this app. */
    private var targetApp: AppRef? = null
    private var targetItemId = -1L
    private var decided = false
    private var secondTapSeen = false
    private var createdAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createdAt = SystemClock.elapsedRealtime()
        val folderId = intent.getLongExtra(EXTRA_FOLDER_ID, -1L)
        targetApp = intent.getStringExtra(EXTRA_APP)?.let(AppRef::unflatten)
        targetItemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (folderId <= 0) {
            finish()
            return
        }
        // A transparent view covering the window so the second tap reaches us.
        setContentView(View(this).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onSecondTap()
                true
            }
        })

        val appContext = applicationContext
        scope.launch {
            val loaded = appContext.graph.repository.getFolder(folderId)
            handler.post { onFolderLoaded(loaded) }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onSecondTap()
    }

    private fun onFolderLoaded(loaded: Folder?) {
        if (isFinishing || decided) return
        if (loaded == null) {
            finish()
            return
        }
        folder = loaded
        val app = targetApp
        if (app != null) {
            if (secondTapSeen) {
                decide(TapAction.OPEN_FOLDER)
                return
            }
            val remaining = WINDOW_MILLIS - (SystemClock.elapsedRealtime() - createdAt)
            handler.postDelayed({ launchTarget(app) }, remaining.coerceAtLeast(0L))
            return
        }
        if (loaded.effectiveDoubleTapAction == TapAction.NONE) {
            decide(loaded.effectiveTapAction)
            return
        }
        if (secondTapSeen) {
            decide(loaded.effectiveDoubleTapAction)
            return
        }
        val remaining = WINDOW_MILLIS - (SystemClock.elapsedRealtime() - createdAt)
        handler.postDelayed({ folder?.let { decide(it.effectiveTapAction) } }, remaining.coerceAtLeast(0L))
    }

    private fun onSecondTap() {
        if (decided) return
        secondTapSeen = true
        val loaded = folder ?: return
        decide(if (targetApp != null) TapAction.OPEN_FOLDER else loaded.effectiveDoubleTapAction)
    }

    /** Single tap on an app icon: open that app and count the launch. */
    private fun launchTarget(app: AppRef) {
        if (decided) return
        decided = true
        handler.removeCallbacksAndMessages(null)
        val current = folder
        if (AppLauncher.launch(this, app) && current != null) {
            val appContext = applicationContext
            val itemId = targetItemId
            scope.launch {
                val repo = appContext.graph.repository
                val id = itemId.takeIf { it > 0 } ?: repo.getItems(current.id).firstOrNull { it.app == app }?.id
                id?.let { repo.recordLaunch(it) }
                WidgetUpdater.updateForFolder(appContext, current.id)
            }
        }
        finish()
    }

    private fun decide(action: TapAction) {
        if (decided) return
        decided = true
        handler.removeCallbacksAndMessages(null)
        val current = folder
        if (current != null) perform(current, action)
        finish()
    }

    private fun perform(folder: Folder, action: TapAction) {
        when (action) {
            TapAction.OPEN_FOLDER -> startActivity(FolderOpenActivity.intent(this, folder.id))
            TapAction.SHOW_WIDGET -> startActivity(FolderOpenActivity.intent(this, folder.id, showWidget = true))
            TapAction.LAUNCH_DEFAULT_APP -> {
                val app = folder.defaultApp ?: return
                if (AppLauncher.launch(this, app)) {
                    val appContext = applicationContext
                    scope.launch {
                        val repo = appContext.graph.repository
                        repo.getItems(folder.id).firstOrNull { it.app == app }?.let { repo.recordLaunch(it.id) }
                        WidgetUpdater.updateForFolder(appContext, folder.id)
                    }
                }
            }
            TapAction.NONE -> Unit
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FOLDER_ID = "folder_id"
        const val EXTRA_APP = "app"
        const val EXTRA_ITEM_ID = "item_id"

        /** Measured from activity creation, which already trails the first tap slightly. */
        private const val WINDOW_MILLIS = TapResolver.DEFAULT_WINDOW_MILLIS + 50

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, folderId: Long): Intent =
            Intent(context, WidgetTapActivity::class.java)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** An app icon on a folder widget: single tap opens [flatApp], double tap opens the folder. */
        fun appIntent(context: Context, folderId: Long, flatApp: String, itemId: Long): Intent =
            intent(context, folderId)
                .putExtra(EXTRA_APP, flatApp)
                .putExtra(EXTRA_ITEM_ID, itemId)
    }
}
