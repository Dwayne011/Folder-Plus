package com.widgetsplus

import android.app.Application
import android.content.Context
import com.widgetsplus.apps.InstalledApps
import com.widgetsplus.data.FolderRepository
import com.widgetsplus.data.db.FolderPlusDatabase
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
class FolderPlusApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        // After a restore onto another device, hosted widget ids point nowhere; forget them
        // so "Show widget" falls back to opening the folder until the user picks again.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            graph.repository.sanitizeHostedWidgets { id -> graph.hostedWidgets.info(id) != null }
        }
    }
}

class Graph(context: Context) {
    val appContext: Context = context.applicationContext
    val database: FolderPlusDatabase = FolderPlusDatabase.build(context)
    val repository: FolderRepository = FolderRepository(database)
    val installedApps: InstalledApps = InstalledApps(context)
    val lockManager: LockManager = LockManager(context)
    val hostedWidgets: HostedWidgets = HostedWidgets(context)
}

val Context.graph: Graph
    get() = (applicationContext as FolderPlusApp).graph
