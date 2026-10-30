package com.folderplus

import android.app.Application
import android.content.Context
import com.folderplus.apps.InstalledApps
import com.folderplus.data.FolderRepository
import com.folderplus.data.db.FolderPlusDatabase
import com.folderplus.security.LockManager
import com.folderplus.widget.HostedWidgets

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
