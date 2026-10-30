package com.folderplus.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.folderplus.core.gesture.TapResolver
import com.folderplus.core.model.Folder
import com.folderplus.core.model.TapAction
import com.folderplus.graph
import com.folderplus.launch.AppLauncher
import com.folderplus.ui.open.FolderOpenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Invisible activity behind a widget's main tap area when the folder has a
 * double-tap action.
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
    private var decided = false
    private var secondTapSeen = false
    private var createdAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createdAt = SystemClock.elapsedRealtime()
        val folderId = intent.getLongExtra(EXTRA_FOLDER_ID, -1L)
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
        folder?.let { decide(it.effectiveDoubleTapAction) }
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

        /** Measured from activity creation, which already trails the first tap slightly. */
        private const val WINDOW_MILLIS = TapResolver.DEFAULT_WINDOW_MILLIS + 50

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, folderId: Long): Intent =
            Intent(context, WidgetTapActivity::class.java)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
