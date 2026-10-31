package com.widgetsplus.agenda

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.widgetsplus.R
import com.widgetsplus.ui.common.RowIcon
import com.widgetsplus.ui.common.SectionTitle
import com.widgetsplus.ui.common.SettingRow
import com.widgetsplus.ui.common.SettingsGroup
import com.widgetsplus.ui.theme.FolderPlusTheme

/** Asks for calendar access and offers to place the agenda widget. */
class AgendaSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolderPlusTheme { SetupScreen(onBack = ::finish) }
        }
    }

    override fun onResume() {
        super.onResume()
        AgendaWidgetProvider.updateAll(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SetupScreen(onBack: () -> Unit) {
        var granted by remember { mutableStateOf(CalendarAgenda.hasPermission(this)) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            if (ok) AgendaWidgetProvider.updateAll(this)
        }
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.agenda_setup_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Top) {
                Text(
                    stringResource(R.string.agenda_setup_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
                SectionTitle(stringResource(R.string.agenda_widget_label))
                SettingsGroup(rows = listOf(
                    {
                        SettingRow(
                            title = stringResource(if (granted) R.string.agenda_granted else R.string.agenda_allow),
                            subtitle = stringResource(R.string.agenda_permission_hint),
                            leading = { RowIcon(if (granted) Icons.Default.Check else Icons.Default.CalendarMonth) },
                            onClick = { if (!granted) permission.launch(Manifest.permission.READ_CALENDAR) },
                        )
                    },
                    {
                        SettingRow(
                            title = stringResource(R.string.agenda_add_widget),
                            subtitle = stringResource(R.string.agenda_add_widget_hint),
                            leading = { RowIcon(Icons.Default.AddToHomeScreen) },
                            onClick = { pinWidget() },
                        )
                    },
                    {
                        SettingRow(
                            title = stringResource(R.string.agenda_calendars),
                            subtitle = stringResource(R.string.agenda_calendars_hint),
                            leading = { RowIcon(Icons.Default.EventAvailable) },
                            enabled = granted,
                            onClick = { startActivity(Intent(this@AgendaSetupActivity, AgendaConfigActivity::class.java)) },
                        )
                    },
                ))
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        manager.requestPinAppWidget(ComponentName(this, AgendaWidgetProvider::class.java), null, null)
    }
}
