package com.widgetsplus.devices

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Earbuds
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.widgetsplus.R
import com.widgetsplus.core.devices.DeviceKind
import com.widgetsplus.ui.common.Option
import com.widgetsplus.ui.common.OptionPickerDialog
import com.widgetsplus.ui.common.RowIcon
import com.widgetsplus.ui.common.SectionTitle
import com.widgetsplus.ui.common.SettingRow
import com.widgetsplus.ui.common.SettingsGroup
import com.widgetsplus.ui.theme.WidgetsPlusTheme

/**
 * Setup for the connected devices widget: asks for the "Nearby devices" permission it needs to
 * see connected devices and their battery, and offers to place the widget. The launcher opens it
 * when the widget is added and from the widget's long-press settings.
 */
class DevicesSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Added from the launcher: keep the widget whatever happens here.
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        setContent { WidgetsPlusTheme { SetupScreen(askNow = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID, onBack = ::finish) } }
    }

    override fun onResume() {
        super.onResume()
        DevicesWidgetProvider.updateAll(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SetupScreen(askNow: Boolean, onBack: () -> Unit) {
        var granted by remember { mutableStateOf(BluetoothDevices.hasPermission(this)) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            DevicesWidgetProvider.updateAll(this)
        }
        LaunchedEffect(Unit) {
            if (askNow && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.devices_setup_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.devices_setup_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
                SectionTitle(stringResource(R.string.devices_widget_label))
                SettingsGroup(rows = listOf(
                    {
                        SettingRow(
                            title = stringResource(if (granted) R.string.devices_granted else R.string.devices_allow),
                            subtitle = stringResource(R.string.devices_permission_hint),
                            leading = { RowIcon(if (granted) Icons.Default.Check else Icons.Default.Devices) },
                            onClick = { if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                        )
                    },
                    {
                        SettingRow(
                            title = stringResource(R.string.devices_add_widget),
                            subtitle = stringResource(R.string.devices_add_widget_hint),
                            leading = { RowIcon(Icons.Default.AddToHomeScreen) },
                            onClick = { pinWidget() },
                        )
                    },
                    {
                        SettingRow(
                            title = stringResource(R.string.devices_bluetooth_settings),
                            subtitle = stringResource(R.string.devices_bluetooth_settings_hint),
                            leading = { RowIcon(Icons.Default.Bluetooth) },
                            onClick = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        )
                    },
                ))
                DeviceTypes(granted)
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    /**
     * Your paired devices and what the widget takes each to be. Tap one to choose its kind (kept
     * for that device by its Bluetooth address), or set it back to Automatic.
     */
    @Composable
    private fun DeviceTypes(granted: Boolean) {
        var changes by remember { mutableIntStateOf(0) }
        val paired = remember(granted, changes) { BluetoothDevices.paired(this) }
        var editing by remember { mutableStateOf<BluetoothDevices.Paired?>(null) }

        SectionTitle(stringResource(R.string.devices_types_section))
        Text(
            stringResource(if (paired.isEmpty()) R.string.devices_types_none else R.string.devices_types_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
        )
        if (paired.isNotEmpty()) {
            SettingsGroup(rows = paired.map<BluetoothDevices.Paired, @Composable () -> Unit> { device ->
                {
                    val chosen = DevicePrefs.kindChoice(this, device.address)
                    SettingRow(
                        title = device.name,
                        subtitle = if (chosen != null) stringResource(R.string.devices_kind_chosen, kindLabel(chosen))
                        else stringResource(R.string.devices_kind_automatic, kindLabel(device.guessed)),
                        leading = { RowIcon(kindIcon(chosen ?: device.guessed)) },
                        onClick = { editing = device },
                    )
                }
            })
        }

        editing?.let { device ->
            OptionPickerDialog(
                title = device.name,
                options = listOf(Option<DeviceKind?>(null, stringResource(R.string.devices_kind_automatic, kindLabel(device.guessed)))) +
                    DeviceKind.entries.map { Option<DeviceKind?>(it, kindLabel(it)) },
                selected = DevicePrefs.kindChoice(this, device.address),
                onSelect = { kind ->
                    DevicePrefs.setKindChoice(this, device.address, kind)
                    changes++
                    DevicesWidgetProvider.updateAll(this)
                },
                onDismiss = { editing = null },
            )
        }
    }

    @Composable
    private fun kindLabel(kind: DeviceKind): String = stringResource(
        when (kind) {
            DeviceKind.EARBUDS -> R.string.devices_kind_earbuds
            DeviceKind.HEADPHONES -> R.string.devices_kind_headphones
            DeviceKind.WATCH -> R.string.devices_kind_watch
            DeviceKind.KEYBOARD -> R.string.devices_kind_keyboard
            DeviceKind.MOUSE -> R.string.devices_kind_mouse
            DeviceKind.GAMEPAD -> R.string.devices_kind_gamepad
            DeviceKind.SPEAKER -> R.string.devices_kind_speaker
            DeviceKind.CAR -> R.string.devices_kind_car
            DeviceKind.OTHER -> R.string.devices_kind_other
        },
    )

    private fun kindIcon(kind: DeviceKind): ImageVector = when (kind) {
        DeviceKind.EARBUDS -> Icons.Default.Earbuds
        DeviceKind.HEADPHONES -> Icons.Default.Headphones
        DeviceKind.WATCH -> Icons.Default.Watch
        DeviceKind.KEYBOARD -> Icons.Default.Keyboard
        DeviceKind.MOUSE -> Icons.Default.Mouse
        DeviceKind.GAMEPAD -> Icons.Default.SportsEsports
        DeviceKind.SPEAKER -> Icons.Default.Speaker
        DeviceKind.CAR -> Icons.Default.DirectionsCar
        DeviceKind.OTHER -> Icons.Default.Bluetooth
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        manager.requestPinAppWidget(ComponentName(this, DevicesWidgetProvider::class.java), null, null)
    }
}
