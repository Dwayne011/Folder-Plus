package com.widgetsplus

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.widgetsplus.agenda.AgendaWidgetProvider
import com.widgetsplus.apps.InstalledApps
import com.widgetsplus.data.FolderRepository
import com.widgetsplus.data.db.WidgetsPlusDatabase
import com.widgetsplus.security.LockManager
import com.widgetsplus.widget.HostedWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tiny service locator. The app is small enough that a DI framework would be
 * more ceremony than value; everything is reachable via [Context.graph].
 */
class WidgetsPlusApp : Application() {
    lateinit var graph: Graph
        private set

    /** The agenda card colour last seen, to notice the phone's theme or wallpaper colours changing. */
    private var themeColor = 0

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        themeColor = ContextCompat.getColor(this, R.color.agenda_card)
        // After a restore onto another device, hosted widget ids point nowhere; forget them
        // so "Show widget" falls back to opening the folder until the user picks again.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            graph.repository.sanitizeHostedWidgets { id -> graph.hostedWidgets.info(id) != null }
        }
    }

    /**
     * The folder and devices widgets take every colour from their layouts, which the launcher
     * re-resolves on a theme change. The agenda's day cards are drawn here, so when the theme
     * changes while the app is running, redraw them.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val color = ContextCompat.getColor(this, R.color.agenda_card)
        if (color != themeColor) {
            themeColor = color
            AgendaWidgetProvider.notifyDataChanged(this)
        }
    }
}

class Graph(context: Context) {
    val appContext: Context = context.applicationContext
    val database: WidgetsPlusDatabase = WidgetsPlusDatabase.build(context)
    val repository: FolderRepository = FolderRepository(database)
    val installedApps: InstalledApps = InstalledApps(context)
    val lockManager: LockManager = LockManager(context)
    val hostedWidgets: HostedWidgets = HostedWidgets(context)
}

val Context.graph: Graph
    get() = (applicationContext as WidgetsPlusApp).graph
