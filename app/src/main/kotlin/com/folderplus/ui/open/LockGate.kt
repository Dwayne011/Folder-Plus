package com.folderplus.ui.open

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.folderplus.R
import com.folderplus.core.model.Folder
import com.folderplus.core.security.PinHasher

/**
 * Blocks the folder until the user authenticates. Biometrics are requested
 * automatically on first show when available; the PIN pad is always usable
 * when a PIN lock applies.
 */
@Composable
fun LockGate(
    folder: Folder,
    pinEnabled: Boolean,
    biometricEnabled: Boolean,
    verifyPin: (String) -> Boolean,
    requestBiometric: ((Boolean) -> Unit) -> Unit,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    var biometricAsked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !biometricAsked) {
            biometricAsked = true
            requestBiometric { ok ->
                if (ok) onUnlocked() else if (!pinEnabled) onDismiss()
            }
        }
    }

    fun submit(value: String) {
        if (verifyPin(value)) onUnlocked() else {
            error = true
            pin = ""
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.unlock_title, folder.name), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.unlock_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))

                if (pinEnabled) {
                    PinDots(length = pin.length, error = error)
                    Spacer(Modifier.height(16.dp))
                    PinPad(
                        onDigit = { d ->
                            if (pin.length < PinHasher.MAX_PIN_LENGTH) {
                                pin += d
                                error = false
                            }
                        },
                        onBackspace = { pin = pin.dropLast(1) },
                        onSubmit = { if (pin.length >= PinHasher.MIN_PIN_LENGTH) submit(pin) },
                        submitEnabled = pin.length >= PinHasher.MIN_PIN_LENGTH,
                    )
                    if (error) {
                        Text(stringResource(R.string.unlock_wrong_pin), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (biometricEnabled) {
                    TextButton(onClick = { requestBiometric { if (it) onUnlocked() } }) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.unlock_use_biometrics))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun PinDots(length: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val shown = length.coerceAtLeast(PinHasher.MIN_PIN_LENGTH)
        repeat(shown) { index ->
            val filled = index < length
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            error -> MaterialTheme.colorScheme.error
                            filled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, submitEnabled: Boolean) {
    val rows = listOf("123", "456", "789")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { digit -> PinKey(digit.toString()) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackspace, modifier = Modifier.size(64.dp)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
            }
            PinKey("0") { onDigit('0') }
            TextButton(onClick = onSubmit, enabled = submitEnabled, modifier = Modifier.size(64.dp)) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}
