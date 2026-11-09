package com.widgetsplus.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.widgetsplus.R
import com.widgetsplus.core.security.PinHasher

/** A labelled option for a page of choices ([com.widgetsplus.ui.frost.FrostChoicePage]). */
data class Option<T>(val value: T, val label: String, val description: String? = null, val enabled: Boolean = true)

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmText: String = stringResource(R.string.save),
    allowBlank: Boolean = false,
    placeholder: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = allowBlank || text.isNotBlank(), onClick = { onConfirm(text.trim()); onDismiss() }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Two-step "enter, then confirm" dialog for creating or changing the PIN. */
@Composable
fun SetPinDialog(onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.pin_invalid)
    val mismatch = stringResource(R.string.pin_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirming) R.string.confirm_pin else R.string.set_pin)) },
        text = {
            Column {
                OutlinedTextField(
                    value = if (confirming) second else first,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }.take(PinHasher.MAX_PIN_LENGTH)
                        if (confirming) second = digits else first = digits
                        error = null
                    },
                    label = { Text(stringResource(if (confirming) R.string.confirm_pin else R.string.enter_new_pin)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!confirming) {
                    if (PinHasher.isValidPin(first)) confirming = true else error = invalid
                } else if (second == first) {
                    onSet(first)
                    onDismiss()
                } else {
                    error = mismatch
                    second = ""
                }
            }) { Text(stringResource(if (confirming) R.string.done else R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
