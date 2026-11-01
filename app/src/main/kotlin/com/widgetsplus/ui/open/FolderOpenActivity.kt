package com.widgetsplus.ui.open

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.widgetsplus.R
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.sort.AppSorter
import com.widgetsplus.graph
import com.widgetsplus.launch.AppLauncher
import com.widgetsplus.ui.MainActivity
import com.widgetsplus.ui.theme.WidgetsPlusTheme
import com.widgetsplus.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The folder popup. Started by widgets (tap/double-tap/badge). Draws over the
 * home screen with a scrim; tapping outside closes it. Locked folders show the
 * lock gate first.
 */
class FolderOpenActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val folderId = intent.getLongExtra(EXTRA_FOLDER_ID, -1L)
        if (folderId <= 0) {
            finish()
            return
        }
        val appGraph = graph
        val repo = appGraph.repository
        val lock = appGraph.lockManager

        val startOnWidget = intent.getBooleanExtra(EXTRA_SHOW_WIDGET, false)
        setContent {
            WidgetsPlusTheme {
                val state by remember(folderId) {
                    combine(repo.observeFolder(folderId), repo.observeCategories(folderId), repo.observeItems(folderId)) { f, c, i ->
                        PopupState(loaded = true, folder = f, categories = c, items = i)
                    }
                }.collectAsStateWithLifecycle(initialValue = PopupState())

                var unlocked by rememberSaveable { mutableStateOf(false) }
                var showingWidget by rememberSaveable { mutableStateOf(startOnWidget) }
                val folder = state.folder

                LaunchedEffect(state.loaded, folder) {
                    if (state.loaded && folder == null) finish()
                }
                if (folder == null) return@WidgetsPlusTheme

                LaunchedEffect(Unit) { appGraph.installedApps.ensureLoaded() }
                LaunchedEffect(folder.frosting) { applyFrosting(folder.frosting) }

                if (!unlocked && lock.requiresUnlock(folder.lockMode)) {
                    LockGate(
                        folder = folder,
                        pinEnabled = folder.lockMode.allowsPin && lock.hasPin,
                        biometricEnabled = folder.lockMode.allowsBiometric && lock.biometricOrCredentialAvailable(),
                        verifyPin = lock::verifyPin,
                        requestBiometric = { onResult -> authenticate(folder, onResult) },
                        onUnlocked = { unlocked = true },
                        onDismiss = ::finish,
                    )
                } else {
                    val hosted = folder.hostedWidget
                    if (showingWidget && hosted != null) {
                        WidgetPopup(
                            folder = folder,
                            hosted = hosted,
                            hostedWidgets = appGraph.hostedWidgets,
                            onShowFolder = { showingWidget = false },
                            frosting = folder.frosting,
                            onOpenSettings = {
                                startActivity(MainActivity.editorIntent(this, folder.id))
                                finish()
                            },
                            onDismiss = ::finish,
                        )
                    } else {
                        FolderPopup(
                            folder = folder,
                            categories = state.categories,
                            items = state.items,
                            labelOf = { appGraph.installedApps.label(it) },
                            onLaunch = { launch(folder, it) },
                            onOpenSettings = {
                                startActivity(MainActivity.editorIntent(this, folder.id))
                                finish()
                            },
                            onDismiss = ::finish,
                            onShowWidget = if (hosted != null) ({ showingWidget = true }) else null,
                            frosting = folder.frosting,
                            onReorder = { category, ordered -> reorder(folder, state.items, category, ordered) },
                        )
                    }
                }
            }
        }
    }

    /** Real blur behind the window on Android 12+, scaled by the folder's frosting level. */
    private fun applyFrosting(level: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val radiusPx = (level / 100f * MAX_BLUR_DP * resources.displayMetrics.density).toInt()
        if (radiusPx > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        val params = window.attributes
        params.blurBehindRadius = radiusPx
        window.attributes = params
    }

    override fun onStart() {
        super.onStart()
        graph.hostedWidgets.startListening()
    }

    override fun onStop() {
        graph.hostedWidgets.stopListening()
        super.onStop()
    }

    private fun launch(folder: Folder, item: FolderItem) {
        if (AppLauncher.launch(this, item.app)) {
            // Not lifecycleScope: finish() below would cancel it before the write lands.
            val appContext = applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                appContext.graph.repository.recordLaunch(item.id)
                WidgetUpdater.updateForFolder(appContext, folder.id)
            }
        }
        finish()
    }

    /**
     * Saves a tab's new order from a press-and-hold drag: the tab's apps keep their places in the
     * folder's whole order, in their new sequence. Your own order only shows with Manual sorting,
     * so a folder (or a category with its own sort) sorted another way switches to Manual.
     */
    private fun reorder(folder: Folder, items: List<FolderItem>, category: Category?, ordered: List<FolderItem>) {
        val categoryOverride = category?.sortMode?.takeIf { it != SortMode.MANUAL }
        if (folder.sortMode != SortMode.MANUAL || categoryOverride != null) {
            Toast.makeText(this, R.string.sort_switched_manual, Toast.LENGTH_SHORT).show()
        }
        val appContext = applicationContext
        val apps = graph.installedApps
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repo = appContext.graph.repository
            val current = repo.getFolder(folder.id) ?: return@launch
            // The order the "All" tab showed, which the drag rearranged a part of.
            val whole = AppSorter.sort(items, current.sortMode) { apps.label(it.app) }
            repo.saveManualOrder(AppSorter.mergeTabOrder(whole, ordered))
            if (current.sortMode != SortMode.MANUAL) repo.updateFolder(current.copy(sortMode = SortMode.MANUAL))
            if (category != null && categoryOverride != null) repo.updateCategory(category.copy(sortMode = null))
            WidgetUpdater.updateForFolder(appContext, folder.id)
        }
    }

    /** Shows the system biometric sheet. [onResult] gets true on success, false when the user backs out. */
    private fun authenticate(folder: Folder, onResult: (Boolean) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title, folder.name))
            .setSubtitle(getString(R.string.unlock_subtitle))
        if (folder.lockMode.allowsPin && graph.lockManager.hasPin) {
            // Our own PIN pad is the fallback.
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK)
            builder.setNegativeButtonText(getString(R.string.unlock_use_pin))
        } else {
            // Biometric-only folders fall back to the device credential.
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
        }
        prompt.authenticate(builder.build())
    }

    companion object {
        const val EXTRA_FOLDER_ID = "folder_id"
        const val EXTRA_SHOW_WIDGET = "show_widget"
        private const val MAX_BLUR_DP = 40

        fun intent(context: Context, folderId: Long, showWidget: Boolean = false): Intent =
            Intent(context, FolderOpenActivity::class.java)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .putExtra(EXTRA_SHOW_WIDGET, showWidget)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private data class PopupState(
    val loaded: Boolean = false,
    val folder: Folder? = null,
    val categories: List<Category> = emptyList(),
    val items: List<FolderItem> = emptyList(),
)
