package com.widgetsplus.devices

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.widgetsplus.R
import com.widgetsplus.core.devices.DeviceKind
import com.widgetsplus.ui.common.Option
import com.widgetsplus.ui.frost.FrostChoicePage
import com.widgetsplus.ui.frost.FrostGroup
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostNote
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostRow
import com.widgetsplus.ui.frost.FrostTheme
import com.widgetsplus.ui.frost.Trailing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Setup for the connected devices widget: asks for the "Nearby devices" permission it needs to
 * see connected devices and their battery, and offers to place the widget. The launcher opens it
 * when the widget is added and from the widget's long-press settings.
 */
class DevicesSetupActivity : ComponentActivity() {

    private companion object {
        const val SHIZUKU_REQUEST = 41
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Added from the launcher: keep the widget whatever happens here.
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        setContent { FrostTheme { SetupScreen(askNow = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID, onBack = ::finish) } }
    }

    override fun onResume() {
        super.onResume()
        DevicesWidgetProvider.updateAll(this)
    }

    @Composable
    private fun SetupScreen(askNow: Boolean, onBack: () -> Unit) {
        var granted by remember { mutableStateOf(BluetoothDevices.hasPermission(this)) }
        // Shizuku's row only appears when Shizuku is installed.
        val shizukuInstalled = remember { BudAccess.isInstalled(this, BudAccess.SHIZUKU) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            DevicesWidgetProvider.updateAll(this)
        }
        LaunchedEffect(Unit) {
            if (askNow && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // A device type being chosen, as its own page of choices.
        var changes by remember { mutableIntStateOf(0) }
        var editing by remember { mutableStateOf<BluetoothDevices.Paired?>(null) }
        editing?.let { device ->
            FrostChoicePage(
                overline = stringResource(R.string.devices_types_section),
                title = device.name,
                label = stringResource(R.string.devices_types_section),
                options = listOf(Option<DeviceKind?>(null, stringResource(R.string.devices_kind_automatic, kindLabel(device.guessed)))) +
                    DeviceKind.entries.map { Option<DeviceKind?>(it, kindLabel(it)) },
                selected = DevicePrefs.kindChoice(this, device.address),
                onSelect = { kind ->
                    DevicePrefs.setKindChoice(this, device.address, kind)
                    changes++
                    DevicesWidgetProvider.updateAll(this)
                },
                onBack = { editing = null },
            )
            return
        }
        FrostPage(title = stringResource(R.string.devices_widget_label), overline = stringResource(R.string.settings_title), onBack = onBack) {
            item(key = "devices") {
                FrostGroup(stringResource(R.string.devices_widget_label), listOfNotNull<@Composable () -> Unit>(
                    {
                        FrostRow(
                            FrostIcons.Phone,
                            stringResource(R.string.devices_nearby),
                            stringResource(if (granted) R.string.devices_nearby_on else R.string.devices_nearby_off),
                            if (granted) Trailing.None else Trailing.Chevron,
                            onClick = if (granted) null else ({ if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }),
                        )
                    },
                    {
                        FrostRow(
                            FrostIcons.Bluetooth,
                            stringResource(R.string.devices_bluetooth_settings),
                            stringResource(R.string.devices_bluetooth_settings_hint),
                            Trailing.Chevron,
                            onClick = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        )
                    },
                    if (shizukuInstalled) ({ BudLevelsRow() }) else null,
                ))
            }
            item(key = "types") { DeviceTypes(granted, changes) { editing = it } }
        }
    }

    /**
     * Earbuds' left, right and case levels through Shizuku: tapping shows Shizuku's own one-time
     * "Allow" pop-up, then grants this app the access it needs. Once on (through Shizuku or At a
     * Glance Plus) the row only says so.
     */
    @Composable
    private fun BudLevelsRow() {
        var on by remember { mutableStateOf(BudBattery.hasAccess(this)) }
        var working by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun grant() {
            working = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { BudAccess.grantWithShizuku(this@DevicesSetupActivity) }
                working = false
                on = ok
                if (ok) {
                    DevicesWidgetProvider.updateAll(this@DevicesSetupActivity)
                } else {
                    Toast.makeText(this@DevicesSetupActivity, R.string.devices_buds_failed, Toast.LENGTH_LONG).show()
                }
            }
        }

        DisposableEffect(Unit) {
            val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
                if (code == SHIZUKU_REQUEST && result == PackageManager.PERMISSION_GRANTED) grant()
            }
            runCatching { Shizuku.addRequestPermissionResultListener(listener) }
            onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
        }

        if (on) {
            FrostRow(
                FrostIcons.Earbuds,
                stringResource(R.string.devices_buds),
                when {
                    BudAccess.grantedViaShizuku(this) -> stringResource(R.string.devices_buds_on_shizuku)
                    BudAccess.isInstalled(this, BudAccess.AT_A_GLANCE) -> stringResource(R.string.devices_buds_on_glance)
                    else -> stringResource(R.string.devices_buds_on_plain)
                },
            )
        } else {
            FrostRow(
                FrostIcons.Earbuds,
                stringResource(R.string.devices_buds),
                stringResource(R.string.devices_buds_hint),
                Trailing.Chevron,
                enabled = !working,
                onClick = {
                    when {
                        !BudAccess.shizukuRunning() ->
                            Toast.makeText(this, R.string.devices_buds_start_shizuku, Toast.LENGTH_LONG).show()
                        BudAccess.shizukuAllowed() -> grant()
                        else -> runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST) }
                    }
                },
            )
        }
    }

    /**
     * Your paired devices and what the widget takes each to be. Tap one to choose its kind (kept
     * for that device by its Bluetooth address), or set it back to Automatic.
     */
    @Composable
    private fun DeviceTypes(granted: Boolean, changes: Int, onEdit: (BluetoothDevices.Paired) -> Unit) {
        val paired = remember(granted, changes) { BluetoothDevices.paired(this) }
        FrostGroup(
            stringResource(R.string.devices_types_section),
            if (paired.isEmpty()) {
                listOf { FrostNote(stringResource(R.string.devices_types_none)) }
            } else {
                paired.map<BluetoothDevices.Paired, @Composable () -> Unit> { device ->
                    {
                        val chosen = DevicePrefs.kindChoice(this, device.address)
                        FrostRow(
                            kindIcon(chosen ?: device.guessed),
                            device.name,
                            if (chosen != null) stringResource(R.string.devices_kind_chosen, kindLabel(chosen))
                            else stringResource(R.string.devices_kind_automatic, kindLabel(device.guessed)),
                            Trailing.Chevron,
                            onClick = { onEdit(device) },
                        )
                    }
                }
            },
        )
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

    private fun kindIcon(kind: DeviceKind): String = when (kind) {
        DeviceKind.EARBUDS -> FrostIcons.Earbuds
        DeviceKind.HEADPHONES -> FrostIcons.Headphones
        DeviceKind.WATCH -> FrostIcons.Watch
        DeviceKind.KEYBOARD -> FrostIcons.Keyboard
        DeviceKind.MOUSE -> FrostIcons.Mouse
        DeviceKind.GAMEPAD -> FrostIcons.Gamepad
        DeviceKind.SPEAKER -> FrostIcons.Speaker
        DeviceKind.CAR -> FrostIcons.Car
        DeviceKind.OTHER -> FrostIcons.Bluetooth
    }
}
