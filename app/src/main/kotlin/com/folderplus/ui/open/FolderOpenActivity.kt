package com.folderplus.ui.open

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.folderplus.R
import com.folderplus.core.model.Folder
import com.folderplus.core.model.FolderItem
import com.folderplus.graph
import com.folderplus.launch.AppLauncher
import com.folderplus.ui.MainActivity
import com.folderplus.ui.theme.FolderPlusTheme
import com.folderplus.widget.WidgetUpdater
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

        setContent {
            FolderPlusTheme {
                val state by remember(folderId) {
                    combine(repo.observeFolder(folderId), repo.observeCategories(folderId), repo.observeItems(folderId)) { f, c, i ->
                        PopupState(loaded = true, folder = f, categories = c, items = i)
                    }
                }.collectAsStateWithLifecycle(initialValue = PopupState())

                var unlocked by rememberSaveable { mutableStateOf(false) }
                val folder = state.folder

                LaunchedEffect(state.loaded, folder) {
                    if (state.loaded && folder == null) finish()
                }
                if (folder == null) return@FolderPlusTheme

                LaunchedEffect(Unit) { appGraph.installedApps.ensureLoaded() }

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
                    )
                }
            }
        }
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

        fun intent(context: Context, folderId: Long): Intent =
            Intent(context, FolderOpenActivity::class.java)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private data class PopupState(
    val loaded: Boolean = false,
    val folder: Folder? = null,
    val categories: List<com.folderplus.core.model.Category> = emptyList(),
    val items: List<FolderItem> = emptyList(),
)
