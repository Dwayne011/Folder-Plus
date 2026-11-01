package com.widgetsplus.launch

import android.app.Activity
import android.os.Bundle
import com.widgetsplus.core.model.AppRef
import com.widgetsplus.graph
import com.widgetsplus.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Invisible activity behind widget quick-launch icons. It records the launch
 * (so "most used" / "recently used" sorting works) and starts the target app.
 */
class AppLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = intent.getStringExtra(EXTRA_APP)?.let(AppRef::unflatten)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (app != null) {
            AppLauncher.launch(this, app)
            if (itemId > 0) {
                val appContext = applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    appContext.graph.repository.recordLaunch(itemId)
                    WidgetUpdater.updateAll(appContext)
                }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_APP = "app"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
