package com.example.skydiveapp

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal data class InfoContent(val title: String, val text: String)

@Composable
internal fun InfoDialog(info: InfoContent?, onDismiss: () -> Unit) {
    if (info != null) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.title) },
        text = { Text(info.text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } }
    )
}

@Composable
internal fun InfoButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Подсказка: $label" }) {
        Text("ⓘ")
    }
}
